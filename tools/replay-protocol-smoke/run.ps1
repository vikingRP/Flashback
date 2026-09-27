param([switch]$SkipCompile)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $repo
try {
    if (-not $SkipCompile) {
        & ./gradlew.bat compileJava writeMigrationClasspath
        if ($LASTEXITCODE -ne 0) { throw "Gradle compilation failed" }
    }
    $cp = (Get-Content .migration/runtime-classpath.txt -Raw).Trim()
    $javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/javac.exe" } else { (Get-Command javac).Source }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/java.exe" } else { (Get-Command java).Source }
    $classes = Join-Path $repo "build/replay-protocol-smoke"
    New-Item -ItemType Directory -Force $classes | Out-Null
    & $javac -proc:none -cp $cp -d $classes (Join-Path $PSScriptRoot "ReplayProtocolSelfTest.java")
    if ($LASTEXITCODE -ne 0) { throw "Smoke test compilation failed" }
    & $java -cp "$classes;$cp" ReplayProtocolSelfTest
    if ($LASTEXITCODE -ne 0) { throw "Replay protocol smoke test failed" }
} finally { Pop-Location }
