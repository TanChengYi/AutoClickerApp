import 'dart:async';

import 'package:flutter/services.dart';

typedef PointPickedCallback = void Function(int x, int y);
typedef RunningChangedCallback = void Function(bool isRunning);
typedef PermissionsChangedCallback = void Function({required bool overlayGranted, required bool accessibilityEnabled});

class AutoClickerChannel {
  AutoClickerChannel._();

  static final AutoClickerChannel instance = AutoClickerChannel._();

  static const MethodChannel _methodChannel = MethodChannel('autoclicker/channel');
  static const EventChannel _eventChannel = EventChannel('autoclicker/events');

  StreamSubscription? _subscription;

  void listen({
    PointPickedCallback? onPointPicked,
    RunningChangedCallback? onRunningChanged,
    PermissionsChangedCallback? onPermissionsChanged,
  }) {
    _subscription ??= _eventChannel.receiveBroadcastStream().listen((event) {
      if (event is Map) {
        final type = event['type'];
        switch (type) {
          case 'onPointPicked':
            final x = event['x'];
            final y = event['y'];
            if (x is int && y is int) {
              onPointPicked?.call(x, y);
            }
            break;
          case 'onRunningChanged':
            final running = event['isRunning'];
            if (running is bool) {
              onRunningChanged?.call(running);
            }
            break;
          case 'onPermissionsChanged':
            final overlay = event['overlay'];
            final accessibility = event['accessibility'];
            if (overlay is bool && accessibility is bool) {
              onPermissionsChanged?.call(
                overlayGranted: overlay,
                accessibilityEnabled: accessibility,
              );
            }
            break;
        }
      }
    });
  }

  Future<void> dispose() async {
    await _subscription?.cancel();
    _subscription = null;
  }

  Future<void> requestOverlayPermission() async {
    await _methodChannel.invokeMethod('requestOverlayPermission');
  }

  Future<bool> isOverlayPermissionGranted() async {
    final result = await _methodChannel.invokeMethod<bool>('isOverlayPermissionGranted');
    return result ?? false;
  }

  Future<void> openAccessibilitySettings() async {
    await _methodChannel.invokeMethod('openAccessibilitySettings');
  }

  Future<void> startAutoClick({required int x, required int y, required int intervalMs}) async {
    await _methodChannel.invokeMethod('startAutoClick', {
      'x': x,
      'y': y,
      'intervalMs': intervalMs,
    });
  }

  Future<void> stopAutoClick() async {
    await _methodChannel.invokeMethod('stopAutoClick');
  }

  Future<void> showOverlay() async {
    await _methodChannel.invokeMethod('showOverlay');
  }

  Future<void> hideOverlay() async {
    await _methodChannel.invokeMethod('hideOverlay');
  }

  Future<void> setPointEditingEnabled(bool enabled) async {
    await _methodChannel.invokeMethod('setPointEditingEnabled', {
      'enabled': enabled,
    });
  }

  Future<void> enterPointPickMode() async {
    await _methodChannel.invokeMethod('enterPointPickMode');
  }

  Future<void> setTapPoint({required int x, required int y}) async {
    await _methodChannel.invokeMethod('setTapPoint', {
      'x': x,
      'y': y,
    });
  }

  Future<void> updateInterval(int intervalMs) async {
    await _methodChannel.invokeMethod('updateInterval', {
      'intervalMs': intervalMs,
    });
  }
}
