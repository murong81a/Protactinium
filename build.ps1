# Protactinium: Actinium's optimisation half ported to Minecraft 1.10.2 (Forge 12.18.3.2511).
#
# Why no Gradle:
#   ForgeGradle 2.3 is the only resolver for 1.10.2 Forge and it only runs on Gradle 4.x,
#   which only runs on JDK 8. The upstream sources use records, var, and pattern matching,
#   so javac must be 17 or newer. This project therefore compiles with a modern JDK
#   directly against the deobfuscated 1.10.2 Forge jar that ForgeGradle already produced,
#   and reobfuscates with ForgeAutoRenamingTool plus the MCP stable_29 SRG, which is the
#   same two-step Gradle would have done. The script is explicit rather than magical so
#   every classpath entry can be inspected.
#
#   The target runtime is the user's 1.10.2 instance, which launches on Java 25 through
#   RetroFuturaBootstrap and LWJGL 3 (see lwjgl3ify-1102). Class file 61 (Java 17) loads
#   there, which is what upstream Actinium requires anyway (Java 21 runtime minimum).
#
# Staged compilation:
#   Each module is compiled into its own output directory and the previous modules are put
#   on the next stage's classpath. A failure in GTNHLib is therefore reported alone instead
#   of drowning in downstream "cannot find symbol" noise.

param(
    [string[]]$Stage = @('mccompat', 'gtnhlib', 'glsm', 'celeritas', 'shader', 'modapistubs', 'main'),
    [switch]$NoReobf,
    [switch]$KeepGoing,
    [switch]$Clean,
    [string]$Release = '21'
)

$ErrorActionPreference = 'Stop'

# $Clean clears every stage output before compiling; the automatic check below covers the common case.
$ClearStages = $Clean

$base = $PSScriptRoot
$root = Split-Path -Parent $base

$javaHome = 'D:\JAVA\java25'
$javac = Join-Path $javaHome 'bin\javac.exe'
$jarExe = Join-Path $javaHome 'bin\jar.exe'
$javaExe = Join-Path $javaHome 'bin\java.exe'

# Deobfuscated Forge 1.10.2 + Minecraft, MCP stable_29 names.
$forgeJar = Join-Path $root '_tmp\ysm\port\.gradle\minecraft\forgeBin-1.10.2-12.18.3.2511-PROJECT(port).jar'
if (-not (Test-Path $forgeJar)) { throw "1.10.2 Forge jar not found: $forgeJar" }

# MCP stable_29 SRG: maps MCP names to the SRG names a production client links against.
$srg = Join-Path $root '_tmp\ysm\gradle-home\caches\minecraft\de\oceanlabs\mcp\mcp_stable\29\1.10.2\srgs\mcp-srg.srg'
$fart = 'D:\pcl\libraries\net\minecraftforge\ForgeAutoRenamingTool\0.1.22\ForgeAutoRenamingTool-0.1.22-all.jar'

# Compile against a view of Forge that already has this mod's access transformer applied.
#
# ForgeGradle applies the AT while it builds the deobfuscated jar, so upstream always sees the
# widened members. The jar this port compiles against is that pipeline's output *before* the AT
# step, so without this the compiler rejects every `private` field an AT line widens, even though
# the shipped jar widens it again at runtime through the FMLAT manifest entry. The derived jar is
# rebuilt whenever the AT file changes, and it changes no bytecode other than access flags.
$forgeBase = Join-Path $root '_tmp\ysm\port\.gradle\minecraft\forgeBin-1.10.2-12.18.3.2511-PROJECT(port).jar'
if (-not (Test-Path $forgeBase)) { throw "1.10.2 Forge jar not found: $forgeBase" }
$atFile = Join-Path $base 'src\main\resources\actinium_at.cfg'
$forgeJar = Join-Path $base 'build\forgeBin-at.jar'
$python = 'C:\Users\moye136\AppData\Local\Programs\Python\Python312\python.exe'
$needAt = -not (Test-Path $forgeJar)
if (-not $needAt) { $needAt = (Get-Item $atFile).LastWriteTime -gt (Get-Item $forgeJar).LastWriteTime }
if ($needAt) {
    if (-not (Test-Path $python)) { throw "Python not found at $python (needed to apply the access transformer)" }
    New-Item -ItemType Directory -Force (Split-Path $forgeJar) | Out-Null
    $staged = Join-Path $base 'build\forgeBin-at-staged.jar'
    & $python (Join-Path $base 'tools\apply_at.py') $forgeBase $atFile $staged
    if ($LASTEXITCODE -ne 0) { throw "applying the access transformer failed with $LASTEXITCODE" }
    # Widening a nested class's own access flags is not enough: javac resolves `Foo.Bar` through
    # Foo's InnerClasses attribute, which carries a second copy of Bar's flags. Without this step
    # every nested AT target stays inaccessible to the compiler even though its class file says
    # public. See the header of patch_inner_classes.py.
    & $python (Join-Path $base 'tools\patch_inner_classes.py') $staged $atFile $forgeJar
    if ($LASTEXITCODE -ne 0) { throw "patching InnerClasses attributes failed with $LASTEXITCODE" }
    Remove-Item $staged -Force
}

$libs = Get-ChildItem (Join-Path $base 'libs') -File -Filter '*.jar' | ForEach-Object { $_.FullName }
$outRoot = Join-Path $base 'build\classes'
New-Item -ItemType Directory -Force $outRoot | Out-Null

$stages = @{
    # Back-implementations of host API that 1.10.2 does not have. Compiled first because every
    # other module may call into it; see COMPAT.md for the policy and the list of entries.
    'mccompat'  = @{ dir = Join-Path $base 'modules\mc-compat\src\main\java'; deps = @() }
    'gtnhlib'   = @{ dir = Join-Path $base 'modules\gtnhlib\src\main\java'; deps = @('mccompat') }
    'glsm'      = @{ dir = Join-Path $base 'modules\glsm\src\main\java'; deps = @('mccompat', 'gtnhlib'); extra = @(
                        (Join-Path $base 'modules\glsm\src\lwjglCommon'),
                        (Join-Path $base 'modules\glsm\src\lwjgl3')) }
    'lwjgl2'    = @{ dir = Join-Path $base 'modules\lwjgl2\src\main\java'; deps = @('mccompat', 'gtnhlib', 'glsm') }
    'celeritas' = @{ dir = Join-Path $base 'modules\celeritas\src\main\java'; deps = @('mccompat', 'gtnhlib', 'glsm') }
    # The Iris-style shader pipeline. Out of scope as a feature (the port does not ship shader pack
    # support), but the main module references net.coderbot.iris.* types at compile time from the
    # render path, so this module is still compiled: without it the main module cannot be built at
    # all. The ported binary only reaches these classes when a pack is loaded, which never happens
    # on this target because the shader pipeline is never started.
    'shader'    = @{ dir = Join-Path $base 'modules\shader\src\main\java'; deps = @('mccompat', 'gtnhlib', 'glsm', 'celeritas') }
    # Optional per-mod integrations. They are compiled after the core modules and before main so
    # the presence-gated call sites in Actinium.java resolve; at runtime every one of them checks
    # the mod list first. See COMPAT.md.
    # Compile-time-only declarations of mod APIs the core pipeline names. Never packaged.
    'modapistubs' = @{ dir = Join-Path $base 'modules\mod-api-stubs\src\main\java'; deps = @('mccompat') }
    'modcompat' = @{ dir = Join-Path $base 'modules\mod-compat\src\main\java'; deps = @('mccompat', 'gtnhlib', 'glsm', 'celeritas', 'shader') }
    'main'      = @{ dir = Join-Path $base 'src\main\java'; deps = @('mccompat', 'gtnhlib', 'glsm', 'celeritas', 'shader', 'modapistubs') }
}

# glsm's LWJGL abstraction (com.mitchej123.lwjgl.*) and the LWJGL 3 service implementation live in
# two extra source roots that upstream's glsm/build.gradle adds to that module. Every later module
# compiles against them, so they are emitted into the glsm output directory by an extra first pass
# rather than being copied or duplicated.
$glsmExtraRoots = @(
    (Join-Path $base 'modules\glsm\src\lwjglCommon'),
    (Join-Path $base 'modules\glsm\src\lwjgl3')
)

$logDir = Join-Path $base 'build\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null

$summary = @()

foreach ($name in $Stage) {
    if (-not $stages.ContainsKey($name)) { throw "Unknown stage: $name" }
    $spec = $stages[$name]
    $outDir = Join-Path $outRoot $name
    New-Item -ItemType Directory -Force $outDir | Out-Null

    $srcDirs = @($spec['dir']) + @($spec['extra'] | Where-Object { $_ })
    $sources = @()
    foreach ($d in $srcDirs) {
        if (Test-Path $d) {
            $sources += Get-ChildItem $d -Recurse -File -Filter '*.java' | ForEach-Object { $_.FullName }
        }
    }
    if ($sources.Count -eq 0) { throw "No sources for stage $name" }

    # Previous stages first, then third party, then Forge.
    $cpParts = @()
    foreach ($d in $spec['deps']) { $cpParts += (Join-Path $outRoot $d) }
    $cpParts += $libs
    $cpParts += $forgeJar
    $cp = ($cpParts -join ';') -replace '\\', '/'

    $argFile = Join-Path $logDir "$name.args"
    $logFile = Join-Path $logDir "$name.log"
    # Each argv element is built with explicit interpolation, because a PowerShell array
    # literal binds the comma operator tighter than '+' and silently splits quoted values.
    $outArg = '"{0}"' -f ($outDir -replace '\\', '/')
    $cpArg = '"{0}"' -f $cp
    # Lombok runs as the only annotation processor. It is required, not optional: upstream
    # declares hundreds of accessors with @Getter/@Setter, and compiling with -proc:none
    # turns every one of them into a "cannot find symbol" on a generated method.
    $lombokArg = '"{0}"' -f ((Join-Path $base 'libs\lombok-1.18.46.jar') -replace '\\', '/')
    # The Mixin annotation processor generates the SRG refmap. Without one, Mixin cannot resolve the
    # @Shadow and @Inject member names in a production client, and the first applied mixin fails
    # with "not located in the target class ... No refMap loaded". It is added only for the module
    # that declares mixins, because it also scans every class it is given.
    $processorPath = $lombokArg
    if ($name -eq 'main') {
        $mixinApArg = '"{0}"' -f ((Join-Path $base 'libs\mixin-processor.jar') -replace '\\', '/')
        $refmapOut = '"{0}"' -f ((Join-Path $outDir 'mixins.actinium-refmap.json') -replace '\\', '/')
        $srgArg = '"{0}"' -f ($srg -replace '\\', '/')
        $processorPath = "$lombokArg;$mixinApArg"
    }
    $lines = @('-nowarn', '-encoding', 'UTF-8', '-Xlint:none', '-Xmaxerrs', '100000',
               '-processorpath', $processorPath,
               # Explicit, not implicit. javac 21 and newer only discover annotation processors
               # when their discovery is requested; relying on the old implicit behaviour left
               # one module compiled without Lombok at all, which surfaced as a hundred
               # "cannot find symbol" errors on generated getters rather than as a build failure
               # about processing.
               '-proc:full',
               '-d', $outArg, '-cp', $cpArg)
    if ($name -eq 'main') {
        $lines += @("-AoutRefMapFile=$($refmapOut.Trim('""'))",
                    "-AreobfSrgFile=$($srgArg.Trim('""'))",
                    '-AmappingTypes=searge',
                    '-AdefaultObfuscationEnv=searge',
                    '-AMSG_NO_OBFDATA_FOR_SHADOW=warning',
                    '-AMSG_TARGET_ELEMENT_NOT_FOUND=warning',
                    # These mixins target methods whose 1.10.2 name differs from the 1.12.2 name the
                    # source was written against. The mapping is absent rather than wrong, so the
                    # processor reports it and the mixin still ships; a genuinely bad target shows
                    # up at apply time in the launch log, which is where it is caught.
                    '-AMSG_CANT_FIND_MAPPING=warning',
                    '-AMSG_UNABLE_TO_LOCATE_MAPPING=warning',
                    '-AMSG_UNMAPPED_TARGET=warning')
    }
    $lines += ($sources | ForEach-Object { '"{0}"' -f ($_ -replace '\\', '/') })
    Set-Content $argFile ($lines -join "`r`n") -Encoding ASCII

    Write-Host "== $name : compiling $($sources.Count) sources (release $Release)"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    # javac localises its diagnostics; forcing a C locale keeps 'file:line: error: ...'
    # parseable, which the error count and the summaries below both depend on.
    $env:JAVA_TOOL_OPTIONS = '-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8'
    # A stale output tree is the failure mode this guards against: javac keeps a class file whose
    # source changed but whose compile was reported successful by the previous run, and the build then
    # packages the old bytecode. That produced three separate "the fix did not take" investigations,
    # each resolved only by deleting the class by hand. Whenever any source in this stage is newer
    # than the newest class it produced, the output directory is cleared first, which makes javac do
    # the full pass that the change actually requires.
    $newestSource = ($sources | ForEach-Object { (Get-Item $_).LastWriteTime } | Measure-Object -Maximum).Maximum
    $newestClass = $null
    if (Test-Path $outDir) {
        $newestClass = (Get-ChildItem $outDir -Recurse -File -Filter '*.class' -ErrorAction SilentlyContinue |
            ForEach-Object { $_.LastWriteTime } | Measure-Object -Maximum).Maximum
    }
    if ($ClearStages -or ($newestClass -and $newestSource -gt $newestClass)) {
        Write-Host "   clearing $name output (sources are newer than the compiled classes)"
        Get-ChildItem $outDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
    }
    cmd /c "`"$javac`" --release $Release @`"$argFile`" > `"$logFile`" 2>&1"
    $code = $LASTEXITCODE
    $env:JAVA_TOOL_OPTIONS = ''
    $sw.Stop()
    $errCount = 0
    if (Test-Path $logFile) {
        $errCount = (Get-Content $logFile | Select-String -Pattern ': error: ').Count
    }
    Write-Host ("   exit={0} errors={1} time={2}s" -f $code, $errCount, [math]::Round($sw.Elapsed.TotalSeconds, 1))
    $summary += [pscustomobject]@{ Stage = $name; Sources = $sources.Count; Errors = $errCount; Exit = $code; Seconds = [math]::Round($sw.Elapsed.TotalSeconds, 1) }
    if ($code -ne 0) {
        Write-Host "   first errors:"
        Get-Content $logFile | Select-String -Pattern ': error: ' | Select-Object -First 25 | ForEach-Object { "   " + $_.Line }
        if (-not $KeepGoing) { break }
    }
    # The Mixin annotation processor emits no refmap entry for an @Overwrite method body, so such a
    # method has to be declared with its SRG name and remap = false, which is the form this tree
    # uses. Hand-written refmap entries do NOT work for @Overwrite: the entry is keyed by member
    # reference and the apply resolves through the target's own member lookup. This gate fails the
    # build when a new @Overwrite arrives in MCP form, instead of leaving it to fail at launch.
    if ($name -eq 'main' -and $code -eq 0) {
        # The annotation processor writes no refmap entry for a @Shadow member, so every such
        # member would fail at apply time one class per launch. Filling them here is what makes the
        # refmap complete rather than partly complete.
        & $python (Join-Path $base 'tools\complete_shadow_refmap.py') $base $srg
        if ($LASTEXITCODE -ne 0) { throw "shadow refmap completion failed with $LASTEXITCODE" }
        # Every one of these is the same defect: a member or method named with an MCP name that the
        # annotation processor does not remap, so a production client resolves it to nothing and the
        # mixin fails at apply time, one class per launch. Each converter is idempotent and --check
        # exits non-zero, which turns "a new mixin was added in MCP form" into a build error.
        $converters = @(
            @{ Tool = 'convert_overwrite_mixins.py';      Done = '@Overwrite methods are in SRG form' },
            @{ Tool = 'convert_shadow_mixins.py';         Done = '@Shadow members are in SRG form' },
            @{ Tool = 'convert_mixinextras_selectors.py'; Done = 'MixinExtras selectors are in SRG form' }
        )
        foreach ($converter in $converters) {
            & $python (Join-Path $base ("tools\" + $converter.Tool)) $base $srg --check
            if ($LASTEXITCODE -ne 0) { throw ("not all " + $converter.Done + "; run tools\" + $converter.Tool) }
        }

        # Injection selectors and handler signatures are checked against the real deobfuscated
        # classes. The form-only gates above passed while every one of these failed at runtime,
        # which is why both audits resolve names through javap against the real jar.
        $javap = 'D:\JAVA\zulu-jdk17.0.21.0.3\bin\javap.exe'
        $forgeBinAt = Join-Path $base 'build\forgeBin-at.jar'
        if ((Test-Path $javap) -and (Test-Path $forgeBinAt)) {
            & $python (Join-Path $base 'tools\audit_injection_selectors.py') $base $forgeBinAt $javap $srg
            if ($LASTEXITCODE -ne 0) { throw "injection selector audit failed" }
            & $python (Join-Path $base 'tools\audit_handler_signatures.py') $base $forgeBinAt $javap $srg
            if ($LASTEXITCODE -ne 0) { throw "handler signature audit failed" }
        }
    }
}

$summary | Format-Table -AutoSize

if ($NoReobf) { return }

$allErrors = ($summary | Measure-Object -Property Errors -Sum).Sum
if ($allErrors -gt 0) {
    Write-Host "Not packaging: $allErrors compile errors remain."
    return
}

# ---- package -------------------------------------------------------------
$modId = 'protactinium'
$version = 'alpha-0.0.1-1.10.2'
$stage = Join-Path $base 'build\stage'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force $stage | Out-Null
# modapistubs is deliberately absent: its declarations exist only to satisfy javac. Packaging them
# would make runtime presence probes (which look for the real mod's classes) report a mod as
# installed when it is not, and that mistake crashed the first launch through
# OutlandsExplicitTransformerRegistration.
$modules = @('mccompat', 'gtnhlib', 'glsm', 'celeritas', 'shader', 'main')
foreach ($name in $modules) {
    $d = Join-Path $outRoot $name
    if (Test-Path $d) {
        # robocopy merges directory trees without the wildcard/destination ambiguity that bites
        # Copy-Item when the source contains nested packages.
        & robocopy $d $stage /E /NFL /NDL /NJH /NJS /NP | Out-Null
    }
}
& robocopy (Join-Path $base 'src\main\resources') $stage /E /NFL /NDL /NJH /NJS /NP | Out-Null
# Language files must reach the jar or every options label renders as a raw key. They were silently
# missing once because a robocopy pass reported success while the destination kept an older tree, so
# the result is asserted rather than assumed.
$langSources = @(Get-ChildItem (Join-Path $base 'src\main\resources\assets') -Recurse -Filter '*.lang' -ErrorAction SilentlyContinue)
foreach ($langFile in $langSources) {
    $relative = $langFile.FullName.Substring((Join-Path $base 'src\main\resources').Length).TrimStart('\')
    $destination = Join-Path $stage $relative
    New-Item -ItemType Directory -Force (Split-Path $destination) | Out-Null
    Copy-Item $langFile.FullName $destination -Force
}
$stagedLang = @(Get-ChildItem (Join-Path $stage 'assets') -Recurse -Filter '*.lang' -ErrorAction SilentlyContinue)
Write-Host ("   staged {0} language file(s)" -f $stagedLang.Count)
if ($langSources.Count -gt 0 -and $stagedLang.Count -lt $langSources.Count) {
    throw "language files did not reach the package: $($stagedLang.Count) of $($langSources.Count)"
}
# A module's own resources have to be merged too. glsm registers its render backend through
# META-INF/services, and ServiceLoader finds nothing when only src/main/resources is copied, which
# makes BackendManager throw "No available render backend!" during GLStateManager's static
# initialiser, before any context exists.
$moduleResourceDirs = @(
    (Join-Path $base 'modules\glsm\src\main\resources'),
    (Join-Path $base 'modules\shader\src\main\resources'),
    (Join-Path $base 'modules\celeritas\src\main\resources')
)
foreach ($resourceDir in $moduleResourceDirs) {
    if (Test-Path $resourceDir) {
        & robocopy $resourceDir $stage /E /NFL /NDL /NJH /NJS /NP | Out-Null
    }
}

# fastutil 8 ships inside the jar because the renderer links against APIs that 1.10.2's bundled
# fastutil 7.0.12 lacks. Only the types actually referenced are staged: the whole library would add
# about 23 MB and 13000 classes for a few dozen reachable types.
# The jar does not exist yet at this point, so the scan reads the compiled module output.
$fastutilScanJar = Join-Path $base 'build\Protactinium-classes.jar'
if (Test-Path $fastutilScanJar) { Remove-Item $fastutilScanJar -Force }
& $jarExe --create --file $fastutilScanJar -C $stage .
$fastutilStage = Join-Path $base 'build\staged-fastutil'
if (Test-Path $fastutilStage) { Remove-Item $fastutilStage -Recurse -Force }
& $python (Join-Path $base "tools\stage_fastutil.py") `
    $fastutilScanJar `
    (Join-Path $base "libs\fastutil-8.5.18.jar") `
    $fastutilStage
if ($LASTEXITCODE -ne 0) { throw "staging fastutil failed with $LASTEXITCODE" }
# fastutil 8.5.18 predates the Java 25 verifier needs on some classes (half of them carry
# no StackMapTable), so the payload is re-emitted with computed frames exactly like the
# other embedded libraries before it is packed into the .bin entries.
& $python (Join-Path $base "tools\add_stackmaps.py") $fastutilStage 61 `
    (Join-Path $base "libs\asm-9.7.1.jar") `
    (Join-Path $base "libs\asm-tree-9.7.1.jar")
if ($LASTEXITCODE -ne 0) { throw "computing stack maps for fastutil failed with $LASTEXITCODE" }
# The staged fastutil 8 classes become the swap payload FastutilUpgraderTransformer reads
# from /META-INF/actinium_libs/fastutil8/<name>.bin. They deliberately do NOT sit at the jar
# top level: Forge puts fastutil 7 ahead of the mod jar on the launch class path, so a
# top-level copy never loads, and a half-swapped mix of 7 and 8 dies with
# NoSuchMethodError on exactly the interface the shader code calls.
$fastutilPayload = Join-Path $base "build\fastutil-payload"
if (Test-Path $fastutilPayload) { Remove-Item $fastutilPayload -Recurse -Force }
$payloadRoot = Join-Path $fastutilPayload ("META-INF\actinium_libs\fastutil8")
New-Item -ItemType Directory -Force $payloadRoot | Out-Null
Get-ChildItem $fastutilStage -Recurse -Filter "*.class" | ForEach-Object {
    $relative = $_.FullName.Substring($fastutilStage.Length + 1) -replace "[.]class$", ".bin"
    $destination = Join-Path $payloadRoot $relative
    New-Item -ItemType Directory -Force (Split-Path $destination) | Out-Null
    Copy-Item $_.FullName $destination -Force
}
& robocopy $fastutilPayload $stage /E /NFL /NDL /NJH /NJS /NP | Out-Null

# Libraries the coremod touches before any mod is on the class path have to live inside the jar
# body: FML instantiates the coremod while building the launch class path, and a jar that merely
# declares these as dependencies is not on it yet. Upstream calls this the shadow bundle; the
# effect here is the same.
$embeddedLibs = @(
    'antlr4-runtime-4.13.2.jar',
    'glsl-transformation-lib.jar',
    'jcpp-1.4.14.jar',
    # The Iris compatibility layer references org.joml from Iris.onEarlyInitialize, which the
    # GameSettings mixin calls during game start. 1.10.2 ships no JOML, and the ported renderer
    # must not lose a feature for that reason, so the library is carried inside the jar the same
    # way the shader compiler's dependencies are.
    'joml-1.10.5.jar'
)
foreach ($embedded in $embeddedLibs) {
    $jarPath = Join-Path $base "libs\$embedded"
    if (-not (Test-Path $jarPath)) { throw "missing embedded library: $jarPath" }
    $expanded = Join-Path $base "build\embedded\$embedded"
    if (Test-Path $expanded) { Remove-Item $expanded -Recurse -Force }
    New-Item -ItemType Directory -Force $expanded | Out-Null
    & $jarExe --extract --file $jarPath --dir $expanded
    # Every embedded library is re-emitted through ASM so it carries stack map frames.
    #
    # This is not optional polish. These libraries are compiled to class versions that predate
    # StackMapTable (JOML 46, jcpp 49, antlr and the shader transformer 52), and the Java 25 verifier
    # this instance runs on requires frames for anything it loads on a branch. Without this pass the
    # failure is
    #
    #     java.lang.VerifyError: Expecting a stackmap frame at branch target 40
    #     Location: org/anarres/cpp/PreprocessorCommand.forText(Ljava/lang/String;)
    #
    # which is the options screen dying on the embedded C preprocessor inside jcpp. Applying it to
    # every library rather than to JOML alone is deliberate: the same defect was found in three of the
    # four, and fixing them one crash at a time is how the first one was missed.
    & $python (Join-Path $base 'tools\add_stackmaps.py') $expanded 61 `
        (Join-Path $base 'libs\asm-9.7.1.jar') `
        (Join-Path $base 'libs\asm-tree-9.7.1.jar')
    if ($LASTEXITCODE -ne 0) { throw "computing stack maps for $embedded failed with $LASTEXITCODE" }
    & robocopy $expanded $stage /E /NFL /NDL /NJH /NJS /NP /XF "MANIFEST.MF" "*.SF" "*.RSA" "*.DSA" "module-info.class" | Out-Null
}

# jcpp's Preprocessor static-initialises an slf4j Logger, and FML excludes org.slf4j from the
# launch class loader while nothing on this instance provides it, so the first shaderpack load
# died with NoClassDefFoundError: org/slf4j/LoggerFactory. The API is embedded under a private
# package and every reference in the staged classes is rewritten to it.
& $python (Join-Path $base "tools\relocate_slf4j.py") `
    (Join-Path $base "libs\slf4j-api-2.0.16.jar") `
    $stage `
    (Join-Path $base "libs\asm-9.7.1.jar") `
    (Join-Path $base "libs\asm-commons-9.7.1.jar") `
    (Join-Path $base "libs\asm-tree-9.7.1.jar")
if ($LASTEXITCODE -ne 0) { throw "slf4j relocation failed with $LASTEXITCODE" }

# FML only reads an access transformer from META-INF, and only the file named by the FMLAT
# manifest entry, so the AT file is moved there rather than left at the jar root where it would be
# packaged but never applied.
#
# The AT must be rewritten to SRG member names BEFORE it is packaged: the production client is
# SRG-named, FML applies the entries verbatim, and an MCP-named entry silently matches nothing. The
# first hotbar item render then dies with IllegalAccessError on a field the AT was supposed to
# widen. The compile-time jar keeps MCP names, so the rewrite happens here, in packaging.
$atInStage = Join-Path $stage 'actinium_at.cfg'
if (Test-Path $atInStage) {
    & $python (Join-Path $base 'tools\rewrite_at.py') $atInStage $srg $atInStage
    if ($LASTEXITCODE -ne 0) { throw "AT SRG rewrite failed with $LASTEXITCODE" }
    New-Item -ItemType Directory -Force (Join-Path $stage 'META-INF') | Out-Null
    Move-Item $atInStage (Join-Path $stage 'META-INF\protactinium_at.cfg') -Force
}

$manifest = @"
Manifest-Version: 1.0
FMLCorePlugin: com.dhj.actinium.mixins.MixinEarly
FMLCorePluginContainsFMLMod: true
ForceLoadAsMod: true
FMLAT: protactinium_at.cfg
Implementation-Title: Protactinium
Implementation-Version: $version
Multi-Release: true
"@
$manifestPath = Join-Path $base 'build\MANIFEST.MF'
$manifest -replace "`r?`n", "`r`n" | Set-Content -Encoding ASCII -NoNewline $manifestPath

$preJar = Join-Path $base 'build\Protactinium-pre.jar'
if (Test-Path $preJar) { Remove-Item $preJar -Force }
& $jarExe --create --file $preJar --manifest $manifestPath -C $stage .
if ($LASTEXITCODE -ne 0) { throw "jar failed with $LASTEXITCODE" }

# ForgeAutoRenamingTool 0.1.22 embeds an ASM that rejects class files newer than it knows, so the
# version field is lowered to Java 17 before remapping. This does not change any instruction: the
# constructs this tree uses (records, pattern switches, `List.getFirst`) are compiler desugaring,
# and the runtime this mod targets is Java 25, which loads either version. See the tool's header.
$preJarForFart = Join-Path $base 'build\Protactinium-pre-r17.jar'
& $python (Join-Path $base 'tools\lower_class_version.py') $preJar $preJarForFart 61
if ($LASTEXITCODE -ne 0) { throw "lowering the class file version failed with $LASTEXITCODE" }

$jarPath = Join-Path $base "build\libs\Protactinium-$version.jar"
New-Item -ItemType Directory -Force (Split-Path $jarPath) | Out-Null

Write-Host "Reobfuscating to SRG names ..."
# Every jar the ported classes reference must be a library here, except the two groups that cannot
# be: OSHI (compiled for a newer bytecode level) and the annotation/tooling jars. Those references
# resolve to warnings rather than failures, and none of them is a Minecraft class that needs
# remapping.
$fartLibs = $libs | Where-Object { (Split-Path $_ -Leaf) -notmatch '^(oshi-|lombok-|annotations-)' }
$fartArgs = @('-jar', $fart, '--input', $preJarForFart, '--output', $jarPath, '--map', $srg,
              '--ann-fix', '--ids-fix', '--record-fix', '--threads', '2')
foreach ($lib in ($fartLibs + $forgeJar)) { $fartArgs += @('-e', $lib) }
& $javaExe @fartArgs 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "reobfuscation failed with $LASTEXITCODE" }
Remove-Item $preJar -Force
Remove-Item $preJarForFart -Force
# ForgeAutoRenamingTool writes its own manifest over the one given to `jar`, so the attributes the
# mod needs at runtime are put back afterwards.
#
# Deliberately NOT Lwjgl3ify-Aware. That attribute exempts the whole jar from lwjgl3ify's class
# transforms, which sounds right for a mod that talks to LWJGL 3 directly, but it also disables the
# rewrites this mod depends on: GLContext.getCapabilities() then reaches the compatibility shim's
# UnsupportedOperationException, and every org.lwjgl.opengl call in the render pipeline goes to the
# unloaded LWJGL 2 shape instead. The exemption has to be per class, with @Lwjgl3Aware, on the
# classes that must keep their LWJGL 3 types.
& $python (Join-Path $base 'tools\set_manifest.py') $jarPath (Join-Path $base 'build\MANIFEST.MF')
if ($LASTEXITCODE -ne 0) { throw "restoring the jar manifest failed with $LASTEXITCODE" }
Write-Host ("Built {0} ({1} MB)" -f $jarPath, [math]::Round((Get-Item $jarPath).Length / 1MB, 2))
