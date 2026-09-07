$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$targets = @(
    @{ Name = 'Forge 1.20.1'; File = 'versions/1.20.1-forge/build/libs/polyglottoolchainprobe-1.20.1-forge-47.4.23-0.1.0-production.jar'; Major = 61; Metadata = 'META-INF/mods.toml'; Nested = 'META-INF/jarjar/'; Mapping = 'refmap' },
    @{ Name = 'Fabric 1.20.1'; File = 'versions/1.20.1-fabric/build/libs/polyglottoolchainprobe-1.20.1-fabric-0.19.2-0.1.0.jar'; Major = 61; Metadata = 'fabric.mod.json'; Nested = 'META-INF/jars/'; Mapping = 'method_1551' },
    @{ Name = 'NeoForge 1.21.1'; File = 'versions/1.21.1-neoforge/build/libs/polyglottoolchainprobe-1.21.1-neoforge-21.1.249-0.1.0.jar'; Major = 65; Metadata = 'META-INF/neoforge.mods.toml'; Nested = 'META-INF/jarjar/'; Mapping = 'getInstance' },
    @{ Name = 'Fabric 1.21.1'; File = 'versions/1.21.1-fabric/build/libs/polyglottoolchainprobe-1.21.1-fabric-0.19.2-0.1.0.jar'; Major = 65; Metadata = 'fabric.mod.json'; Nested = 'META-INF/jars/'; Mapping = 'method_1551' },
    @{ Name = 'NeoForge 26.1.2'; File = 'versions/26.1.2-neoforge/build/libs/polyglottoolchainprobe-26.1.2-neoforge-26.1.2.101-0.1.0.jar'; Major = 69; Metadata = 'META-INF/neoforge.mods.toml'; Nested = 'META-INF/jarjar/'; Mapping = 'getInstance' },
    @{ Name = 'Fabric 26.1.2'; File = 'versions/26.1.2-fabric/build/libs/polyglottoolchainprobe-26.1.2-fabric-0.19.2-0.1.0.jar'; Major = 69; Metadata = 'fabric.mod.json'; Nested = 'META-INF/jars/'; Mapping = 'getInstance' }
)

function Read-ZipEntryText($zip, [string] $name) {
    $entry = $zip.GetEntry($name)
    if ($null -eq $entry) { throw "Missing entry: $name" }
    $reader = [System.IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

foreach ($target in $targets) {
    $path = Join-Path $PSScriptRoot $target.File
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing artifact: $path" }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    try {
        $names = @($zip.Entries.FullName)
        foreach ($required in @(
            $target.Metadata,
            'polyglottoolchainprobe.mixins.json',
            'com/starskyxiii/polyglottoolchainprobe/ProbePolicy.class',
            'com/starskyxiii/polyglottoolchainprobe/ToolchainProbe.class',
            'com/starskyxiii/polyglottoolchainprobe/mixin/MinecraftConstructorMixin.class'
        )) {
            if ($required -notin $names) { throw "$($target.Name) missing $required" }
        }

        if ($target.Name -eq 'Forge 1.20.1' -and 'pack.mcmeta' -notin $names) {
            throw 'Forge 1.20.1 missing pack.mcmeta'
        }

        foreach ($library in @('opencc4j-1.14.0.jar', 'heaven-0.13.0.jar', 'nlp-common-0.0.5.jar')) {
            if (("$($target.Nested)$library") -notin $names) { throw "$($target.Name) missing $library" }
        }

        $metadata = Read-ZipEntryText $zip $target.Metadata
        $mixinConfig = Read-ZipEntryText $zip 'polyglottoolchainprobe.mixins.json'
        if ($metadata -notmatch 'polyglottoolchainprobe') { throw "$($target.Name) has invalid metadata" }
        if ($metadata -match '\$\{') { throw "$($target.Name) has an unexpanded metadata value" }
        if ($mixinConfig -notmatch '"minVersion"\s*:\s*"0\.8"') { throw "$($target.Name) lacks Mixin minVersion" }

        $entrypointBytes = Read-ZipEntryText $zip 'com/starskyxiii/polyglottoolchainprobe/ToolchainProbe.class'
        $mixinBytes = Read-ZipEntryText $zip 'com/starskyxiii/polyglottoolchainprobe/mixin/MinecraftConstructorMixin.class'
        if ($entrypointBytes -notmatch 'org/slf4j/Logger') { throw "$($target.Name) entrypoint lacks SLF4J logging" }
        if ($mixinBytes -notmatch 'org/slf4j/Logger') { throw "$($target.Name) Mixin lacks SLF4J logging" }
        if ($entrypointBytes -match 'java/lang/System' -or $mixinBytes -match 'java/lang/System') {
            throw "$($target.Name) marker still uses System.out"
        }

        $classEntry = $zip.GetEntry('com/starskyxiii/polyglottoolchainprobe/ProbePolicy.class')
        $stream = $classEntry.Open()
        try {
            $header = [byte[]]::new(8)
            if ($stream.Read($header, 0, 8) -ne 8) { throw "$($target.Name) has a truncated class" }
            $major = ($header[6] * 256) + $header[7]
            if ($major -ne $target.Major) { throw "$($target.Name) class major is $major, expected $($target.Major)" }
        } finally { $stream.Dispose() }

        if ($target.Mapping -eq 'refmap') {
            $refmap = Read-ZipEntryText $zip 'polyglottoolchainprobe.refmap.json'
            if ($refmap -notmatch 'm_91087_') { throw "$($target.Name) refmap lacks mapped getInstance" }
            if ($mixinConfig -notmatch 'polyglottoolchainprobe.refmap.json') { throw "$($target.Name) mixin config lacks refmap" }
        } else {
            if ($mixinBytes -notmatch $target.Mapping) { throw "$($target.Name) lacks $($target.Mapping) mapping evidence" }
            if ($mixinConfig -match 'refmap') { throw "$($target.Name) unexpectedly declares a refmap" }
        }
    } finally {
        $zip.Dispose()
    }

    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
    Write-Output "$($target.Name)|$path|SHA256=$hash|class=$($target.Major)"
}
