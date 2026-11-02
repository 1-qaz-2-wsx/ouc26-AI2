param(
    [ValidateSet('Test','Build','Hard','Query','Compare')][string]$Mode='Test',
    [ValidateSet('555','663','762','771')][string]$Preset='555',
    [int]$TtCapacity=0,
    [long]$BudgetMillis=60000,
    [string]$CacheDirectory=''
)
$ErrorActionPreference='Stop'
$labRoot=Split-Path -Parent $PSScriptRoot
if(-not $CacheDirectory){$CacheDirectory=Join-Path $labRoot '.local/leader-pdb'}
$runRoot=Join-Path $labRoot '.local/leader'
$classes=Join-Path $runRoot 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $labRoot 'WattAi/src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac '-J-Duser.language=en' '-encoding' 'UTF-8' '-d' $classes @sources
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
if($Mode -eq 'Test'){
    foreach($test in @('SafeIdaStarTest','PackedIdaStarTest')){
        & java '-Xmx768m' '-cp' $classes "stud.g01.test.$test"
        if($LASTEXITCODE -ne 0){throw "Failed: $test"}
    }
    & java '-Xmx768m' '-cp' $classes 'stud.g01.test.AdvancedPdbTest' (Join-Path $runRoot ('test-'+[guid]::NewGuid().ToString('N')))
    if($LASTEXITCODE -ne 0){throw 'Failed: AdvancedPdbTest'}
    exit 0
}
if($Mode -eq 'Build' -and $Preset -ne '555'){
    $freeBytes=(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB
    if($freeBytes -lt 3GB){throw "Need 3 GiB free RAM before large build; currently $([math]::Round($freeBytes/1GB,2)) GiB. No programs were closed."}
}
$log=Join-Path $runRoot ("$($Mode.ToLower())-$Preset-tt$TtCapacity-"+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log')
$repoRoot=Split-Path -Parent $labRoot
$revision=& git -c "safe.directory=$repoRoot" -C $repoRoot rev-parse HEAD
$dirty=[bool](& git -c "safe.directory=$repoRoot" -C $repoRoot status --porcelain)
$sourceIdentity=($sources | Sort-Object | ForEach-Object {(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}) -join ''
$sourceDigest=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($sourceIdentity)))
$metadata=@("-Dexperiment.revision=$revision","-Dexperiment.dirty=$($dirty.ToString().ToLower())","-Dexperiment.source_sha256=$sourceDigest")
if($Mode -eq 'Build'){
    # 单轮离线建表强制30分钟上限；已写完的组文件可供下次复用。
    $arguments=@('-Xmx2g','-Dfile.encoding=UTF-8')+$metadata+@('-cp',('"'+$classes+'"'),'stud.g01.runner.LeaderBenchmarkRunner',$Mode,$Preset,('"'+$CacheDirectory+'"'),$TtCapacity,$BudgetMillis)
    $process=Start-Process -FilePath (Get-Command java).Source -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError ($log+'.error.log')
    if(-not $process.WaitForExit(1800000)){$process.Kill();throw 'Offline build exceeded 30-minute budget; completed group files retained'}
    Get-Content -LiteralPath $log
    if($process.ExitCode -ne 0){Get-Content -LiteralPath ($log+'.error.log');throw 'Build failed'}
}else{
    if($Mode -eq 'Compare'){
        & java '-Xmx2g' @metadata '-cp' $classes 'stud.g01.runner.LeaderComparisonRunner' (Join-Path $runRoot 'comparison.csv') 2>&1 | Tee-Object -FilePath $log
    }else{
        & java '-Xmx2g' '-Dfile.encoding=UTF-8' @metadata '-cp' $classes 'stud.g01.runner.LeaderBenchmarkRunner' $Mode $Preset $CacheDirectory $TtCapacity $BudgetMillis 2>&1 | Tee-Object -FilePath $log
    }
    if($LASTEXITCODE -ne 0){Write-Output "Result not accepted; exit=$LASTEXITCODE; log=$log";exit $LASTEXITCODE}
}
Write-Output "Local log: $log"
