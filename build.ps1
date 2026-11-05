$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$game = Split-Path -Parent $here
$jdk  = "C:\Program Files (x86)\RedHat\java-1.8.0-openjdk-1.8.0.372-1\bin"
$asm  = "C:\Program Files (x86)\Android\android-sdk\tools\lib"
$cp   = "$asm\asm-5.1.jar;$asm\asm-tree-5.1.jar"
$tmp  = Join-Path $env:TEMP "uaz_ws"
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$tmp\src","$tmp\h","$tmp\t" | Out-Null
Expand-Archive "$game\code.pak.bak" "$tmp\src" -Force -ErrorAction SilentlyContinue
if (-not (Test-Path "$tmp\src\UIWindow.class")) { Copy-Item "$game\code.pak.bak" "$tmp\code.zip"; Expand-Archive "$tmp\code.zip" "$tmp\src" -Force }
& "$jdk\javac.exe" -nowarn -source 1.4 -target 1.4 -bootclasspath "$game\jre\lib\rt.jar" -cp "$tmp\src" -d "$tmp\h" "$here\UIAspect.java"
& "$jdk\javac.exe" -nowarn -cp $cp -d "$tmp\t" "$here\Patcher.java"
& "$jdk\java.exe" -cp "$cp;$tmp\t" Patcher "$game\code.pak.bak" "$game\code.pak" "$tmp\h\UIAspect.class" "$game\code_inf.pak.bak" "$game\code_inf.pak"
