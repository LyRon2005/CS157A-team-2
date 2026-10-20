# Build tool, not the website server. Requires JDK 17+ and Tomcat 11.
# Run from PowerShell: ./scripts/build.ps1 -TomcatHome "E:/apache-tomcat-11.0.25"
# Output: build/SJSUClassrooms.war. Deploy it to Tomcat.
# Only webapp files and compiled classes are packaged. The private config file stays outside.
param([string]$TomcatHome = $env:CATALINA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $TomcatHome -or -not (Test-Path -LiteralPath (Join-Path $TomcatHome 'lib/servlet-api.jar'))) {
    throw 'Supply -TomcatHome with your Tomcat 11 installation folder.'
}
Push-Location $projectRoot
try {
    # Find the real JDK location so javac and jar work even when PATH contains a Java launcher shortcut.
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = 'java'
    $startInfo.Arguments = '-XshowSettings:properties -version'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardError = $true
    $javaProcess = [System.Diagnostics.Process]::Start($startInfo)
    $javaSettings = $javaProcess.StandardError.ReadToEnd()
    $javaProcess.WaitForExit()
    if ($javaSettings -notmatch 'java.home\s*=\s*([^\r\n]+)') { throw 'Cannot locate the JDK.' }
    $jdkBin = Join-Path $Matches[1].Trim() 'bin'
    & "$PSScriptRoot/setup-dependencies.ps1"
    # Use a new staging folder so old compiled files cannot enter the WAR.
    $stage = Join-Path $projectRoot ('build/package-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path "$stage/WEB-INF/classes" -Force | Out-Null
    Copy-Item 'src/main/webapp/*' $stage -Recurse -Force
    # Compile our Java code against the Servlet API supplied by Tomcat.
    $sources = @(Get-ChildItem 'src/main/java' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & "$jdkBin/javac.exe" --release 17 -encoding UTF-8 -cp "$TomcatHome/lib/servlet-api.jar" -d "$stage/WEB-INF/classes" @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    # Package the standard web application layout. Tomcat supplies its own Servlet API at runtime.
    & "$jdkBin/jar.exe" --create --file build/SJSUClassrooms.war -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'WAR packaging failed.' }
    # Only remove the generated staging directory under this project's build folder.
    $resolvedStage = (Resolve-Path -LiteralPath $stage).Path
    $buildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build')) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedStage.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid staging path.' }
    Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    Write-Output 'Built build/SJSUClassrooms.war (no database credentials included).'
} finally { Pop-Location }
