# Compiles the project and runs the JUnit 5 test suite. Extra arguments are passed to the JUnit
# console launcher, e.g.:
#   .\test.ps1                                              # all tests
#   .\test.ps1 --select-class com.shipfast.order.OrderServiceTest
#   .\test.ps1 --select-method com.shipfast.common.MoneyTest#addsAmounts
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$junitVersion = "1.10.2"
$junitJar = "lib/junit-platform-console-standalone-$junitVersion.jar"

if (-not (Test-Path $junitJar)) {
    New-Item -ItemType Directory -Force lib | Out-Null
    Write-Host "Downloading JUnit console launcher..."
    Invoke-WebRequest -UseBasicParsing -OutFile $junitJar `
        "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$junitVersion/junit-platform-console-standalone-$junitVersion.jar"
}

if (Test-Path build/classes) { Remove-Item -Recurse -Force build/classes }
if (Test-Path build/test-classes) { Remove-Item -Recurse -Force build/test-classes }
New-Item -ItemType Directory -Force build/classes, build/test-classes | Out-Null

(Get-ChildItem -Recurse src/main/java -Filter *.java).FullName | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } |
    Set-Content -Encoding ascii build/main-sources.txt
(Get-ChildItem -Recurse src/test/java -Filter *.java).FullName | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } |
    Set-Content -Encoding ascii build/test-sources.txt

javac -encoding UTF-8 -d build/classes "@build/main-sources.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
javac -encoding UTF-8 -cp "build/classes;$junitJar" -d build/test-classes "@build/test-sources.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$junitArgs = @($args)
if (-not ($junitArgs | Where-Object { $_ -like "--select*" -or $_ -like "--scan*" })) {
    $junitArgs = @("--scan-class-path") + $junitArgs
}
java -jar $junitJar execute --class-path "build/classes;build/test-classes" --disable-banner --details=tree @junitArgs
exit $LASTEXITCODE
