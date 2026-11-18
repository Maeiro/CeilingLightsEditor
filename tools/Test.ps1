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
$jar = "$root\content\mods\ViewpointCeilingLightSources\42\media\java\client\viewpoint-ceiling-light-sources.jar"
$cp = "$classes;$jar;$vp;$cl;$zb;$game"
$stubs = @(Get-ChildItem "$root\src\test\stubs" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JdkDir\bin\javac.exe" --release 17 -classpath $cp -d $classes @stubs
if ($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed.' }
$tests = @(Get-ChildItem "$root\src\test\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JdkDir\bin\javac.exe" --release 17 -classpath $cp -d $classes @tests
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& "$JdkDir\bin\java.exe" "-Dtest.cache=$build\cache" -classpath $cp maeiro.ceilinglights.RuntimeTest
if ($LASTEXITCODE -ne 0) { throw 'Runtime checks failed.' }
$hooks = Join-Path $build 'hooks'
New-Item -ItemType Directory -Force -Path $hooks | Out-Null
$realCp = "$hooks;$jar;$vp;$cl;$zb;$game"
& "$JdkDir\bin\javac.exe" --release 17 -classpath $realCp -d $hooks "$PSScriptRoot\CheckHooks.java"
if ($LASTEXITCODE -ne 0) { throw 'Hook check compilation failed.' }
& "$JdkDir\bin\java.exe" -classpath $realCp CheckHooks
if ($LASTEXITCODE -ne 0) { throw 'Hook weaving checks failed.' }
