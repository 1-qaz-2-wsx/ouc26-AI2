param([string]$Javac = 'javac')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot 'WattAi/src'
$runRoot = Join-Path $repoRoot ('.local/compile-' + [guid]::NewGuid().ToString('N'))
$normalizedRoot = Join-Path $runRoot 'normalized-src'
New-Item -ItemType Directory -Force -Path $normalizedRoot | Out-Null
$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$utf8Output = [System.Text.UTF8Encoding]::new($false)
$legacyEncoding = [System.Text.Encoding]::GetEncoding('GB18030')
$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java' -Recurse)
$normalizedFiles = @()
$encodingRows = @()
foreach ($source in $sources) {
    $bytes = [System.IO.File]::ReadAllBytes($source.FullName)
    $relative = $source.FullName.Substring($sourceRoot.Length + 1)
    try {
        $content = $utf8Strict.GetString($bytes)
        $encodingName = 'UTF-8 compatible'
    } catch [System.Text.DecoderFallbackException] {
        $content = $legacyEncoding.GetString($bytes)
        $encodingName = 'GB18030 fallback'
    }
    $destination = Join-Path $normalizedRoot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    # Mechanical encoding conversion of a diagnostic copy; original files are untouched.
    [System.IO.File]::WriteAllText($destination, $content, $utf8Output)
    $normalizedFiles += $destination
    $encodingRows += "$relative`t$encodingName"
}
[System.IO.File]::WriteAllLines((Join-Path $runRoot 'encodings.tsv'), $encodingRows, $utf8Output)
$probes = @(
    @{ Name = 'original-utf8'; Encoding = 'UTF-8'; Files = @($sources.FullName) },
    @{ Name = 'original-gb18030'; Encoding = 'GB18030'; Files = @($sources.FullName) },
    @{ Name = 'normalized-utf8'; Encoding = 'UTF-8'; Files = $normalizedFiles }
)
$summary = @()
foreach ($probe in $probes) {
    $outputDir = Join-Path $runRoot ($probe.Name + '-classes')
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    $files = $probe.Files
    $messages = @(& $Javac '-J-Duser.language=en' '-J-Duser.country=US' '-encoding' $probe.Encoding '-d' $outputDir @files 2>&1)
    $compilerExit = $LASTEXITCODE
    $log = Join-Path $runRoot ($probe.Name + '.log')
    [System.IO.File]::WriteAllLines($log, @($messages | ForEach-Object { $_.ToString() }), $utf8Output)
    $summary += "$($probe.Name): exit=$compilerExit; log=$log"
    if ($probe.Name -eq 'normalized-utf8') { $messages | ForEach-Object { $_.ToString() } }
}
$summary | ForEach-Object { Write-Output $_ }
Write-Output "Diagnostic copies and logs: $runRoot"
# This is a diagnostic command, not a successful build claim.
