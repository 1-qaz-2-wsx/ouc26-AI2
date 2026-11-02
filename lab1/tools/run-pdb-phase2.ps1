param([ValidateSet('Test','Build','Demo','Hard','Compare')][string]$Mode='Test')
$ErrorActionPreference='Stop'
$labRoot=Split-Path -Parent $PSScriptRoot
$runRoot=Join-Path $labRoot '.local/pdb-phase2'
$classes=Join-Path $runRoot 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $labRoot 'WattAi/src') -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac '-J-Duser.language=en' '-encoding' 'UTF-8' '-d' $classes @sources
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
$log=Join-Path $runRoot ($Mode.ToLower()+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log')
if($Mode -eq 'Test'){
    & java '-Xmx768m' '-cp' $classes 'stud.g01.test.DisjointPdbTest' (Join-Path $runRoot ('tests-'+[guid]::NewGuid().ToString('N'))) 2>&1 | Tee-Object -FilePath $log
    if($LASTEXITCODE -ne 0){throw 'Disjoint PDB tests failed'}
    & java '-Xmx768m' '-cp' $classes 'stud.g01.test.BfsComparisonTest' 2>&1 | Tee-Object -FilePath $log -Append
}elseif($Mode -eq 'Compare'){
    & java '-Xmx768m' '-cp' $classes 'stud.g01.runner.BfsComparisonRunner' 2>&1 | Tee-Object -FilePath $log
}else{
    & java '-Xmx768m' '-cp' $classes 'stud.g01.runner.PdbPhase2Runner' $Mode (Join-Path $runRoot 'data') 2>&1 | Tee-Object -FilePath $log
}
if($LASTEXITCODE -ne 0){throw 'PDB phase2 execution failed'}
Write-Output "Local log: $log"
