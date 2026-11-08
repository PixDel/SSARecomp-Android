# Android port

Spyro's Adventure on Android (arm64, Vulkan), built from the same recompiled code as the PC
version. Developed and measured on an Honor MagicPad 2 (Snapdragon 8s Gen 3, Adreno 735, Android
16). This branch (`android`) and its submodule branches are private work in progress.

## What's different from the PC version

- **Graphics driver.** Qualcomm's own Vulkan driver draws the 3D scene as one flat colour (it
  reads 4x multisampled depth as 0). The app loads a Mesa Turnip driver instead through
  libadrenotools: the first `.so` in `Android/data/de.pixdel.ssarecomp/files/drivers/` (tested:
  MrPurple's "T30", PurpleVK 26.3). `--vulkan_driver=<path>` picks another one for a test.
  Plain Mesa builds (K11MCH1 v26 R8, Banner v27) flicker or draw black: see *Open problems*.
- **3D picture size.** Tablet GPUs pay a lot per pixel for the 360's 4x MSAA and EDRAM emulation.
  The default 3D scene on Android is 560x352 (`scene_size`); the settings menu (Grafik →
  Größe des 3D-Bilds) offers 560x352 / 700x440 / 840x528 / 1120x704.
- **Touch controls**, drawn by an Android view over the game (`TouchOverlayView`), hidden
  while a controller is connected (`touch_controls` auto/on/off). The eye button hides them.
- **Settings menu** on the touch Menu button (or Back+RB on a controller). Changes that need a
  restart offer it right away; the app restarts itself (`RestartActivity`).
- **Frame generation** (optional): Lossless Scaling Frame Generation through lsfg-vk's framegen
  library. Needs the player's own `Lossless.dll` (from Lossless Scaling on Steam) copied to
  `Android/data/de.pixdel.ssarecomp/files/Lossless.dll`; Grafik → Frame Generation.
- **Display rate.** The app asks for the 60 Hz display mode (a 30 FPS game paces evenly there).

## Measured (MagicPad 2, Turnip T30, 2026-11-08)

Frame generation is very experimental (barely tested; uneven on displays fixed at 120 Hz).


| 3D picture | guest FPS |
|---|---|
| 1120x704 (Xbox 360) | 20 |
| 840x528 | 25 |
| 700x440 | 28 |
| 560x352 (default) | 30 steady |

CAS sharpening is free; FSR 1 upscaling to 3000x1920 costs about 3 FPS. Frame generation keeps
the game at ~30 FPS.

## Building

Needs: Visual Studio 2022 (for the Windows host tool), LLVM 19, Android NDK r30
(30.0.16248370), Android SDK (platform 35), Gradle 8.9, a JDK 17.

1. Build the Windows tools once (codegen runs on the host):
   `cmake --build --preset win-amd64-release --target ssarecomp_codegen` (see `docs/build.md`).
2. Configure and build the native side for arm64 (paths as on the development machine):
   ```
   cmake -S . -B out/build/android-arm64 -G Ninja ^
     -DCMAKE_TOOLCHAIN_FILE=%NDK%\build\cmake\android.toolchain.cmake ^
     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared ^
     -DCMAKE_BUILD_TYPE=Release -DREXSDK_DIR=thirdparty/rexglue-sdk ^
     "-DCMAKE_C_FLAGS=-march=armv8.2-a" "-DCMAKE_CXX_FLAGS=-march=armv8.2-a" ^
     -DREXGLUE_USE_VULKAN=ON -DREXGLUE_ENABLE_FIDELITYFX=OFF -DREXGLUE_ENABLE_TRACY=OFF ^
     -DREXGLUE_HOST_TOOL=<repo>/out/build/win-amd64-release/rexglue-out/rexglue.exe ^
     -DREXGLUE_LSFG_DIR=<checkout of GameNative/lsfg-vk-android with submodules>
   cmake --build out/build/android-arm64 --target ssarecompiled rexgpu-xenos
   ```
   `REXGLUE_LSFG_DIR` is optional (frame generation). Its `thirdparty/dxbc` submodule points to a
   repository that no longer exists; the same commit (78ab59a) is on
   `github.com/Username404-59/dxbc` (`git config submodule.thirdparty/dxbc.url ...`).
3. Package (and install): `tools\android\build.ps1 [-Install]`.

## Installing (player)

1. Install the APK (`SSARecomp-Android-<version>.apk` from the private repository's releases).
2. Open it: the setup screen asks for your own Xbox 360 ISO of Spyro's Adventure (USA/Europe).
   Its files (about 7.4 GB, without the console's `$SystemUpdate`) are copied into the app's
   folder and `default.xex` is checked against `docs/game/default.xex.sha256`. The ISO can be
   deleted afterwards. Needs the ISO's size in free storage.
3. Optional: the same screen takes `Lossless.dll` (from Lossless Scaling on Steam) for frame
   generation.
4. "Spielen". Later starts go straight into the game; a long press on the app icon →
   "Spieldateien" opens the setup screen again (another ISO, another DLL).

The Turnip driver comes with the APK (`libvulkan_turnip.so`, offered on Qualcomm/Adreno devices with
Android 11+). Menu -> Grafik -> Grafiktreiber (`vulkan_driver_choice`) picks it (`bundled`), a driver
imported on the setup screen (`custom`, kept in `files/drivers/`; a `.so` or an emulator driver
`.zip` with `meta.json`) or the system's (`system`). `--vulkan_driver=<path>` still overrides all
three for tests. The README lists what was tried on which driver.

## Android menu and touch

- The settings menu leaves out what only a PC has (window size, fullscreen, D3D12, VSync, Discord,
  keyboard and mouse, Problem melden, the SDK's full list); the frame rate (30/60) is on the
  Grafik page. Spiel beenden closes the app (`SSAActivity.quitGame`).
- Steuerung -> Bildschirm-Steuerung: `touch_controls` auto / on / menu (Menu and Portal only) / off,
  plus opacity and size. They apply at once.
- The menus take taps: rows, tabs, figures, and the button hints at the bottom (menu_draw
  `TouchHint` presses that button on the touch controller). While a menu is drawn, the on-screen
  controls step aside (`NoteMenuShown`). Taps reach ImGui through its input queue, so a quick tap
  inside one frame isn't lost (rexglue imgui_drawer.cpp).

The setup screen (`SetupActivity`, `XisoExtractor`) is plain Java and loads none of the game's
libraries; the extractor's output was compared file by file with extract-xiso's for the
supported ISO (identical).

## Release APK

`tools\android\build.ps1 -Release` builds `app-release.apk`, signed with the key named in
`C:/dev/keys/ssarecomp-release.properties` (outside the repository; `SSARECOMP_SIGNING=<file>`
names another). The debug APK is signed with the debug key, so switching between the two needs an
uninstall, and an uninstall deletes `Android/data/de.pixdel.ssarecomp` (game files, saves).

## Game files on the device

Everything lives in the app's own folder (no storage permission, adb can fill it):

```
Android/data/de.pixdel.ssarecomp/files/
  rom/            the extracted disc (rom/default.xex) - your own copy, never in git
  userdata/       saves, figures, logs (userdata/logs)
  drivers/        optional: a Turnip driver .so instead of the one in the APK
  ssarecomp.toml  settings (the settings menu writes it)
  Lossless.dll    optional, for frame generation - your own copy, never in git
```

Test runs take extra flags: `adb shell am start -n de.pixdel.ssarecomp/.SSAActivity --es args "--a=1 --b=2"`.

## Open problems

- **Newer Mesa drivers flicker.** With K11 R8 or Banner v27 about a third of the frames are
  wrong (flat colour or black) unless the driver flushes after every draw
  (`--vulkan_driver_debug=flushall`). Not fixed by widening every barrier of ours
  (`vulkan_debug_barriers`), nor by turning off concurrent binning, concurrent resolves, LRZ,
  sysmem/gmem or aliasing. These drivers are 25-30 % faster on the GPU when right.
- **Qualcomm's driver** reads 4x MSAA depth as 0; reworking the EDRAM depth transfers so they
  don't sample multisampled depth would make it (and maybe the newer Mesa) usable.
- **120 Hz policies.** Some systems pin the display to 120 Hz (Honor's game mode); the app's
  60 Hz request is then ignored and frame generation's frames aren't evenly paced.
- **Controllers** are untested on a device (the code path is SDL's, like on PC; touch merges
  into player 1, the second controller is player 2).
