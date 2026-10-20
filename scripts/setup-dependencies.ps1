# Download the MySQL JDBC driver from Maven Central.
# The driver belongs in WEB-INF/lib so Tomcat can load it for this app.
# Check its SHA-256 value before use. Do not accept an unknown replacement file.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'src/main/webapp/WEB-INF/lib/mysql-connector-j.jar'
# This hash belongs to the pinned Connector/J version in the download URL.
$expectedHash = '69084713593A4AA8D07C383619B9639276F08BCCF8FAF1C562178147D389B1E1'
New-Item -ItemType Directory -Force (Join-Path $projectRoot 'src/main/webapp/WEB-INF/lib') | Out-Null
# Download only if the file is absent. Always check the hash afterward.
if (-not (Test-Path -LiteralPath $jarPath)) {
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/26.7.0/mysql-connector-j-26.7.0.jar' -OutFile $jarPath
}
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Connector/J checksum mismatch. Replace src/main/webapp/WEB-INF/lib/mysql-connector-j.jar with the documented official version.'
}
Write-Output 'MySQL Connector/J 26.7.0 is ready. Refresh the project in Eclipse.'
