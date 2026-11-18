# BUSSID Auto Pilot — calibration & gesture test build

Package `com.bussid.autopilot` • Kotlin • minSdk 26 • targetSdk 35 • AGP 8.7.3 • Kotlin 2.0.21

## What this revision implements
- MediaProjection foreground service and downsampled frame processing.
- Lightweight Kotlin pixel heuristic for road-edge candidates, estimated center offset and bend direction. This is real frame processing, not a dummy feed, but it is not a trained road segmentation model/OpenCV.
- Steering and conservative throttle/brake decision calculation. Throttle is capped at 35% because no reliable in-game speedometer telemetry/OCR exists yet. `maxSpeed` is currently a target setting only, not a verified speed limiter.
- Persistent calibration for gas, brake, steering-left and steering-right touch coordinates as normalized screen positions (0–100% X/Y).
- Individual 180 ms Accessibility gesture test buttons. Each test launches BUSSID (`com.maleo.bussimulatorid`) and schedules the calibrated touch 2.8 seconds later so the game, not the dashboard, is foreground. Android reports whether a gesture completed/cancelled; that does not prove BUSSID registered the touch.
- Explicit opt-in live input switch. It remains OFF on launch and can only be enabled while capture and Accessibility are connected.
- Live actuator uses one Accessibility `GestureDescription` with simultaneous gas-or-brake and steering strokes. Actual multi-touch behavior depends on Android OEM, display, game control layout and BUSSID input handling; it is not guaranteed.
- Capture stop/disconnect disables automatic input.

## Safe setup and test sequence
1. Install the debug APK and open BUSSID Auto Pilot.
2. Open **BUKA PENGATURAN ACCESSIBILITY**, enable **BUSSID Auto Pilot Input**, then return to the app.
3. In BUSSID, enter a stationary/empty practice area or garage. Keep the bus stopped.
4. Start screen capture and approve Android's system prompt.
5. Calibrate X/Y for Gas, Brake, Steering Left and Steering Right. Values are relative to the whole display. Landscape orientation and in-game HUD layout must match the calibration.
6. Tap each individual **UJI** button. The app opens BUSSID and sends the test after 2.8 seconds. Check that the intended game control reacts. Gesture completion in Android is not proof of game response. If BUSSID is installed under a different package ID, the launch step will not work. If the wrong control reacts, adjust coordinates. Do not test while driving in traffic.
7. Keep live control OFF until all four individual tests behave correctly. Then tick the manual verification checkbox. Any coordinate adjustment invalidates that control’s gesture test and requires retesting. If you choose to experiment, enable the opt-in switch only in a safe, stationary practice area and be ready to stop capture immediately.
8. Stop using **STOP CAPTURE & INPUT** or the foreground notification action.

## Important limitations
- The detector is heuristic. Shadows, road texture, sky, dashboard overlays, night scenes, camera pitch, intersections and unusual road geometry can cause false road estimates. Confidence is only a heuristic score.
- There is no robust speedometer OCR/game telemetry. The max-speed setting cannot currently enforce a km/h ceiling. Automatic throttle is intentionally conservative and capped, but this does not make unattended driving safe.
- Android Accessibility gestures are not a game controller API. Simultaneous strokes can be cancelled, merged, ignored or interpreted differently across devices and game versions. A steering wheel/joystick UI may need a different calibrated gesture model than discrete left/right buttons.
- The current control loop is experimental and not suitable for public-road, unattended or long-duration operation. Do not rely on it to avoid collisions or stay in lane.
- The project currently uses Kotlin image processing, not OpenCV. OpenCV integration should follow after the frame pipeline and device performance are validated.
- No floating overlay is included in this revision.

## Build APK with GitHub Actions
1. Upload the repository contents to GitHub.
2. Push to `main` or run **Build Android APK** manually from Actions.
3. Download artifact `BUSSID-AutoPilot-debug` containing `app-debug.apk`.

Workflow uses JDK 17 and Gradle 8.9. Local build: `gradle :app:assembleDebug`.
