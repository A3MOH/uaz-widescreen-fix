$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$game = Split-Path -Parent $here
$jdk  = "C:\Program Files (x86)\RedHat\java-1.8.0-openjdk-1.8.0.372-1\bin"
$asm  = "C:\Program Files (x86)\Android\android-sdk\tools\lib"
$cp   = "$asm\asm-5.1.jar;$asm\asm-tree-5.1.jar"
$tmp  = Join-Path $env:TEMP "uaz_ws"
$javac14 = @("-nowarn", "-source", "1.4", "-target", "1.4", "-bootclasspath", "$game\jre\lib\rt.jar")

# Optional game-specific helpers. Each one is compiled on its own; if it does not fit this game
# (missing classes or fields), it is skipped and its hooks are not applied.
$optional = @("RainFX", "CockpitLook")

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$tmp\src","$tmp\h","$tmp\t" | Out-Null
Expand-Archive "$game\code.pak.bak" "$tmp\src" -Force -ErrorAction SilentlyContinue
if (-not (Test-Path "$tmp\src\UIWindow.class")) { Copy-Item "$game\code.pak.bak" "$tmp\code.zip"; Expand-Archive "$tmp\code.zip" "$tmp\src" -Force }

& "$jdk\javac.exe" @javac14 -cp "$tmp\src" -d "$tmp\h" "$here\UIAspect.java"
if ($LASTEXITCODE -ne 0) { Write-Host "UIAspect failed to compile, nothing patched"; exit 1 }

$extra = @()
foreach ($name in $optional) {
    $src = Join-Path $here "$name.java"
    if (-not (Test-Path $src)) { continue }
    if (Test-Path (Join-Path $here "no_build_$name.txt")) { Write-Host "skipped $name (no_build_$name.txt)"; continue }
    $out = Join-Path $tmp "o_$name"
    New-Item -ItemType Directory -Force $out | Out-Null
    & "$jdk\javac.exe" @javac14 -cp "$tmp\src;$tmp\h" -d $out $src 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0 -and (Test-Path "$out\$name.class")) {
        Write-Host "built $name"
        $extra += "$out\$name.class"
    } else {
        Write-Host "skipped $name (does not fit this game)"
    }
}

& "$jdk\javac.exe" -nowarn -cp $cp -d "$tmp\t" "$here\Patcher.java"
if ($LASTEXITCODE -ne 0) { Write-Host "Patcher failed to compile"; exit 1 }
& "$jdk\java.exe" -cp "$cp;$tmp\t" Patcher "$game\code.pak.bak" "$game\code.pak" "$tmp\h\UIAspect.class" "$game\code_inf.pak.bak" "$game\code_inf.pak" @extra
