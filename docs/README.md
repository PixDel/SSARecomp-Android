# Documentation

The Android port's own page is [android.md](android.md); the rest describes the game's code, which
this port shares with [tsapZ/SSARecomp](https://github.com/tsapZ/SSARecomp) (its project status
and roadmap live there).

Pages, roughly in reading order:

| Page | What it covers |
|---|---|
| [android.md](android.md) | The Android port: building, files on the device, drivers, touch, measurements, open problems |
| [game/spyros-adventure.md](game/spyros-adventure.md) | The game, the target dump, its file formats and how it differs from Swap Force |
| [portal.md](portal.md) | The Portal of Power here: modes, the figure folder, the menu at the game's own portal screens, the portal check |
| [menus.md](menus.md) | Menus and keys: portal menu, player menu, settings menu |
| [development.md](development.md) | Building from source on Windows, scripted test runs |
| [build.md](build.md) | Codegen fixes (`config/default.toml`), kernel stubs, the fix loop, testing |
| [reverse-engineering.md](reverse-engineering.md) | Headless Ghidra: identify and name functions |

The shared parts are documented where they live: the portal library and its menus in
SwapForceRecomp's [portal.md](https://github.com/tsapZ/SwapForceRecomp/blob/main/docs/portal.md)
and [menus.md](https://github.com/tsapZ/SwapForceRecomp/blob/main/docs/menus.md), the high frame
rate research in its [high-framerate.md](https://github.com/tsapZ/SwapForceRecomp/blob/main/docs/high-framerate.md),
the hub and online co-op in [Skylanders: Core of Light](https://github.com/tsapZ/SkylandersCoreOfLight).

## Glossary

- **Static recompilation:** translating the game's PowerPC machine code into C++ once, ahead of
  time, and compiling that into a native Windows program. No emulation of the CPU at runtime.
- **ReXGlue:** the SDK that does the translation (codegen) and provides the runtime: Xbox 360
  kernel/XAM functions, the Xenos GPU on Direct3D 12/Vulkan, audio, input. Lives in
  `thirdparty/rexglue-sdk` (our fork, branch `skylanders`).
- **`default.xex`:** the game's main executable on the disc. Everything in `config/default.toml`
  is keyed to addresses inside exactly one version of this file.
- **Guest address:** an address in the original Xbox 360 program (e.g. `0x82C569B0`). Functions
  without a known purpose are named `sub_<address>`.
- **Midasm hook:** a call into our own C++ inserted at a guest address, declared in
  `config/default.toml` and implemented in `src/hooks/`.
- **Cvar:** a runtime setting (`REXCVAR_DEFINE_*`), set in `ssarecomp.toml`, by `--flag` or by a
  `REX_*` environment variable.
- **Input script:** a text file of timed controller input and probes (`--input_script`), for
  repeatable test runs; see development.md.
