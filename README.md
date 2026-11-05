# UAZ 4x4 Widescreen UI Patch

**Патчер для Полный привод: УАЗ 4x4**
Исправляет отображение интерфейса при соотношениях сторон, отличных от 4:3, за счёт изменения логики отрисовки UI. Работает так же для Полный привод 2(и, скорее всего, для остальных игр с текущим движком и фреймворком, выпущенных ASE)

## Как это работает

Игра размещает меню и HUD в экранных координатах от 0 до 1 и умножает X на полную ширину
экрана. На экранах 16:9 и шире из-за этого все текстуры интерфейса, которые берутся из
текстурных атласов игры, растягиваются по горизонтали.

Патч изменяет Java-код интерфейса игры в `code.pak`, чтобы элементы UI сохраняли пропорции 4:3:

- **Меню** рисуются в области 4:3 по центру экрана (ширина 1440 px при 1920x1080).
- **Элементы HUD** сохраняют форму и прижимаются к ближайшему краю экрана: левому, центру или правому.
- **Элементы на всю ширину** (логическая ширина >= 0.95, например фоны) по-прежнему заполняют
  весь экран, поэтому чёрных полос нет.
- Пиксельные отступы и размеры иконок, которые считались через `width / 800` (курсор, иконки
  кнопок, отступы текста, элементы настроек), теперь масштабируются равномерно.

- **Список разрешений** в «Настройки ? Видео» теперь показывает все найденные режимы экрана
  от 800x600, а не только встроенные размеры 4:3. На стандартных экранах 4:3 патч ничего не меняет.

### Что изменяется

| Файл | Изменение |
|---|---|
| `code.pak` | 13 изменённых классов (`UIWindow`, `UIDummy`, `UIButtonWithBackground`, `UIStatic`, `UITexture`, `UICursor`, `UIOptionsControl*`, `RallyUserInterface`, `MenuOptionsVideo`, `XpandRallyFonts`) и новый `UIAspect.class` |
| `code_inf.pak` | `UIAspect.class` добавлен в список классов |

Оригиналы сохраняются как `code.pak.bak` и `code_inf.pak.bak`.

## Требования
- Желательно издание "Полный привод: Весь УАЗ", т.к. в нём отсутствует Starforce DRM

**Необязательно, но рекомендуется**:
- [DXVK](https://github.com/doitsujin/dxvk): 32-битный `d3d9.dll` рядом с
  `4x4game.exe`. Исправляет вылеты в полноэкранном режиме (`D3DERR_DEVICELOST`).
- [Large Address Aware Patcher](https://ntcore.com/files/4gb_patch.zip) для патча 4x4game.exe для использования 4GB адресного пространства(уменьшит вылеты на современных системах)

Для сборки через `build.ps1`:

- **Windows PowerShell 5.1 или PowerShell 7.**
- **JDK 8** (`javac`, `java`). Более новые JDK не умеют собирать под Java 1.4. Путь по умолчанию
  в `build.ps1`: `C:\Program Files (x86)\RedHat\java-1.8.0-openjdk-1.8.0.372-1\bin`.
- **ASM 5.1**: `asm-5.1.jar` и `asm-tree-5.1.jar`. Путь по умолчанию: папка `tools\lib`
  в Android SDK. Их также можно скачать из Maven Central (`org.ow2.asm`).
- **Оригинальные, непропатченные pak-файлы**, сохранённые как `code.pak.bak` и `code_inf.pak.bak`
  в папке игры. Патчер всегда берёт файлы из этих копий.

Если JDK 8 или ASM лежат в другом месте, измените пути `$jdk` и `$asm` в начале `build.ps1`.

## Установка / пересборка

1. При первой установке сделайте резервную копию оригиналов(через PowerShell или руками):
   ```powershell
   Copy-Item <GAME INSTALL DIR>\code.pak     <GAME INSTALL DIR>\code.pak.bak
   Copy-Item <GAME INSTALL DIR>\code_inf.pak <GAME INSTALL DIR>\code_inf.pak.bak
   ```
2. Скопируйте папку `widescreen_patch` в папку игры.
3. Запустите:
   ```powershell
   <GAME INSTALL DIR>\widescreen_patch\build.ps1
   ```
   Сборка должна закончиться строкой `patched classes: 13`.

## Удаление

```powershell
Copy-Item <GAME INSTALL DIR>\code.pak.bak     <GAME INSTALL DIR>\code.pak     -Force
Copy-Item <GAME INSTALL DIR>\code_inf.pak.bak <GAME INSTALL DIR>\code_inf.pak -Force
```

## Настройка

Измените эти константы в `UIAspect.java`, затем снова запустите `build.ps1`:

| Константа | По умолчанию | Значение |
|---|---|---|
| `ASPECT` | `4/3` | Соотношение сторон, под которое сделан интерфейс |
| `FULL_WIDTH` | `0.95` | Элементы шире этого значения (в логических единицах игры) растягиваются на весь экран |
| `HUD_LEFT_EDGE` | `0.4` | Элементы HUD, центр которых левее этого значения, прижимаются к левому краю |
| `HUD_RIGHT_EDGE` | `0.6` | Элементы HUD, центр которых правее этого значения, прижимаются к правому краю |
| `MIN_MODE_W` / `MIN_MODE_H` | `800` / `600` | Минимальный режим экрана, который попадает в список разрешений |

## Переключатели без пересборки

Части патча можно отключить без пересборки. Создайте пустой файл в `widescreen_patch\`
(или переименуйте соответствующий файл `.off` из комплекта), затем перезапустите игру:

| Файл | Действие |
|---|---|
| `no_aspect.txt` | Отключает масштабирование UI в 4:3 (интерфейс растягивается, как в оригинале) |
| `no_reslist.txt` | Возвращает оригинальный список из 6 разрешений |

## Известные ограничения

- Выбор шрифта и общий масштаб текста следуют ширине области 4:3 (`XpandRallyFonts`). Сама
  отрисовка символов происходит внутри `ChromeEngine2.dll` и не изменяется.
- 3D-фон меню и мини-карта HUD используют собственные расчёты и не изменяются.
- Редактор интерфейса (классы `UIE*`) не адаптирован.
- В некоторых местах текст выровнен неправильно, ещё ищу причину.

---

# UAZ 4x4 Widescreen UI Patch

**Patcher for UAZ 4x4 Racing**
Fixes UI rendering in non 4:3 aspect ratios by changing UI render logic.  

## How this works

The game lays out its menus and HUD in 0..1 screen coordinates and multiplies X by the full
screen width. On 16:9 and wider screens this stretches every UI texture horizontally, which are pulled and mapped from game's texture atlases.

This patch changes the game's Java UI code in `code.pak` so UI elements keep a 4:3 aspect ratio:

- **Menus** are drawn in a centered 4:3 area (1440 px wide on 1920x1080).
- **HUD** elements keep their shape and stick to the nearest screen edge: left, center or right.
- **Full-width elements** (logic width >= 0.95, such as backgrounds) still fill the screen, so
  there are no black bars.
- Pixel offsets and icon sizes that used `width / 800` (cursor, button icons, text margins,
  option controls) now scale uniformly.

- **Resolution list** in Options -> Video now shows every detected display mode of at least
  800x600, not only built-in 4:3 sizes. This patch does not affect on standard 4:3 aspect ratios

### What gets changed

| File | Change |
|---|---|
| `code.pak` | 13 patched classes (`UIWindow`, `UIDummy`, `UIButtonWithBackground`, `UIStatic`, `UITexture`, `UICursor`, `UIOptionsControl*`, `RallyUserInterface`, `MenuOptionsVideo`, `XpandRallyFonts`) plus new `UIAspect.class` |
| `code_inf.pak` | `UIAspect.class` added to the class list |

The originals are kept as `code.pak.bak` and `code_inf.pak.bak`.

## Prerequisites
- UAZ 4x4 Racing Gold Collection
**Optional but recommended**: 
- [DXVK](https://github.com/doitsujin/dxvk) 32-bit `d3d9.dll` next to
  `4x4game.exe`. It fixes crashes in fullscreen mode (`D3DERR_DEVICELOST`).
- [Large Address Aware Patcher](https://ntcore.com/files/4gb_patch.zip) to patch 4x4game.exe to use 4GB address space

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
   Copy-Item <GAME INSTALL DIR>\code.pak     <GAME INSTALL DIR>\code.pak.bak
   Copy-Item <GAME INSTALL DIR>\code_inf.pak <GAME INSTALL DIR>\code_inf.pak.bak
   ```
2. Copy `widescreen_patch` dir in game install dir
3. Run:
   ```powershell
   <GAME INSTALL DIR>\widescreen_patch\build.ps1
   ```
   The build should end with `patched classes: 13`.

## Uninstall

```powershell
Copy-Item <GAME INSTALL DIR>\code.pak.bak     <GAME INSTALL DIR>\code.pak     -Force
Copy-Item <GAME INSTALL DIR>\code_inf.pak.bak <GAME INSTALL DIR>\code_inf.pak -Force
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
`widescreen_patch\` (or rename the matching `.off` file included), then restart the game:

| File | Effect |
|---|---|
| `no_aspect.txt` | Turns off the 4:3 UI scaling (UI stretches like the original) |
| `no_reslist.txt` | Brings back the original 6-mode resolution list |

## Known limits

- Font choice and global text scale follow the 4:3 UI width (`XpandRallyFonts`). Glyph drawing
  itself happens inside `ChromeEngine2.dll` and is not changed.
- The 3D menu backdrop and HUD minimap use their own math and are not changed.
- The UI editor (`UIE*` classes) is not adjusted.
- Some text alignment here and there is misplaced, will look for it