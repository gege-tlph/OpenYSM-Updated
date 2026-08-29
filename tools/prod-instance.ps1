<#
  prod-instance.ps1 - Build (and optionally launch) a REAL production Fabric instance.

  Why this exists
  ---------------
  `play.ps1` runs the Loom development client. That is the right place to test our
  own code, but the wrong place to test third-party mods: dev runs against *named*
  mappings with Loader's dev remapper in the loop, so production mixins that ship
  without a refmap can fail their injection checks even though the target method is
  present. On 2026-08-29 that produced a false "Iris and Sodium are incompatible"
  finding - both mods were fine; the dev runtime was not a valid witness.

  Third-party co-installation acceptance therefore runs here: the vanilla client jar,
  intermediary mappings at runtime, real Fabric Loader, no Loom, no dev remapper -
  exactly what a player has.

  Reuses what Loom already downloaded (client jar, 1.1 GB of assets) instead of
  re-fetching them; only libraries and the loader are pulled from the network.

  Usage:
    .\tools\prod-instance.ps1                       # build/refresh the instance
    .\tools\prod-instance.ps1 -Mods 'fabric\build\libs\openysm-fabric-2.7.0.0.jar','C:\mods\iris.jar'
    .\tools\prod-instance.ps1 -Launch -Server localhost:25565
#>
[CmdletBinding()]
param(
    [string]$Root          = 'fabric\prod-instance',
    [string]$McVersion     = '26.1.2',
    [string]$LoaderVersion = '',
    [switch]$Launch,
    [string]$Name          = 'Prod',
    [string]$Server        = '',
    [int]$Width            = 1280,
    [int]$Height           = 720,
    [string]$WaitFor       = '',
    [int]$TimeoutSec       = 420,
    [string]$LogDir        = 'tools-logs',
    [string]$LogName       = 'prod',
    # Mod jars to install into the instance. Third-party co-installation acceptance is
    # this script's whole purpose, so putting them there must not be a manual step.
    [string[]]$Mods        = @(),
    [switch]$ClearMods
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\lib-env.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not [System.IO.Path]::IsPathRooted($Root))   { $Root   = Join-Path $repoRoot $Root }
if (-not [System.IO.Path]::IsPathRooted($LogDir)) { $LogDir = Join-Path $repoRoot $LogDir }
New-Item -ItemType Directory -Path $Root, $LogDir -Force | Out-Null

$loomCache = Join-Path $env:USERPROFILE ".gradle\caches\fabric-loom"
$libDir    = Join-Path $Root 'libraries'
New-Item -ItemType Directory -Path $libDir -Force | Out-Null

function Get-Json([string]$Url) {
    return Invoke-RestMethod -Uri $Url -UseBasicParsing -TimeoutSec 60
}

function Get-MavenPath([string]$Coord) {
    # group:artifact:version[:classifier] -> group/as/dirs/artifact/version/artifact-version[-classifier].jar
    $parts = $Coord.Split(':')
    $group = $parts[0].Replace('.', '/')
    $artifact = $parts[1]
    $version = $parts[2]
    $file = "$artifact-$version"
    if ($parts.Count -ge 4) { $file = "$file-$($parts[3])" }
    return "$group/$artifact/$version/$file.jar"
}

function Test-LibraryRules($Library) {
    # Mojang rule format: ordered allow/disallow, last match wins. No rules = allow.
    if (-not $Library.rules) { return $true }
    $allowed = $false
    foreach ($rule in $Library.rules) {
        $applies = $true
        if ($rule.os -and $rule.os.name) {
            if ($rule.os.name -ne 'windows') { $applies = $false }
        }
        if ($rule.os -and $rule.os.arch) {
            if ($rule.os.arch -ne 'x86_64') { $applies = $false }
        }
        if ($applies) { $allowed = ($rule.action -eq 'allow') }
    }
    return $allowed
}

# Every jar here is a pinned, hash-named release artifact, so a copy found in another
# local cache is the same file. Prism mirrors Mojang's exact library layout and Gradle
# keeps artifacts under a hash directory - check both before touching the network.
$script:PrismLibs   = Join-Path $env:APPDATA 'PrismLauncher\libraries'
$script:GradleFiles = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'

function Find-LocalCopy([string]$RelativePath) {
    $rel = $RelativePath -replace '/', '\'
    $prism = Join-Path $script:PrismLibs $rel
    if (Test-Path $prism) { return $prism }
    if (Test-Path $script:GradleFiles) {
        $leaf = Split-Path -Leaf $rel
        $hit = Get-ChildItem $script:GradleFiles -Filter $leaf -Recurse -File -ErrorAction SilentlyContinue |
               Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

function Test-Sha1([string]$Path, [string]$Expected) {
    if ([string]::IsNullOrWhiteSpace($Expected)) { return $true }   # nothing to check against
    return (Get-FileHash -Path $Path -Algorithm SHA1).Hash -ieq $Expected
}

function Save-File([string]$Url, [string]$Destination, [string]$RelativePath = '', [string]$Sha1 = '') {
    if (Test-Path $Destination) { return $false }
    $dir = Split-Path -Parent $Destination
    New-Item -ItemType Directory -Path $dir -Force | Out-Null

    # A cache hit is matched by leaf filename, and distinct coordinates can share one
    # (annotations-*.jar, asm-*.jar). This instance exists to overrule a dev-run finding
    # about mod compatibility, so a silently wrong jar would make the witness unsound in
    # exactly the direction it is trusted. Verify against Mojang's sha1 before accepting.
    if ($RelativePath -ne '') {
        $local = Find-LocalCopy $RelativePath
        if ($local) {
            if (Test-Sha1 $local $Sha1) { Copy-Item $local $Destination -Force; return $true }
            Write-Host "[prod] cache copy rejected (sha1 mismatch): $local"
        }
    }

    # Invoke-WebRequest renders a progress bar per chunk in PS 5.1, which dominates
    # the transfer time on multi-MB jars and causes spurious timeouts.
    $previousProgress = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'
    $tmp = "$Destination.part"
    try {
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            try {
                Invoke-WebRequest -Uri $Url -OutFile $tmp -UseBasicParsing -TimeoutSec 300
                if (-not (Test-Sha1 $tmp $Sha1)) {
                    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
                    throw "sha1 mismatch for $Url"
                }
                Move-Item $tmp $Destination -Force
                return $true
            } catch {
                if (Test-Path $tmp) { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
                if ($attempt -eq 3) { throw "download failed after 3 attempts: $Url`n$($_.Exception.Message)" }
                Start-Sleep -Seconds ($attempt * 2)
            }
        }
    } finally {
        $ProgressPreference = $previousProgress
    }
}

# --- 1. vanilla version manifest ------------------------------------------------
$versionJsonPath = Join-Path $loomCache "$McVersion\mojang_minecraft_info.json"
if (Test-Path $versionJsonPath) {
    Write-Host "[prod] vanilla manifest from Loom cache: $versionJsonPath"
    $vanilla = Get-Content $versionJsonPath -Raw | ConvertFrom-Json
} else {
    Write-Host "[prod] vanilla manifest not cached; querying Mojang"
    $manifest = Get-Json 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
    $entry = $manifest.versions | Where-Object { $_.id -eq $McVersion } | Select-Object -First 1
    if (-not $entry) { Write-Error "Minecraft $McVersion not in the Mojang manifest"; exit 2 }
    $vanilla = Get-Json $entry.url
}

# --- 2. client jar --------------------------------------------------------------
$clientJar = Join-Path $Root "client-$McVersion.jar"
$loomClient = Join-Path $loomCache "$McVersion\minecraft-client.jar"
if (-not (Test-Path $clientJar)) {
    if (Test-Path $loomClient) {
        Write-Host "[prod] client jar from Loom cache"
        Copy-Item $loomClient $clientJar -Force
    } else {
        Write-Host "[prod] downloading client jar"
        [void](Save-File $vanilla.downloads.client.url $clientJar)
    }
}

# --- 3. vanilla libraries -------------------------------------------------------
$classpath = New-Object System.Collections.Generic.List[string]
$fetched = 0
foreach ($lib in $vanilla.libraries) {
    if (-not (Test-LibraryRules $lib)) { continue }
    if (-not $lib.downloads -or -not $lib.downloads.artifact) { continue }
    $dest = Join-Path $libDir ($lib.downloads.artifact.path -replace '/', '\')
    if (Save-File $lib.downloads.artifact.url $dest $lib.downloads.artifact.path $lib.downloads.artifact.sha1) { $fetched++ }
    $classpath.Add($dest)
}
Write-Host "[prod] vanilla libraries: $($classpath.Count) on classpath, $fetched newly downloaded"

# --- 4. fabric loader -----------------------------------------------------------
if ([string]::IsNullOrWhiteSpace($LoaderVersion)) {
    $builds = Get-Json "https://meta.fabricmc.net/v2/versions/loader/$McVersion"
    $stable = $builds | Where-Object { $_.loader.stable } | Select-Object -First 1
    if ($stable) { $LoaderVersion = $stable.loader.version }
    else { $LoaderVersion = $builds[0].loader.version }
}
Write-Host "[prod] fabric loader $LoaderVersion"
$profile = Get-Json "https://meta.fabricmc.net/v2/versions/loader/$McVersion/$LoaderVersion/profile/json"

$loaderFetched = 0
foreach ($lib in $profile.libraries) {
    $rel = Get-MavenPath $lib.name
    $dest = Join-Path $libDir ($rel -replace '/', '\')
    $base = $lib.url
    if ([string]::IsNullOrWhiteSpace($base)) { $base = 'https://maven.fabricmc.net/' }
    if (-not $base.EndsWith('/')) { $base = "$base/" }
    if (Save-File "$base$rel" $dest $rel) { $loaderFetched++ }
    $classpath.Add($dest)
}
Write-Host "[prod] loader libraries: $($profile.libraries.Count) ($loaderFetched newly downloaded)"

$classpath.Add($clientJar)

# --- 5. assets ------------------------------------------------------------------
# Loom keeps assets in the vanilla layout but prefixes the index file with the MC
# version. Point the game at Loom's objects/ via a junction so we do not copy 1.1 GB.
$assetIndexId = $vanilla.assetIndex.id
$assetsRoot   = Join-Path $Root 'assets'
$loomAssets   = Join-Path $loomCache 'assets'
New-Item -ItemType Directory -Path (Join-Path $assetsRoot 'indexes') -Force | Out-Null

$srcIndex = Join-Path $loomAssets "indexes\$McVersion-$assetIndexId.json"
if (-not (Test-Path $srcIndex)) { $srcIndex = Join-Path $loomAssets "indexes\$assetIndexId.json" }
$dstIndex = Join-Path $assetsRoot "indexes\$assetIndexId.json"
if (Test-Path $srcIndex) {
    Copy-Item $srcIndex $dstIndex -Force
} else {
    [void](Save-File $vanilla.assetIndex.url $dstIndex)
}

$objectsLink = Join-Path $assetsRoot 'objects'
$loomObjects = Join-Path $loomAssets 'objects'
if (-not (Test-Path $objectsLink)) {
    if (Test-Path $loomObjects) {
        cmd /c mklink /J "$objectsLink" "$loomObjects" | Out-Null
        Write-Host "[prod] assets/objects junction -> Loom cache"
    } else {
        Write-Error "[prod] no asset objects in the Loom cache; run a dev client once first"
        exit 3
    }
}

# --- 6. game directory ----------------------------------------------------------
$gameDir = Join-Path $Root 'game'
New-Item -ItemType Directory -Path (Join-Path $gameDir 'mods') -Force | Out-Null
& "$PSScriptRoot\prepare-client-dir.ps1" -GameDir $gameDir | Out-Null

# --- 6b. mods ------------------------------------------------------------------
$modsDir = Join-Path $gameDir 'mods'
if ($ClearMods) {
    Get-ChildItem "$modsDir\*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Write-Host "[prod] mods dir cleared"
}
foreach ($mod in $Mods) {
    $modPath = $mod
    if (-not [System.IO.Path]::IsPathRooted($modPath)) { $modPath = Join-Path $repoRoot $modPath }
    if (-not (Test-Path $modPath)) { Write-Error "[prod] mod jar not found: $modPath"; exit 4 }
    Copy-Item $modPath $modsDir -Force
    Write-Host ("[prod] mod installed: {0}" -f (Split-Path -Leaf $modPath))
}
$installed = @(Get-ChildItem "$modsDir\*.jar" -ErrorAction SilentlyContinue)
Write-Host ("[prod] mods present: {0}" -f $(if ($installed.Count) { ($installed | ForEach-Object { $_.Name }) -join ', ' } else { '(none)' }))

$cpFile = Join-Path $Root 'classpath.txt'
$classpath -join [IO.Path]::PathSeparator | Set-Content $cpFile -Encoding utf8
Write-Host "[prod] instance ready: $Root"
Write-Host "[prod]   game dir : $gameDir"
Write-Host "[prod]   mods dir : $(Join-Path $gameDir 'mods')"
Write-Host "[prod]   classpath: $($classpath.Count) entries -> $cpFile"

if (-not $Launch) { return }

# --- 7. launch ------------------------------------------------------------------
$jdk = Use-Jdk25
Write-Host "[prod] JAVA_HOME=$jdk"
$java = Join-Path $jdk 'bin\java.exe'

$gameArgs = @(
    '--username', $Name,
    '--version', $profile.id,
    '--gameDir', $gameDir,
    '--assetsDir', $assetsRoot,
    '--assetIndex', $assetIndexId,
    '--uuid', '00000000000040008000000000000000',
    '--accessToken', '0',
    '--userType', 'legacy',
    '--versionType', 'release',
    '--width', "$Width",
    '--height', "$Height"
)
if ($Server -ne '') { $gameArgs += @('--quickPlayMultiplayer', $Server) }

$jvmArgs = @(
    '-Xmx4G',
    "-Dfabric.gameVersion=$McVersion",
    '-Djava.net.preferIPv4Stack=true',
    '-cp', ($classpath -join [IO.Path]::PathSeparator),
    $profile.mainClass
)

$log = Join-Path $LogDir "$LogName.log"
Write-Host "[prod] launching production client -> $log"
$proc = Start-Process -FilePath $java -ArgumentList ($jvmArgs + $gameArgs) `
                      -WorkingDirectory $gameDir -RedirectStandardOutput $log `
                      -RedirectStandardError "$log.err" -PassThru
Write-Host "[prod] pid=$($proc.Id) log=$log"

if ($WaitFor -ne '') {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $re = [regex]$WaitFor
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) { Write-Host "[prod] process exited early (code $($proc.ExitCode))"; break }
        if (Test-Path $log) {
            $text = Get-Content $log -Raw -ErrorAction SilentlyContinue
            if ($text -and $re.IsMatch($text)) { Write-Host "[prod] READY: matched /$WaitFor/"; break }
        }
        Start-Sleep -Milliseconds 800
    }
    if ((Get-Date) -ge $deadline) { Write-Host "[prod] TIMEOUT: /$WaitFor/ not seen in ${TimeoutSec}s" }
}
Write-Output $proc.Id
