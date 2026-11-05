# UAZ 4x4 Widescreen UI Patch

## Summary

The game lays out its menus and HUD in 0..1 screen coordinates and multiplies X by the full
screen width. On 16:9 and wider screens this stretches every UI texture horizontally.

This patch changes the game's Java UI code (`code.pak`) so UI elements keep a 4:3 aspect ratio:

- **Menus** are drawn in a centered 4:3 area (1440 px wide on 1920x1080).
- **HUD** elements keep their shape and stick to the nearest screen edge: left, center or right.
- **Full-width elements** (logic width >= 0.95, such as backgrounds) still fill the screen, so
  there are no black bars.
- Pixel offsets and icon sizes that used `width / 800` (cursor, button icons, text margins,
  option controls) now scale uniformly.

- **Resolution list** in Options → Video now shows every detected display mode of at least
  800x600, not only the six built-in 4:3 sizes.

On 4:3 and narrower screens the UI behaves exactly as before.

### What gets changed

| File | Change |
|---|---|
| `code.pak` | 13 patched classes (`UIWindow`, `UIDummy`, `UIButtonWithBackground`, `UIStatic`, `UITexture`, `UICursor`, `UIOptionsControl*`, `RallyUserInterface`, `MenuOptionsVideo`, `XpandRallyFonts`) plus new `UIAspect.class` |
| `code_inf.pak` | `UIAspect.class` added to the class list |

The originals are kept as `code.pak.bak` and `code_inf.pak.bak`.

## Prerequisites

To play:

- The game installed. These steps assume `F:\Games\UAZ`.
- Optional but recommended: [DXVK](https://github.com/doitsujin/dxvk) 32-bit `d3d9.dll` next to
  `4x4game.exe`. It fixes crashes in fullscreen mode (`D3DERR_DEVICELOST`).

To rebuild with `build.ps1`:

- **Windows PowerShell 5.1 or PowerShell 7.**
- **JDK 8** (`javac`, `java`). Newer JDKs can't build for Java 1.4. The default path in
  `build.ps1` is `C:\Program Files (x86)\RedHat\java-1.8.0-openjdk-1.8.0.372-1\bin`.
- **ASM 5.1**: `asm-5.1.jar` and `asm-tree-5.1.jar`. The default path is the Android SDK
  `tools\lib` folder. You can also get them from Maven Central (`org.ow2.asm`).
- **Original, unpatched paks** saved as `code.pak.bak` and `code_inf.pak.bak` in the game folder.
  The build always reads from these copies.

If your JDK 8 or ASM is somewhere else, change the `$jdk` and `$asm` paths at the top of
`build.ps1`.

## Install / Rebuild

1. On first install, back up the originals:
   ```powershell
   Copy-Item F:\Games\UAZ\code.pak     F:\Games\UAZ\code.pak.bak
   Copy-Item F:\Games\UAZ\code_inf.pak F:\Games\UAZ\code_inf.pak.bak
   ```
2. Run:
   ```powershell
   F:\Games\UAZ\widescreen_patch\build.ps1
   ```
   The build should end with `patched classes: 13`.

## Uninstall

```powershell
Copy-Item F:\Games\UAZ\code.pak.bak     F:\Games\UAZ\code.pak     -Force
Copy-Item F:\Games\UAZ\code_inf.pak.bak F:\Games\UAZ\code_inf.pak -Force
```

## Tuning

Change these constants in `UIAspect.java`, then run `build.ps1` again:

| Constant | Default | Meaning |
|---|---|---|
| `ASPECT` | `4/3` | Aspect ratio the UI is designed for |
| `FULL_WIDTH` | `0.95` | Elements at least this wide (in logic units) keep stretching to full screen |
| `HUD_LEFT_EDGE` | `0.4` | HUD elements whose center is left of this stick to the left edge |
| `HUD_RIGHT_EDGE` | `0.6` | HUD elements whose center is right of this stick to the right edge |
| `MIN_MODE_W` / `MIN_MODE_H` | `800` / `600` | Smallest display mode shown in the resolution list |

## Runtime switches

You can turn parts of the patch off without rebuilding. Create an empty file in
`widescreen_patch\` (or rename the matching `.off` file), then restart the game:

| File | Effect |
|---|---|
| `no_aspect.txt` | Turns off the 4:3 UI scaling (UI stretches like the original) |
| `no_reslist.txt` | Brings back the original 6-mode resolution list |

## Known limits

- Font choice and global text scale follow the 4:3 UI width (`XpandRallyFonts`). Glyph drawing
  itself happens inside `ChromeEngine2.dll` and is not changed.
- The 3D menu backdrop and HUD minimap use their own math and are not changed.
- The UI editor (`UIE*` classes) is not adjusted.
