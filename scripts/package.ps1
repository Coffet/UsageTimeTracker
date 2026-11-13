$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root
$pomText = Get-Content "pom.xml" -Raw
$versionMatch = [regex]::Match($pomText, '(?m)^\s*<version>([^<]+)</version>')
if (-not $versionMatch.Success) { throw "Could not read the project version from pom.xml." }
$version = $versionMatch.Groups[1].Value
if ($env:GITHUB_REF_NAME -and $env:GITHUB_REF_NAME -ne "v$version") {
    throw "Release tag $env:GITHUB_REF_NAME does not match project version $version."
}

& mvn --batch-mode --no-transfer-progress clean package dependency:copy-dependencies `
    -DincludeScope=runtime -DoutputDirectory=target/package/lib
if ($LASTEXITCODE -ne 0) { throw "Maven package build failed." }

$stage = Join-Path $root "target/package"
$dist = Join-Path $root "target/dist"
New-Item -ItemType Directory -Force -Path $stage, $dist | Out-Null
Copy-Item "target/usage-time-tracker-$version.jar" $stage -Force
& java "scripts/TrimSqliteNatives.java" (Join-Path $stage "lib")
if ($LASTEXITCODE -ne 0) { throw "Failed to trim SQLite native libraries." }

$commonArgs = @(
    "--input", $stage,
    "--name", "UsageTimeTracker",
    "--app-version", $version,
    "--vendor", "UsageTime Tracker",
    "--main-jar", "usage-time-tracker-$version.jar",
    "--main-class", "app.usagetime.tracker.AppLauncher",
    "--add-modules", "java.desktop,java.sql,java.management,java.naming,jdk.crypto.ec,jdk.jfr,jdk.localedata,jdk.unsupported",
    "--jlink-options", "--strip-native-commands --strip-debug --no-man-pages --no-header-files --compress=2"
)

foreach ($type in @("exe", "msi")) {
    & jpackage @commonArgs `
        --dest $dist `
        --type $type `
        --win-dir-chooser `
        --win-menu `
        --win-shortcut
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed for $type. Confirm WiX is installed and available on PATH." }
}

$imageDest = Join-Path $root "target/app-image"
New-Item -ItemType Directory -Force -Path $imageDest | Out-Null
& jpackage @commonArgs --dest $imageDest --type app-image
if ($LASTEXITCODE -ne 0) { throw "jpackage failed for the portable app image." }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $dist "UsageTimeTracker-$version-windows-portable.zip"
[System.IO.Compression.ZipFile]::CreateFromDirectory(
    (Join-Path $imageDest "UsageTimeTracker"),
    $zip,
    [System.IO.Compression.CompressionLevel]::Optimal,
    $true
)
