import 'dart:async';

import 'package:autoclicker/services/auto_clicker_channel.dart';
import 'package:autoclicker/models/settings.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AutoClickerApp());
}

class AutoClickerApp extends StatefulWidget {
  const AutoClickerApp({super.key});

  @override
  State<AutoClickerApp> createState() => _AutoClickerAppState();
}

class _AutoClickerAppState extends State<AutoClickerApp> {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AutoClicker',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const AutoClickHomePage(),
    );
  }
}

class AutoClickHomePage extends StatefulWidget {
  const AutoClickHomePage({super.key});

  @override
  State<AutoClickHomePage> createState() => _AutoClickHomePageState();
}

class _AutoClickHomePageState extends State<AutoClickHomePage> {
  final AutoClickerChannel _channel = AutoClickerChannel.instance;
  AutoClickSettings _settings = AutoClickSettings(x: null, y: null, intervalMs: 500);
  bool _overlayGranted = false;
  bool _accessibilityEnabled = false;
  bool _isRunning = false;
  bool _isLoading = true;
  final TextEditingController _tpsController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _init();
    _channel.listen(
      onPointPicked: (x, y) async {
        await _persistPoint(x, y);
        setState(() {
          _settings = _settings.copyWith(x: x, y: y);
        });
      },
      onRunningChanged: (running) async {
        setState(() {
          _isRunning = running;
        });
        await _channel.setPointEditingEnabled(!running);
        },
      onPermissionsChanged: ({required overlayGranted, required accessibilityEnabled}) {
        setState(() {
          _overlayGranted = overlayGranted;
          _accessibilityEnabled = accessibilityEnabled;
        });
        SharedPreferences.getInstance().then(
          (prefs) => prefs.setBool('accessibility_enabled', accessibilityEnabled),
        );
      },
    );
  }

  Future<void> _init() async {
    final prefs = await SharedPreferences.getInstance();
    final x = prefs.getInt('tap_x');
    final y = prefs.getInt('tap_y');
    final intervalMs = prefs.getInt('interval_ms') ?? 500;
    _settings = AutoClickSettings(x: x, y: y, intervalMs: intervalMs);
    _tpsController.text = (1000 / intervalMs).toStringAsFixed(1);
    await _channel.updateInterval(intervalMs);
    if (x != null && y != null) {
      await _channel.setTapPoint(x: x, y: y);
    }
    _overlayGranted = await _channel.isOverlayPermissionGranted();
    _accessibilityEnabled = prefs.getBool('accessibility_enabled') ?? false;
    _isRunning = false;
    setState(() {
      _isLoading = false;
    });
    await _channel.showOverlay();
  }

  Future<void> _persistPoint(int x, int y) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('tap_x', x);
    await prefs.setInt('tap_y', y);
  }

  Future<void> _persistInterval(int intervalMs) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('interval_ms', intervalMs);
  }

  bool get _hasValidPoint => _settings.x != null && _settings.y != null;

  bool get _canStart => _overlayGranted && _accessibilityEnabled && _hasValidPoint && !_isRunning;

  Future<void> _onPickPointPressed() async {
    if (_isRunning) {
      Fluttertoast.showToast(msg: 'Stop first to reposition');
      return;
    }
    await _channel.enterPointPickMode();
  }

  Future<void> _onStartStop() async {
    if (_isRunning) {
      await _channel.stopAutoClick();
      await _channel.setPointEditingEnabled(true);
      setState(() {
        _isRunning = false;
      });
      Fluttertoast.showToast(msg: 'Auto-clicking stopped');
    } else {
      if (!_overlayGranted) {
        Fluttertoast.showToast(msg: 'Overlay permission required');
        return;
      }
      if (!_accessibilityEnabled) {
        Fluttertoast.showToast(msg: 'Enable accessibility service first');
        return;
      }
      if (!_hasValidPoint) {
        Fluttertoast.showToast(msg: 'Pick a tap point first');
        return;
      }
      await _channel.setPointEditingEnabled(false);
      await _channel.startAutoClick(
        x: _settings.x!,
        y: _settings.y!,
        intervalMs: _settings.intervalMs,
      );
      setState(() {
        _isRunning = true;
      });
      Fluttertoast.showToast(msg: 'Auto-clicking started');
    }
  }

  Future<void> _updateTps(double value) async {
    final clamped = value.clamp(0.5, 20.0);
    final intervalMs = (1000 / clamped).round();
    setState(() {
      _settings = _settings.copyWith(intervalMs: intervalMs);
      _tpsController.text = clamped.toStringAsFixed(1);
    });
    await _persistInterval(intervalMs);
    await _channel.updateInterval(intervalMs);
  }

  Future<void> _onTpsSubmitted(String value) async {
    final parsed = double.tryParse(value);
    if (parsed == null) {
      Fluttertoast.showToast(msg: 'Invalid number');
      _tpsController.text = (1000 / _settings.intervalMs).toStringAsFixed(1);
      return;
    }
    await _updateTps(parsed);
  }

  @override
  void dispose() {
    _tpsController.dispose();
    unawaited(_channel.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const MaterialApp(
        home: Scaffold(
          body: Center(child: CircularProgressIndicator()),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('AutoClicker'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildPermissionSection(),
            const SizedBox(height: 16),
            _buildTapPointSection(),
            const SizedBox(height: 16),
            _buildTpsSection(),
            const SizedBox(height: 24),
            _buildStartStopButton(),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Permissions', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                FilledButton.tonal(
                  onPressed: () async {
                    await _channel.requestOverlayPermission();
                    final granted = await _channel.isOverlayPermissionGranted();
                    setState(() {
                      _overlayGranted = granted;
                    });
                  },
                  child: const Text('Enable Overlay Permission'),
                ),
                FilledButton.tonal(
                  onPressed: () async {
                    await _channel.openAccessibilitySettings();
                  },
                  child: const Text('Enable Accessibility Service'),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              children: [
                Chip(
                  label: Text('Overlay ${_overlayGranted ? '✅' : '❌'}'),
                  backgroundColor: _overlayGranted ? Colors.green.shade100 : Colors.red.shade100,
                ),
                Chip(
                  label: Text('Accessibility ${_accessibilityEnabled ? '✅' : '❌'}'),
                  backgroundColor: _accessibilityEnabled ? Colors.green.shade100 : Colors.red.shade100,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTapPointSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Tap Point', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _isRunning ? null : _onPickPointPressed,
              child: const Text('Pick tap point'),
            ),
            const SizedBox(height: 12),
            Text('Selected: ${_settings.x ?? '-'}, ${_settings.y ?? '-'}'),
          ],
        ),
      ),
    );
  }

  Widget _buildTpsSection() {
    final tps = 1000 / _settings.intervalMs;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Taps per second', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            Slider(
              value: tps.clamp(0.5, 20.0),
              min: 0.5,
              max: 20,
              divisions: 39,
              label: tps.toStringAsFixed(1),
              onChanged: (value) {
                unawaited(_updateTps(value));
              },
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                const Text('Taps/sec:'),
                const SizedBox(width: 12),
                SizedBox(
                  width: 100,
                  child: TextField(
                    controller: _tpsController,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))],
                    onSubmitted: (value) {
                      unawaited(_onTpsSubmitted(value));
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text('Interval: ${_settings.intervalMs} ms'),
          ],
        ),
      ),
    );
  }

  Widget _buildStartStopButton() {
    final enabled = _isRunning || _canStart;
    return SizedBox(
      width: double.infinity,
      child: FilledButton(
        onPressed: enabled ? _onStartStop : null,
        child: Text(_isRunning ? 'Stop' : 'Start'),
      ),
    );
  }
}
