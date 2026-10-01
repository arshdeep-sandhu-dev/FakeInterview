# Compiles the app and runs the Black Friday load simulation. Options:
#   .\sim.ps1 [--seed N] [--shoppers N] [--threads N]
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (Test-Path build/classes) { Remove-Item -Recurse -Force build/classes }
New-Item -ItemType Directory -Force build/classes | Out-Null
(Get-ChildItem -Recurse src/main/java -Filter *.java).FullName | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } |
    Set-Content -Encoding ascii build/main-sources.txt

javac -encoding UTF-8 -d build/classes "@build/main-sources.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp build/classes com.shipfast.sim.FlashSaleSimulation @args
exit $LASTEXITCODE
