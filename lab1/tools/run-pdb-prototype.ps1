param([ValidateSet('Demo', 'Test', 'All')][string]$Mode = 'All')
$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$runRoot = Join-Path $labRoot ('.local/pdb-phase1-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $runRoot 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $labRoot 'WattAi/src') -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac '-J-Duser.language=en' '-encoding' 'UTF-8' '-d' $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
if ($Mode -eq 'Test' -or $Mode -eq 'All') {
    & java '-Xmx512m' '-cp' $classes 'stud.g01.test.PdbPrototypeTest' (Join-Path $runRoot 'tests')
    if ($LASTEXITCODE -ne 0) { throw 'PDB tests failed' }
}
if ($Mode -eq 'Demo' -or $Mode -eq 'All') {
    & java '-Xmx512m' '-cp' $classes 'stud.g01.runner.PdbPrototypeDemo' (Join-Path $runRoot 'group-123.pdb')
    if ($LASTEXITCODE -ne 0) { throw 'PDB demonstration failed' }
}
Write-Output "Build and generated files (local only): $runRoot"
