# Build, deploy and launch, then report the single first genuine failure.
#
# One command per iteration, because each part was previously pasted separately and one of them
# being stale produced a misleading result: a launch ran the old jar and reported an error that had
# already been fixed. The script therefore rebuilds, verifies the jar, copies it into the instance,
# confirms the copy matches, and only then launches.

param(
    [int]$TimeoutSec = 175,
    # Save directory name to open automatically; empty leaves the client on the title screen.
    [string]$AutoloadWorld = '',
    [switch]$SkipBuild,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'

$project = $PSScriptRoot
$jar = Join-Path $project 'build\libs\Protactinium-alpha-0.0.1-1.10.2.jar'
$mods = 'D:\pcl\versions\1.10.2-Forge_12.18.3.2511\mods'
$python = 'C:\Users\moye136\AppData\Local\Programs\Python\Python312\python.exe'

if (-not $SkipBuild) {
    Write-Host '=== build ==='
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $project 'build.ps1')
    if ($LASTEXITCODE -ne 0) { throw "build failed with $LASTEXITCODE" }
}

# Every structural check runs before a launch. Each one corresponds to a failure mode that
# previously cost a launch cycle: a truncated jar, a mixin config naming a class the jar lacks, a
# shadow field that does not exist on its target, an injection target whose descriptor drifted, and
# a remapping mixin with no refmap entry.
foreach ($check in @('verify_jar.py', 'verify_mixins.py', 'check_refmap_coverage.py')) {
    $path = Join-Path $project "tools\$check"
    Write-Host "=== $check ==="
    & $python $path $jar
    if ($LASTEXITCODE -ne 0) { throw "$check failed" }
}

Write-Host '=== deploy ==='
$target = Join-Path $mods (Split-Path $jar -Leaf)
Remove-Item $target -Force -ErrorAction SilentlyContinue
Copy-Item $jar $target -Force
$sourceHash = (Get-FileHash $jar -Algorithm SHA256).Hash
$targetHash = (Get-FileHash $target -Algorithm SHA256).Hash
if ($sourceHash -ne $targetHash) { throw 'deployed jar does not match the built jar' }
Write-Host "deployed $target"

if ($NoLaunch) { return }

Write-Host '=== launch ==='
$launchArgs = @('-TimeoutSec', $TimeoutSec)
if ($AutoloadWorld) { $launchArgs += @('-AutoloadWorld', $AutoloadWorld) }
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $project 'launch-test.ps1') @launchArgs

# Report the first genuine failure rather than the cascade the loader prints after it.
$logFile = Join-Path $project 'run\protactinium-launch.log'
if (-not (Test-Path $logFile)) { Write-Host 'no log produced'; return }
$lines = Get-Content $logFile
$fatal = $lines | Select-String -Pattern 'Exception in thread|NoSuchMethodError|NoClassDefFoundError|ClassCastException|InvalidMixinException|InjectionError|MixinApplyError|Failed to' |
    Select-Object -First 1
Write-Host '=== first genuine failure ==='
if ($fatal) {
    Write-Host $fatal.Line
} else {
    Write-Host 'none found'
}
$reached = $lines | Select-String -Pattern 'Sound engine started|Setting user|LWJGL Version'
Write-Host ("=== startup markers: {0}" -f $reached.Count)
$reached | Select-Object -First 4 | ForEach-Object { Write-Host ('  ' + $_.Line.Trim()) }
