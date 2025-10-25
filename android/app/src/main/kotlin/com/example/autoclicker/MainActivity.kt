package com.example.autoclicker

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private lateinit var platformChannelHandler: PlatformChannelHandler

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        platformChannelHandler = PlatformChannelHandler(this)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PlatformChannelHandler.METHOD_CHANNEL)
            .setMethodCallHandler(platformChannelHandler)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, PlatformChannelHandler.EVENT_CHANNEL)
            .setStreamHandler(platformChannelHandler)
    }

    override fun onResume() {
        super.onResume()
        if (::platformChannelHandler.isInitialized) {
            platformChannelHandler.emitPermissions()
        }
    }
}
