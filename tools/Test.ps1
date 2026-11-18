param(
    [string]$JdkDir = 'C:\Users\Maeiro\Documents\ChatGPT\PZ - Performance\build\jdk25\jdk-25.0.5+8'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
& "$PSScriptRoot\Build.ps1" -JdkDir $JdkDir
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
$build = Join-Path $root ('build\tests-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$vp = 'D:\SteamLibrary\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$cl = 'D:\SteamLibrary\steamapps\workshop\content\108600\3823979843\mods\CeilingLights\42\media\java\client\CeilingLights.jar'
$game = 'D:\SteamLibrary\steamapps\common\ProjectZomboid\projectzomboid.jar'
$zb = 'D:\SteamLibrary\steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar'
$jar = "$root\content\mods\CeilingLightsEditor\42\media\java\client\viewpoint-ceiling-light-sources.jar"
$cp = "$classes;$jar;$vp;$cl;$zb;$game"
$stubs = @(Get-ChildItem "$root\src\test\stubs" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JdkDir\bin\javac.exe" --release 17 -classpath $cp -d $classes @stubs
if ($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed.' }
$tests = @(Get-ChildItem "$root\src\test\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JdkDir\bin\javac.exe" --release 17 -classpath $cp -d $classes @tests
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& "$JdkDir\bin\java.exe" "-Dtest.cache=$build\cache" -classpath $cp maeiro.ceilinglights.RuntimeTest
if ($LASTEXITCODE -ne 0) { throw 'Runtime checks failed.' }
& "$JdkDir\bin\java.exe" -classpath $cp maeiro.ceilinglights.RoomFixtureTest "$build\room-data"
if ($LASTEXITCODE -ne 0) { throw 'Current-room filter checks failed.' }
$hooks = Join-Path $build 'hooks'
New-Item -ItemType Directory -Force -Path $hooks | Out-Null
$realCp = "$hooks;$jar;$vp;$cl;$zb;$game"
& "$JdkDir\bin\javac.exe" --release 17 -classpath $realCp -d $hooks "$PSScriptRoot\CheckHooks.java"
if ($LASTEXITCODE -ne 0) { throw 'Hook check compilation failed.' }
& "$JdkDir\bin\java.exe" -classpath $realCp CheckHooks
if ($LASTEXITCODE -ne 0) { throw 'Hook weaving checks failed.' }

$cursorJar = "$root\content\mods\CeilingLightsEditor\42\media\viewpoint\addons\maeiro.ceiling-editor-cursor\ceiling-editor-cursor.jar"
$editorTests = @(Get-ChildItem "$root\src\test\editor","$root\src\test\cursor" -Recurse -Filter '*.java' | ForEach-Object FullName)
$realCp = "$hooks;$cursorJar;$jar;$vp;$cl;$zb;$game"
& "$JdkDir\bin\javac.exe" --release 17 -classpath $realCp -d $hooks @editorTests "$PSScriptRoot\CheckLua.java"
if ($LASTEXITCODE -ne 0) { throw 'Editor checks compilation failed.' }
& "$JdkDir\bin\java.exe" -classpath $realCp maeiro.ceilinglights.EditorStoreTest "$build\editor-data"
if ($LASTEXITCODE -ne 0) { throw 'Editor storage checks failed.' }
& "$JdkDir\bin\java.exe" -classpath $realCp maeiro.ceilinglights.EditorGeometryTest "$build\geometry-data"
if ($LASTEXITCODE -ne 0) { throw 'Editor geometry checks failed.' }
& "$JdkDir\bin\java.exe" -classpath $realCp maeiro.ceilingcursor.CeilingEditorCursorTest
if ($LASTEXITCODE -ne 0) { throw 'Editor cursor checks failed.' }
& "$JdkDir\bin\java.exe" -classpath $realCp CheckLua "$root\content\mods\CeilingLightsEditor\42\media\lua"
if ($LASTEXITCODE -ne 0) { throw 'PZ Lua compilation failed.' }

& python "$PSScriptRoot\test_editor_ui.py"
if ($LASTEXITCODE -ne 0) { throw 'Editor Lua UI lifecycle failed.' }
