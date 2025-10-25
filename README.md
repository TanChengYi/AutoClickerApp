# AutoClickerApp

Flutter auto-clicker app targeting Android with overlay controls and accessibility service.

## How to run

```
flutter pub get
flutter run
```

## Usage steps
1. Grant overlay permission.
2. Enable the AutoClick Accessibility Service.
3. Pick the tap point.
4. Adjust taps per second.
5. Press Start to begin auto-clicking.

## Notes
- For private sideload use only.
- Requires Android API level 24 or higher.
- Uses Android `dispatchGesture` via an accessibility service to automate taps.
