param(
    [ValidateSet('Build','Test','Check','Run')][string]$Mode='Test',
    [string]$InputPath='',
    [string]$FontPath='',
    [string]$ToolchainRoot='',
    [string]$BuildDirectory='',
    [switch]$FetchSFML
)
$ErrorActionPreference='Stop'
$labRoot=Split-Path -Parent $PSScriptRoot
if(-not $BuildDirectory){
    $BuildDirectory=Join-Path $labRoot $(if($Mode -eq 'Test'){'.local/player-build/parser'}else{'.local/player-build/gui'})
}
$BuildDirectory=[IO.Path]::GetFullPath($BuildDirectory)
if(-not $InputPath){$InputPath=Join-Path $labRoot 'examples/paths'}
$previousPath=$env:PATH
try {
    if($ToolchainRoot){
        $ToolchainRoot=(Resolve-Path -LiteralPath $ToolchainRoot).Path
        $compiler=Join-Path $ToolchainRoot 'bin/mingw/bin/g++.exe'
        $ninja=Join-Path $ToolchainRoot 'bin/ninja/win/x64/ninja.exe'
        $cmake=Join-Path $ToolchainRoot 'bin/cmake/win/x64/bin/cmake.exe'
        $ctest=Join-Path $ToolchainRoot 'bin/cmake/win/x64/bin/ctest.exe'
        foreach($program in @($compiler,$ninja,$cmake,$ctest)){
            if(-not (Test-Path -LiteralPath $program)){throw "Tool not found: $program"}
        }
        # CMake stores these paths in generated CMake code; backslashes become escapes.
        $compiler=$compiler.Replace('\','/')
        $ninja=$ninja.Replace('\','/')
        $env:PATH=(Split-Path -Parent $compiler)+';'+$env:PATH
    }elseif($Mode -in @('Build','Test')){
        $cmake=(Get-Command cmake -ErrorAction Stop).Source
        $ctest=(Get-Command ctest -ErrorAction Stop).Source
    }
    if($Mode -in @('Build','Test')){
        $options=@('--fresh','-S',(Join-Path $labRoot 'visualization'),'-B',$BuildDirectory,'-DCMAKE_BUILD_TYPE=Release')
        if($ToolchainRoot){$options+=@('-G','Ninja',"-DCMAKE_CXX_COMPILER=$compiler","-DCMAKE_MAKE_PROGRAM=$ninja")}
        $options+=@("-DNP_BUILD_PLAYER=$(if($Mode -eq 'Test'){'OFF'}else{'ON'})", "-DNP_FETCH_SFML=$(if($FetchSFML -and $Mode -eq 'Build'){'ON'}else{'OFF'})")
        & $cmake @options
        if($LASTEXITCODE -ne 0){throw 'CMake configure failed; use -FetchSFML or configure a matching SFML_DIR'}
        & $cmake --build $BuildDirectory --config Release --parallel 4
        if($LASTEXITCODE -ne 0){throw 'Player build failed'}
        & $ctest --test-dir $BuildDirectory -C Release --output-on-failure
        if($LASTEXITCODE -ne 0){throw 'Player tests failed'}
    }else{
        $name=if($Mode -eq 'Check'){'path_check'}else{'npuzzle_player'}
        $binary=Join-Path $BuildDirectory ($name+$(if($IsWindows -or $env:OS -eq 'Windows_NT'){'.exe'}else{''}))
        if(-not (Test-Path -LiteralPath $binary)){$binary=Join-Path $BuildDirectory ('Release/'+(Split-Path -Leaf $binary))}
        if(-not (Test-Path -LiteralPath $binary)){throw 'Executable missing. Run -Mode Build first, or select the existing -BuildDirectory'}
        $arguments=@((Resolve-Path -LiteralPath $InputPath).Path)
        if($Mode -eq 'Run' -and $FontPath){$arguments+=@('--font',(Resolve-Path -LiteralPath $FontPath).Path)}
        & $binary @arguments
        if($LASTEXITCODE -ne 0){throw "Player/checker returned $LASTEXITCODE; review rejected file messages"}
    }
}finally{
    $env:PATH=$previousPath
}
