# Savage Wind on Android — branch handoff

This branch (`tester`) holds the complete SavageWindRecomp source and the start of an Android port.
It is a **checkpoint, not a working port**. Read "Status" before trusting anything here.

## Layout

    savagewind/                 upstream SavageWindRecomp @ 5270ac2, history grafted under this prefix
    savagewind/android/         Android host shell: Gradle + NDK/CMake + a JNI stub
    .github/workflows/savagewind-apk.yml   manual debug-APK build for the shell

Upstream: https://github.com/Revivedx/SavageWindRecomp (MIT for its own code; the game is Nintendo's and is
never included). Nothing here contains game files, keys, firmware or recompiled game code, and nothing may.

## Why this branch, not the private `tester` repository

The port was asked for in a new private repository. This chat's authenticated push path is bound to
`typhon-op/pes2017-recomp`, and there is no tool here that creates repositories or authenticates to another
one, so the work was parked on a branch instead of being lost. To move it:

1. Create `typhon-op/tester` (private) and grant the Freebuff GitHub app access to it.
2. Start a Cloud chat on that repository so its worktree *is* `tester`.
3. Import upstream there (`git subtree add --prefix=.` or a prefixture-free import — root layout, not `savagewind/`).
4. `git remote -v` must print `typhon-op/tester` before the first commit. That check is the whole point.

## Status

| Item | State |
|---|---|
| Upstream source imported, history intact | done (929 files, 232 commits) |
| Analysis of both projects | done (below) |
| Android Gradle/NDK/CMake project | scaffolded, builds a **host shell only** |
| Native runtime linked into the APK | **not started** |
| Platform layer (`runtime/platform/android.c`) | **not started** |
| Thermal monitor / adaptive quality / frame generation | **not started** |
| Debug APK from CI | workflow exists, manual trigger, **not yet run** |

Do not describe the scaffold APK as the port. It is a launcher that proves the Gradle → CMake → NDK path works.

## What upstream actually is (verified)

Savage Wind statically recompiles *Breath of the Wild* (Switch 1.9.0) into a locally generated game library that
a host runtime loads. It is **not** a Windows game wrapper, and it is not Xbox 360 code like the PES reference.

- `runtime/` — ~140 C files: HLE Horizon OS services, settings, UI, profiling, controllers, audio.
- `runtime/nvn/` — Switch NVN graphics objects, Maxwell shader translation, Vulkan backend
  (`runtime/nvn/vulkan/`, sync model documented in `vk.h`, deferred destruction in `textures.c`).
- `tools/` — NSO loading, code generation, instruction lifting, guest patches (Python + `tools/venv`).
- Host OS layer is a single narrow contract: `runtime/platform.h`, implemented by `runtime/platform/windows.c`,
  `runtime/platform/macos.c` and `platform/linux_compat.h`. **An Android implementation slots in here.**
- Build: one 51 KB `Makefile` (MSYS2/clang on Windows, clang on Linux). No CMake in the build path.
- Renderer: Vulkan + shaderc-translated shaders, VMA, FidelityFX (FSR 1/3/4, **FSR 3 frame generation**), SMAA,
  ImGui, SDL3, FFmpeg (video path), stb.
- Windows assumptions to replace: `VirtualAlloc` guest-window reservation (`0x70_0000_0000`), vectored exception
  handler for stray first touches, `MEM_WRITE_WATCH` dirty-page tracking, DLL/DLL-main startup, `_WIN32` paths.

### The two facts that make or break this port

1. **ARM64 is already supported.** `make ARCH=aarch64` cross-builds a Linux AppImage, and the generator can lower
   guest FP/SIMD to portable C (`--simd=c`) instead of inline asm. The CPU side of an Android port is therefore
   not from scratch.
2. **Memory is the hard risk.** Upstream asks for 16 GB of RAM, a 3072 MB host heap and a ~3120 MB guest heap
   (see `docs/PERF-ROUTES.md`, Steam Deck baseline). Phones ship 8–12 GB with aggressive low-memory killers.
   A 3 GB guest window plus a 3 GB host heap on a phone is not assumed to work; it must be measured early,
   and if it fails the whole port changes shape. Treat this as the first real question, not an optimisation.

Also unverified on mobile: Adreno/Mali Vulkan 1.3 coverage, shaderc availability for Android ABIs, and whether the
Maxwell→SPIR-V path hits driver limits on mobile.

## The PES 2017 reference (what to reuse, where it lives)

These are the systems to study in `typhon-op/pes2017-recomp` and adapt — adapt, not copy, because the architectures
differ (Xbox 360 Xenos vs Switch Maxwell):

- `android/app/src/main/java/com/pes2017/recomp/DeviceProfile.kt` — device tier detection used for presets.
- `.../Prefs.kt`, `.../QuickGraphics.kt`, `.../GraphicsOptions.kt` — settings model and persistence.
- `.../NativeBridge.kt` — Kotlin ↔ JNI channel for runtime switches.
- `runtime/android/jni.cpp` — the native side of that bridge.
- `runtime/gpu/vulkan/dynamic_scale.h` — dynamic render-scale plumbing.
- `runtime/kernel/yield_backoff.h` — CPU wait/yield behaviour.
- `docs/frame-generation.md` — the frame-generation design notes.
- `docs/pillars/` — CPU affinity, mobile Vulkan pipeline, memory/LMK budgeting.

Android real APIs to use (no fabricated readings):
- `PowerManager.getCurrentThermalStatus()` / `addThermalStatusListener` (API 29+) for thermal pressure —
  `PowerManager.THERMAL_STATUS_NONE..SHUTDOWN`, with hysteresis and cooldown in our own governor.
- `ActivityManager.MemoryInfo` / `onTrimMemory` for memory pressure.
- `Display.getRefreshRate()` / `Display.Mode` for rates; `Choreographer` frame timings for pacing.

## Port plan (in order, each step ends in a commit)

0. **Import** — done.
1. **Build pipeline green.** Gradle + NDK + CMake producing a debug APK of the host shell; CI artifact.
   Proves toolchain and ABI before any runtime code is involved.
2. **Platform layer.** `savagewind/runtime/platform/android.c` implementing every function in `platform.h`:
   guest reservation (`mmap` + `MAP_FIXED_NOREPLACE`, page-size aware — Android uses 4 KB and 16 KB pages),
   commit/decommit, monotonic time, thread priority, CPU count/placement, process and path helpers.
   Compile-check it standalone before linking anything.
3. **Surface and Vulkan.** SDL3 Android (`android_native_app_glue` or `SDL_main`), `ANativeWindow` surface,
   Vulkan instance/device selection, swapchain, surface loss on background, `VK_EXT` feature fallbacks.
4. **Filesystem.** Game files live outside the APK; the app must import them (SAF or app-specific external dir)
   and hand a data root to the runtime. Android has no `~/.local/share`; map the existing path helpers to it.
5. **Audio.** SDL3 audio device + the existing HLE audio renderer; resampling and latency tuning for phones.
6. **Input.** SDL3 controllers (Bluetooth pads) plus a touch overlay with the same mapping model as the PES port.
7. **Settings.** Existing `runtime/settings.c` with Android paths, plus Kotlin-side persistence.
8. **Thermal protection.** Kotlin `PowerManager` listener → JNI → governor with multiple levels, hysteresis and
   cooldown; steps down render scale → shadows → post effects; restores gradually.
9. **Adaptive quality.** Governor on frame time + thermal + memory, modes Automatic/Manual/High/Medium/Low.
10. **Frame generation.** Adapt the existing FSR 3 FG path (`third_party/FidelityFX`, `runtime/nvn/vulkan`) to
    mobile; motion vectors and optical flow cost must be measured. Off by default until verified on hardware.
11. **Performance.** Pipeline cache, ASTC textures, yield/backoff, affinity; measure with the existing
    `docs/PERF-ROUTES.md` harness rather than claiming numbers.
12. **Verification.** Install on a real device, confirm the game-code build path, record what works.

## What has not been verified

- Nothing has been compiled. This sandbox has no JDK, Android SDK, NDK, cmake, ninja or gradle, and no root.
- The APK workflow has never run.
- No device testing of any kind.
- Upstream's Windows/Linux build was not reproduced here either.
