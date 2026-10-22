param([Parameter(Mandatory=$true)][string]$RunDirectory)
$ErrorActionPreference = 'Stop'
$resolvedRun = (Resolve-Path -LiteralPath $RunDirectory).Path
$ownership = Get-Content -LiteralPath (Join-Path $resolvedRun 'process.json') -Raw | ConvertFrom-Json
$sourceRoot = Join-Path $ownership.gameDirectory 'config'
$destination = Join-Path $resolvedRun 'external-config-events.jsonl'
if (Test-Path -LiteralPath $destination) { throw 'Observation output already exists' }
$watcher = [System.IO.FileSystemWatcher]::new($sourceRoot)
$watcher.IncludeSubdirectories = $true
$watcher.NotifyFilter = [System.IO.NotifyFilters]::FileName -bor [System.IO.NotifyFilters]::DirectoryName -bor [System.IO.NotifyFilters]::LastWrite -bor [System.IO.NotifyFilters]::Size
$writer = [System.IO.StreamWriter]::new($destination, $false, [System.Text.UTF8Encoding]::new($false))
$writer.AutoFlush = $true
$identifiers = @()
try {
    foreach ($kind in @('Changed','Created','Deleted','Renamed','Error')) {
        $identifier = 'pt-observe-' + $ownership.nonce + '-' + $kind
        Register-ObjectEvent -InputObject $watcher -EventName $kind -SourceIdentifier $identifier | Out-Null
        $identifiers += $identifier
    }
    $watcher.EnableRaisingEvents = $true
    $writer.WriteLine((@{at=[DateTime]::UtcNow.ToString('o');event='started';root=$sourceRoot;ownedPid=$ownership.pid} | ConvertTo-Json -Compress))
    $deadline = [DateTime]::UtcNow.AddMinutes(20)
    while ([DateTime]::UtcNow -lt $deadline -and (Get-Process -Id $ownership.pid -ErrorAction SilentlyContinue)) {
        Wait-Event -Timeout 1 | Out-Null
        foreach ($eventRecord in @(Get-Event | Where-Object { $identifiers -contains $_.SourceIdentifier })) {
            $details = $eventRecord.SourceEventArgs
            $writer.WriteLine((@{at=$eventRecord.TimeGenerated.ToUniversalTime().ToString('o');event=$eventRecord.SourceIdentifier.Split('-')[-1];path=$details.FullPath;oldPath=$details.OldFullPath} | ConvertTo-Json -Compress))
            Remove-Event -EventIdentifier $eventRecord.EventIdentifier
        }
    }
} finally {
    $watcher.EnableRaisingEvents = $false
    foreach ($identifier in $identifiers) { Unregister-Event -SourceIdentifier $identifier -ErrorAction SilentlyContinue }
    $watcher.Dispose()
    $writer.WriteLine((@{at=[DateTime]::UtcNow.ToString('o');event='stopped'} | ConvertTo-Json -Compress))
    $writer.Dispose()
}
