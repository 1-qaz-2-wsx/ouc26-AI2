param(
    [ValidateSet('Help','Verify','Build','Solve','Hard','Compare','Query')][string]$Mode='Help',
    [ValidateSet('ASTAR','IDA','PACKED_IDA')][string]$Algorithm='ASTAR',
    [ValidateSet('MISPLACED','MANHATTAN','PDB')][string]$Heuristic='MANHATTAN',
    [string]$InputPath='',
    [string]$CacheDirectory='',
    [string]$OutputDirectory='',
    [long]$BudgetMillis=60000,
    [int]$TtCapacity=1048576,
    [ValidateSet('BASELINE','OPEN_BEST_G','CLOSED_BEST_G','FULL_OPTIMIZED')][string]$AstarMode='BASELINE'
)
$ErrorActionPreference='Stop'
foreach($name in @('Algorithm','Heuristic','OutputDirectory','BudgetMillis','TtCapacity','AstarMode')){
    if($Mode -ne 'Solve' -and $PSBoundParameters.ContainsKey($name)){throw "$name only applies to Solve; Hard uses fixed formal settings"}
}
if($InputPath -and $Mode -notin @('Build','Solve')){throw 'InputPath only applies to Build or Solve'}
if($CacheDirectory -and $Mode -notin @('Build','Solve','Hard','Query')){throw 'CacheDirectory does not apply to this mode'}
$labRoot=Split-Path -Parent $PSScriptRoot
$repoRoot=Split-Path -Parent $labRoot
$runRoot=Join-Path $labRoot '.local/final-tests'
$classes=Join-Path $runRoot 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $labRoot 'WattAi/src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac '-J-Duser.language=en' '-encoding' 'UTF-8' '-d' $classes @sources
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
$revision=& git -c "safe.directory=$repoRoot" -C $repoRoot rev-parse HEAD
$dirty=[bool](& git -c "safe.directory=$repoRoot" -C $repoRoot status --porcelain)
$identity=($sources | Sort-Object | ForEach-Object {(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}) -join ''
$sha=[Security.Cryptography.SHA256]::Create()
try {$sourceHash=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($identity)))).Replace('-','')}
finally {$sha.Dispose()}
$javaArgs=@('-Xmx2g','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',
    "-Dexperiment.revision=$revision","-Dexperiment.dirty=$($dirty.ToString().ToLower())","-Dexperiment.source_sha256=$sourceHash",
    '-cp',$classes,'stud.g01.runner.LabRunner',$Mode)
if($Mode -in @('Build','Solve') -and $InputPath){$javaArgs+=@('--input',[IO.Path]::GetFullPath($InputPath))}
if($Mode -in @('Build','Solve','Hard','Query') -and $CacheDirectory){$javaArgs+=@('--cache',[IO.Path]::GetFullPath($CacheDirectory))}
if($Mode -eq 'Solve'){
    $javaArgs+=@('--algorithm',$Algorithm,'--heuristic',$Heuristic,'--budget-ms',$BudgetMillis.ToString())
    if($Algorithm -eq 'ASTAR'){$javaArgs+=@('--astar-mode',$AstarMode)}else{$javaArgs+=@('--tt',$TtCapacity.ToString())}
    if($OutputDirectory){$javaArgs+=@('--output',[IO.Path]::GetFullPath($OutputDirectory))}
}
$log=Join-Path $runRoot ($Mode.ToLower()+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,6)+'.log')
$previousEncoding=[Console]::OutputEncoding
try {
    [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
    if($Mode -eq 'Build'){
        # 每轮离线建表最多30分钟；仅在本进程作用域启动隐藏Java，不改系统环境。
        $quotedArgs=@($javaArgs | ForEach-Object { '"'+$_+'"' })
        $process=Start-Process -FilePath (Get-Command java).Source -ArgumentList $quotedArgs -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError ($log+'.error.log')
        if(-not $process.WaitForExit(1800000)){$process.Kill();throw 'Offline build exceeded 30 minutes'}
        $process.Refresh()
        Get-Content -LiteralPath $log -Encoding UTF8
        if($process.ExitCode -ne 0){Get-Content -LiteralPath ($log+'.error.log') -Encoding UTF8;throw 'Offline build failed'}
    }else{
        & java @javaArgs 2>&1 | Tee-Object -FilePath $log
        if($LASTEXITCODE -ne 0){throw "Final test failed; log=$log"}
    }
}finally{[Console]::OutputEncoding=$previousEncoding}
Write-Output "Local log: $log"
