# Requires PowerShell 7 and a JDK 25.
[CmdletBinding()]
param(
    [string]$Modrinth = (Join-Path $env:APPDATA 'ModrinthApp'),
    [string]$Profile = 'Mod Pack Fabric 26.3  (client) (Mart server) 1.0.0',
    [string]$Jdk = $env:JAVA_HOME,
    [string]$FabricApiVersion,
    [string]$ModMenuJar,
    [string]$Output = (Join-Path $PSScriptRoot 'build')
)

$ErrorActionPreference = 'Stop'
$javac = if ($Jdk) {
    Join-Path $Jdk 'bin/javac.exe'
} else {
    $command = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($command) { $command.Source }
}
if (!$javac -or !(Test-Path -LiteralPath $javac -PathType Leaf)) {
    throw 'JDK 25: pass -Jdk with the path to the JDK, or set JAVA_HOME.'
}

$meta = Join-Path $Modrinth 'meta'
$modDir = Join-Path $Modrinth "profiles/$Profile/mods"
if (!$ModMenuJar) {
    $modMenuFiles = @(Get-ChildItem -LiteralPath $modDir -Filter 'modmenu-*.jar' -File)
    if ($modMenuFiles.Count -ne 1) { throw 'Pass -ModMenuJar with a Mod Menu 26.3 JAR for compiling the optional integration.' }
    $ModMenuJar = $modMenuFiles[0].FullName
}
$ModMenuJar = (Resolve-Path -LiteralPath $ModMenuJar).Path
$version = '26.3-0.19.5'
$versionDir = Join-Path $meta "versions/$version"
$manifest = Get-Content -Raw -LiteralPath (Join-Path $versionDir "$version.json") | ConvertFrom-Json
$libraries = foreach ($library in $manifest.libraries) {
    if (!$library.downloads.artifact.path) { continue }
    $path = Join-Path $meta "libraries/$($library.downloads.artifact.path)"
    if ((Test-Path -LiteralPath $path) -and $path -notmatch '-natives-') { $path }
}
$loader = Join-Path $meta 'libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar'
if (!(Test-Path -LiteralPath $loader)) { throw "Missing Fabric Loader: $loader" }
$mixin = Join-Path $meta 'libraries/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar'
if (!(Test-Path -LiteralPath $mixin)) { throw "Missing Fabric Mixin: $mixin" }
$fabricApi = if ($FabricApiVersion) {
    Get-Item -LiteralPath (Join-Path $modDir "fabric-api-$FabricApiVersion.jar")
} else {
    $installed = @(Get-ChildItem -LiteralPath $modDir -Filter 'fabric-api-*+26.3.jar' -File)
    if ($installed.Count -ne 1) { throw 'Pass -FabricApiVersion when the profile does not have exactly one Fabric API 26.3 JAR.' }
    $installed[0]
}
$mods = @(
    $fabricApi.Name
    'litematica-fabric-26.3-0.29.1.jar'
    'malilib-fabric-26.3-0.30.2.jar'
    'toms_storage_fabric-26.3-2.12.1.jar'
) | ForEach-Object {
    $path = Join-Path $modDir $_
    if (!(Test-Path -LiteralPath $path)) { throw "Missing mod: $path" }
    $path
}

$outputDir = (New-Item -ItemType Directory -Path $Output -Force).FullName
$staging = (New-Item -ItemType Directory -Path (Join-Path $outputDir ([guid]::NewGuid().ToString()))).FullName
try {
    $classes = (New-Item -ItemType Directory -Path (Join-Path $staging 'classes')).FullName
    $nested = foreach ($dependency in @($mods[0], $loader)) {
        $zip = [IO.Compression.ZipFile]::OpenRead($dependency)
        try {
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName -match '^META-INF/jars/(fabric-(api-base|screen-api-v1|networking-api-v1|lifecycle-events-v1|key-mapping-api-v1)|mixinextras-fabric)-.*\.jar$') {
                    $path = Join-Path $staging $entry.Name
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $path)
                    $path
                }
            }
        } finally {
            $zip.Dispose()
        }
    }

    $classpath = (@((Join-Path $versionDir "$version.jar"), $loader, $mixin, $ModMenuJar) + $libraries + $mods + $nested) -join ';'
    $sources = (Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/java') -Recurse -Filter '*.java').FullName
    $argsFile = Join-Path $staging 'javac.args'
    $arguments = @('-proc:none', '--release', '25', '-encoding', 'UTF-8', '-classpath', $classpath, '-d', $classes) + $sources
    $arguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' } | Set-Content -Encoding utf8NoBOM -LiteralPath $argsFile
    & $javac "@$argsFile"
    if ($LASTEXITCODE -ne 0) { throw "javac exited with code $LASTEXITCODE" }

    Copy-Item -Path (Join-Path $PSScriptRoot 'src/main/resources/*') -Destination $classes -Recurse
    $archive = Join-Path $staging 'tomsmatic-0.3.3.jar'
    [IO.Compression.ZipFile]::CreateFromDirectory($classes, $archive)
    $result = Join-Path $outputDir 'tomsmatic-0.3.3.jar'
    [IO.File]::Move($archive, $result, $true)
    Write-Output $result
} finally {
    $resolvedOutput = [IO.Path]::GetFullPath($outputDir)
    $resolvedStaging = (Resolve-Path -LiteralPath $staging).Path
    if ([IO.Path]::GetDirectoryName($resolvedStaging) -eq $resolvedOutput) {
        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
    }
}
