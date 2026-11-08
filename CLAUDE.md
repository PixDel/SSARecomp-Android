# CLAUDE.md: SSARecomp for Android

## Goal

Android port (arm64, Vulkan) by PixDel of tsapZ's [SSARecomp](https://github.com/tsapZ/SSARecomp),
the ReXGlue static recompilation of Skylanders: Spyro's Adventure (Xbox 360, `rom/default.xex`).
The game code, portal, menus and fixes come from SSARecomp and the shared libraries
(`thirdparty/rexglue-sdk`, `thirdparty/skylanders-portal`, both on their `android` branches);
this repository adds the Android build, the app (`android/`), drivers, touch and tablet tuning.
docs/android.md describes all of it.

## Rules

- Never commit game data: the extracted disc (`rom/`), generated code (`generated/` except
  `rexglue.cmake`), the user's Lossless.dll, signing keys (`C:/dev/keys`), driver `.so` files.
- Tests run on copies of the owner's data only: an own `--user_data_root`,
  `--portal_mode=software`, muted audio. Close the app on the test device afterwards.
- Work goes to the `android` branches of PixDel's repositories. No pull requests or pushes to
  tsapZ's or other upstream repositories unless the owner asks.
- Problems of the Windows version belong to tsapZ/SSARecomp; changes to shared code that would
  help it too are worth mentioning to the owner.

## Build

Windows host tools first (codegen runs on Windows), from the x64 Native Tools Command Prompt for
VS 2022:

    just submodules        # git submodule update --init --recursive
    just build-release     # Windows build, also needed for the codegen host tool

Then the Android side (docs/android.md has the full commands):

    cmake --build out/build/android-arm64 --target ssarecompiled rexgpu-xenos
    tools\android\build.ps1 -Release [-Install]

## Test device

Honor MagicPad 2 (Snapdragon 8s Gen 3, Adreno 735, 3000x1920, Android 16), bundled Turnip T30
driver. Frame rates in the README and docs/android.md come from it.
