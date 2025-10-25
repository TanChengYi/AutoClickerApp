class AutoClickSettings {
  AutoClickSettings({
    required this.x,
    required this.y,
    required this.intervalMs,
  });

  final int? x;
  final int? y;
  final int intervalMs;

  double get tapsPerSecond => 1000.0 / intervalMs;

  AutoClickSettings copyWith({
    int? x,
    int? y,
    int? intervalMs,
  }) {
    return AutoClickSettings(
      x: x ?? this.x,
      y: y ?? this.y,
      intervalMs: intervalMs ?? this.intervalMs,
    );
  }
}
