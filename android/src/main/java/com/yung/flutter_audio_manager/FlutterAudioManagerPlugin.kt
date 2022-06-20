package com.yung.flutter_audio_manager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlin.collections.ArrayList


class FlutterAudioManagerPlugin: FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel : MethodChannel
    private var audioManager: AudioManager? = null
    private var activeContext: Context? = null

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

    private fun getCurrentOutput(): List<String> {
        val info: MutableList<String> = ArrayList()
        if (audioManager?.isSpeakerphoneOn == true) {
            info.add("Speaker")
            info.add("2")
        } else if (audioManager?.isBluetoothScoOn == true) {
            info.add("Bluetooth")
            info.add("4")
        } else if (audioManager?.isWiredHeadsetOn == true) {
            info.add("Headset")
            info.add("3")
        } else {
            info.add("Receiver")
            info.add("1")
        }
        return info
        // if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // MediaRouter mr = (MediaRouter)
        // activeContext.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        // MediaRouter.RouteInfo routeInfo =
        // mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        // Log.d("aaa", "getCurrentOutput:
        // "+audioManager?.isSpeakerphoneOn()+audioManager?.isWiredHeadsetOn()+audioManager?.isSpeakerphoneOn());
        // info.add(routeInfo.getName().toString());
        // info.add(getDeviceType(routeInfo.getDeviceType()));
        // } else {
        // info.add("unknow");
        // info.add("0");
        // }
        // return info;
    }

    private fun getAvailableInputs(): List<List<String>> {
        val list: MutableList<List<String>> = ArrayList()
        list.add(listOf("Receiver", "1"))
        if (audioManager?.isWiredHeadsetOn == true) {
            list.add(listOf("Headset", "3"))
        }
        if (audioManager?.isBluetoothScoOn == true) {
            list.add(listOf("Bluetooth", "4"))
        }
        return list
    }

    private fun getDeviceType(type: Int): String {
        Log.d("type", "type: $type")
        return when (type) {
            3 -> "3"
            2 -> "2"
            1 -> "4"
            else -> "0"
        }
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel =
            MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_audio_manager")
        channel.setMethodCallHandler(this)
        val receiver = AudioChangeReceiver(listener)
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        activeContext = flutterPluginBinding.applicationContext
        activeContext?.registerReceiver(receiver, filter)
        audioManager = activeContext?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}