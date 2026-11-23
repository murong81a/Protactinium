# Launch the 1.10.2 test instance with a timeout, capture the log, and report what happened.
#
# This uses the instance's own profile JSON so the classpath matches what the launcher would build,
# including the lwjgl3ify harness jars and LWJGL 3 natives. The mod is already in the instance's
# mods directory, so FML discovers it the same way it would on a real install.
#
# The script deliberately does not use the RetroFuturaBootstrap main class's plugin arguments: it
# only needs a normal client launch, and anything the profile specifies is already in the JSON.

param(
    [int]$TimeoutSec = 200,
    [string]$JavaHome = 'D:\JAVA\java25',
    [string]$LogName = 'protactinium-launch.log',
    [switch]$KeepRunning,
    # Set when the goal is to see the title screen rather than to load a world.
    [switch]$QuitAfterLoad,
    # Save directory name to open automatically, so the terrain renderer can be exercised.
    [string]$AutoloadWorld = '',
    # Extra JVM arguments, e.g. -ExtraJvm '-Dactinium.probeProgramSteps=true'.
    [string[]]$ExtraJvm = @()
)

$ErrorActionPreference = 'Stop'

$project = $PSScriptRoot
$run = Join-Path $project 'run'
$gameDir = Join-Path $run 'game'
New-Item -ItemType Directory -Force $run, $gameDir | Out-Null


$profileName = '1.10.2-Forge_12.18.3.2511'
$verDir = Join-Path 'D:\pcl\versions' $profileName
$verJson = Join-Path $verDir "$profileName.json"
$verJar = Join-Path $verDir "$profileName.jar"
$libRoot = 'D:\pcl\libraries'
$assetsDir = 'D:\pcl\assets'
$nativesDir = Join-Path $verDir "$profileName-natives"
$java = Join-Path $JavaHome 'bin\java.exe'

foreach ($p in @($verJson, $verJar, $java)) {
    if (-not (Test-Path $p)) { throw "Missing: $p" }
}

$json = Get-Content $verJson -Raw | ConvertFrom-Json

$cp = New-Object System.Collections.Generic.List[string]
$cp.Add($verJar)
foreach ($lib in $json.libraries) {
    $parts = $lib.name -split ':'
    if ($parts.Count -lt 3) { continue }
    $group = $parts[0] -replace '\.', '\'
    $artifact = $parts[1]
    $version = $parts[2]
    $classifier = if ($parts.Count -ge 4) { '-' + $parts[3] } else { '' }
    $path = Join-Path $libRoot "$group\$artifact\$version\$artifact-$version$classifier.jar"
    if ((Test-Path $path) -and -not $cp.Contains($path)) { $cp.Add($path) }
}

# The game directory for this run is the project's own, but the mods live in the instance. Point
# the run at the instance so FML finds the mod and the config the user already has.
$launchGameDir = $verDir

$gameArgs = @(
    '--username', 'Dev',
    '--version', '1.10.2',
    '--gameDir', $launchGameDir,
    '--assetsDir', $assetsDir,
    '--assetIndex', '1.10',
    '--uuid', '00000000000000000000000000000000',
    '--accessToken', '0',
    '--userType', 'legacy',
    '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker'
)

$jvmArgs = @(
    '-Xmx4G',
    '-Dfile.encoding=UTF-8',
    "-Djava.library.path=$nativesDir",
    "-Dorg.lwjgl.librarypath=$nativesDir",
    # RetroFuturaBootstrap replaces the system class loader; the profile's main class refuses to
    # start without this flag, because its plugin phase has to run before any mod is on the path.
    '-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader',
    '-Dfml.ignoreInvalidMinecraftCertificates=true',
    '-Dfml.ignorePatchDiscrepancies=true'
)

if ($ExtraJvm.Count -gt 0) { $jvmArgs += $ExtraJvm }

# Optional: open a world automatically so the terrain renderer can be exercised without a click.
# Pass -AutoloadWorld '<save directory name>' to enable it; the title screen is drawn either way.
if ($AutoloadWorld) {
    $jvmArgs += "-Dprotactinium.autoloadWorld=$AutoloadWorld"
}

$javaMajor = 0
$previousEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try { $versionOut = & $java -version 2>&1 | Out-String } finally { $ErrorActionPreference = $previousEap }
if (($versionOut -split "`n" | Where-Object { $_ -match 'version' } | Select-Object -First 1) -match '"(\d+)') {
    $javaMajor = [int]$Matches[1]
}
if ($javaMajor -ge 18 -and $javaMajor -le 23) { $jvmArgs += '-Djava.security.manager=allow' }

foreach ($o in @(
    'java.base/java.lang=ALL-UNNAMED',
    'java.base/java.lang.reflect=ALL-UNNAMED',
    'java.base/java.util=ALL-UNNAMED',
    'java.base/java.nio=ALL-UNNAMED',
    'java.base/java.io=ALL-UNNAMED',
    'java.base/java.net=ALL-UNNAMED',
    'java.base/java.util.jar=ALL-UNNAMED',
    'java.base/jdk.internal.misc=ALL-UNNAMED',
    'java.base/sun.nio.ch=ALL-UNNAMED',
    'java.desktop/sun.awt=ALL-UNNAMED'
)) { $jvmArgs += @('--add-opens', $o) }

if ($QuitAfterLoad) {
    # Close the game once the title screen has been reached, so an unattended run terminates
    # instead of sitting on the menu. Implemented with a shutdown delay the client honours.
    $jvmArgs += '-Dfml.autoQuitAfterLoad=true'
}

$logFile = Join-Path $run $LogName
if (Test-Path $logFile) { Remove-Item $logFile -Force }

$argList = @('-cp', ($cp -join ';'), $json.mainClass) + $gameArgs

$cmdFile = Join-Path $run 'launch.cmd'
$cmdLines = New-Object System.Collections.Generic.List[string]
$cmdLines.Add('@echo off')
$cmdLines.Add('cd /d "' + $launchGameDir + '"')
$quoted = foreach ($a in (@($jvmArgs) + $argList)) {
    if ($a -match '[\s=]') { '"' + $a + '"' } else { $a }
}
$cmdLines.Add('"' + $java + '" ' + ($quoted -join ' ') + ' > "' + $logFile + '" 2>&1')
$cmdLines -join "`r`n" | Set-Content -Encoding ASCII $cmdFile

Write-Host "Java:     $java (major $javaMajor)"
Write-Host "Game dir: $launchGameDir"
Write-Host "Log:      $logFile"
Write-Host "Mods:"
Get-ChildItem (Join-Path $launchGameDir 'mods') -Filter '*.jar' | ForEach-Object { "  $($_.Name)" }

if ($KeepRunning) {
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "`"$cmdFile`"" -WindowStyle Minimized
    Write-Host "Started detached."
    return
}

$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "`"$cmdFile`"" -PassThru -WindowStyle Hidden
$exited = $proc.WaitForExit($TimeoutSec * 1000)
if (-not $exited) {
    Write-Host "Still running after $TimeoutSec s; stopping."
    Get-Process java -ErrorAction SilentlyContinue |
        Where-Object { $_.StartTime -gt $proc.StartTime.AddSeconds(-5) } |
        ForEach-Object { $_.Kill() }
    Start-Sleep -Seconds 3
}

if (Test-Path $logFile) {
    $log = Get-Content $logFile
    Write-Host "===== summary ($($log.Count) lines) ====="
    $patterns = @{
        'Crash'            = 'Exception in thread|FATAL|A fatal error'
        'Mixin applied'    = 'Mixin apply|Mixins:|Successfully applied'
        'Actinium loaded'  = 'Actinium|Protactinium'
        'Terrain active'   = 'celeritas|Celeritas|indirect terrain|terrain renderer'
        'GL info'          = 'OpenGL|GL_VERSION|LWJGL Version'
        'Startup reached'  = 'Sound engine started|Created: .* textures-atlas|Setting user'
    }
    foreach ($key in $patterns.Keys) {
        $hits = $log | Select-String -Pattern $patterns[$key]
        Write-Host ("{0,-18} {1}" -f $key, $hits.Count)
    }
    Write-Host "===== tail ====="
    $log | Select-Object -Last 40
}
