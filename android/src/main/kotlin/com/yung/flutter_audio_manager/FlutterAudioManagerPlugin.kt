package com.yung.flutter_audio_manager

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.lang.reflect.Method


class FlutterAudioManagerPlugin: FlutterPlugin, MethodChannel.MethodCallHandler,  ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    companion object {
        const val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1452
        const val LOG_TAG = "flutter_audio_manager"
    }

    private lateinit var channel : MethodChannel
    private var audioManager: AudioManager? = null
    private var btManager: BluetoothManager? = null
    private var context: Context? = null
    private var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var listener: AudioEventListener = object : AudioEventListener {
        override fun onChanged() {
            channel.invokeMethod("inputChanged", 1)
        }
    }

    private fun changeToReceiver(): Boolean {
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.stopBluetoothSco()
        audioManager?.isBluetoothScoOn = false
        audioManager?.isSpeakerphoneOn = false
        listener.onChanged()
        return true
    }

    private fun changeToSpeaker(): Boolean {
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.stopBluetoothSco()
        audioManager?.isBluetoothScoOn = false
        audioManager?.isSpeakerphoneOn = true
        listener.onChanged()
        return true
    }

    private fun changeToHeadphones(): Boolean {
        return changeToReceiver()
    }

    private fun changeToBluetooth(): Boolean {
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.startBluetoothSco()
        audioManager?.isBluetoothScoOn = true
        listener.onChanged()
        return true
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val m: Method = device.javaClass.getMethod("isConnected")
            m.invoke(device) as Boolean
        } catch (e: Exception) {
            return false
        }
    }

    private fun getCurrentOutput(): List<String> {
        val info: MutableList<String> = ArrayList()
        var hasPerm = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context!!, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activityBinding!!.activity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_BLUETOOTH_CONNECT_PERMISSION)
                hasPerm = false
            }
        }
        val isBtScoOn = audioManager?.isBluetoothScoOn ?: false
        var hasConnectedDevice = false
        if (hasPerm) {
            hasConnectedDevice = btManager?.adapter?.bondedDevices?.any { isConnected(it) } ?: false
        }
        if (isBtScoOn && !hasConnectedDevice) {
            // makes sure sco manager is off when no device paired
            audioManager?.isBluetoothScoOn = false
        }
        // Log.i(LOG_TAG, "SCO: ${audioManager?.isBluetoothScoOn} hasConnectedDevice $hasConnectedDevice")
        when {
            audioManager?.isSpeakerphoneOn == true -> info.addAll(arrayOf("Speaker", "2"))
            isBtScoOn && hasConnectedDevice -> info.addAll(arrayOf("Bluetooth", "4"))
            audioManager?.isWiredHeadsetOn == true -> info.addAll(arrayOf("Headset", "3"))
            else -> info.addAll(arrayOf("Receiver", "1"))
        }
        return info
    }

    private fun getAvailableInputs(): List<List<String>> {
        val list: MutableList<List<String>> = ArrayList()
        list.add(listOf("Receiver", "1"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val devicesInfo = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devicesInfo?.forEach {
                when (it.type) {
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                        list.add(listOf("Headset", "3"))
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                        list.add(listOf("Bluetooth", "4"))
                }
            }
        } else {
            if (audioManager?.isWiredHeadsetOn == true) {
                list.add(listOf("Headset", "3"))
            }
            if (audioManager?.isBluetoothScoAvailableOffCall == true) {
                list.add(listOf("Bluetooth", "4"))
            }
        }

        return list
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        pluginBinding = flutterPluginBinding
    }

    override fun onAttachedToActivity(activity: ActivityPluginBinding) {
        val receiver = AudioChangeReceiver(listener)
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        context = pluginBinding?.applicationContext
        context?.registerReceiver(receiver, filter)
        channel =
            MethodChannel(pluginBinding!!.binaryMessenger, "flutter_audio_manager")
        channel.setMethodCallHandler(this)
        this.activityBinding = activity
        audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        btManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        activity.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() = onDetachedFromActivity()
    override fun onReattachedToActivityForConfigChanges(activity: ActivityPluginBinding) = onAttachedToActivity(activity)

    override fun onDetachedFromActivity() {
        activityBinding?.removeRequestPermissionsResultListener(this)
        pluginBinding = null
        context = null
        audioManager = null
        btManager = null
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "getCurrentOutput" -> result.success(getCurrentOutput())
            "getAvailableInputs" -> result.success(getAvailableInputs())
            "changeToReceiver" -> result.success(changeToReceiver())
            "changeToSpeaker" -> result.success(changeToSpeaker())
            "changeToHeadphones" -> result.success(changeToHeadphones())
            "changeToBluetooth" -> result.success(changeToBluetooth())
            else -> result.notImplemented()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>?, grantResults: IntArray
    ): Boolean {
        if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG, "bluetooth connect permission granted")
            } else {
                Log.e(LOG_TAG, "no bluetooth connect permission")
            }
            return true
        }
        return false
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}