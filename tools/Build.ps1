param(
    [string]$GameDir = 'D:\SteamLibrary\steamapps\common\ProjectZomboid',
    [string]$ViewpointJar = 'D:\SteamLibrary\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar',
    [string]$CeilingLightsJar = 'D:\SteamLibrary\steamapps\workshop\content\108600\3823979843\mods\CeilingLights\42\media\java\client\CeilingLights.jar',
    [string]$ZombieBuddyJar = 'D:\SteamLibrary\steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar',
    [string]$JdkDir = 'C:\Users\Maeiro\Documents\ChatGPT\PZ - Performance\build\jdk25\jdk-25.0.5+8'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root ('build\' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$classpath = "$ViewpointJar;$CeilingLightsJar;$ZombieBuddyJar;$GameDir\projectzomboid.jar"
$sources = @(Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JdkDir\bin\javac.exe" --release 17 -Xlint:all -classpath $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
Copy-Item "$root\resources\*.properties" $classes
$jar = Join-Path $root 'content\mods\CeilingLightsEditor\42\media\java\client\viewpoint-ceiling-light-sources.jar'
& "$JdkDir\bin\jar.exe" --create --date=2026-11-18T00:00:00Z --file $jar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed.' }
$cursorClasses = Join-Path $build 'cursor-classes'
New-Item -ItemType Directory -Force -Path $cursorClasses | Out-Null
$cursorSources = @(Get-ChildItem -LiteralPath "$root\src\cursor\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JdkDir\bin\javac.exe" --release 17 -Xlint:all -classpath $ViewpointJar -d $cursorClasses @cursorSources
if ($LASTEXITCODE -ne 0) { throw 'Cursor addon compilation failed.' }
$cursorJar = Join-Path $root 'content\mods\CeilingLightsEditor\42\media\viewpoint\addons\maeiro.ceiling-editor-cursor\ceiling-editor-cursor.jar'
& "$JdkDir\bin\jar.exe" --create --date=2026-11-18T00:00:00Z --file $cursorJar -C $cursorClasses .
if ($LASTEXITCODE -ne 0) { throw 'Cursor addon packaging failed.' }
$stage = Join-Path $root 'dist\CeilingLightsEditor\Contents\mods\CeilingLightsEditor'
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item "$root\content\mods\CeilingLightsEditor\*" $stage -Recurse -Force
if (Test-Path "$root\README.md") { Copy-Item "$root\README.md" "$root\dist\CeilingLightsEditor\README.md" }
Compress-Archive -Path "$root\dist\CeilingLightsEditor" -DestinationPath "$root\dist\CeilingLightsEditor.zip" -Force
Write-Output "Build=$build"
Write-Output "Classpath=$classpath"
Get-FileHash -LiteralPath $jar -Algorithm SHA256
