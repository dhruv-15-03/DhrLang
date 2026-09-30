[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$module = $PSScriptRoot
$root = Split-Path (Split-Path $module -Parent) -Parent
$windows = [System.IO.Path]::DirectorySeparatorChar -eq '\'
$gradle = Join-Path $root 'gradlew'
$maven = Join-Path $module 'mvnw'
if ($windows) {
    $gradle = Join-Path $root 'gradlew.bat'
    $maven = Join-Path $module 'mvnw.cmd'
}
$settings = [System.IO.Path]::Combine($module, '.mvn', 'settings.xml')
$repository = Join-Path $module '.m2'
$saved = @{}
foreach ($name in @('JAVA_OPTS', 'MAVEN_OPTS', 'MAVEN_USER_HOME', 'NODE_OPTIONS', 'GH_TOKEN', 'GITHUB_TOKEN')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Executable @Arguments
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) { throw "$Executable failed with exit code $code" }
}

Push-Location $root
try {
    $env:JAVA_OPTS = '-Xms16m -Xmx64m -XX:+UseSerialGC'
    $env:MAVEN_OPTS = '-Xms32m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC'
    $env:MAVEN_USER_HOME = Join-Path $module '.maven-user-home'
    $env:NODE_OPTIONS = '--max-old-space-size=256'
    $env:GH_TOKEN = $null
    $env:GITHUB_TOKEN = $null
    Invoke-Checked $gradle @('stageCompiler', 'jar', 'generatePomFileForMavenPublication',
        '--no-daemon', '--max-workers=1',
        '-Dorg.gradle.jvmargs=-Xms32m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC')

    $pom = [System.IO.Path]::Combine($root, 'build', 'publications', 'maven', 'pom-default.xml')
    [xml]$publication = Get-Content -LiteralPath $pom -Raw
    $version = $publication.project.version
    $api = [System.IO.Path]::Combine($root, 'build', 'libs', 'plain', "DhrLang-$version.jar")
    if (-not (Test-Path -LiteralPath $api)) { throw 'The local source API artifact was not built' }
    Set-Location -LiteralPath $module
    $common = @('-B', '-ntp', '-T1', '-s', $settings, "-Dmaven.repo.local=$repository")
    Invoke-Checked $maven ($common + @('-N', 'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file',
        "-Dfile=$api", "-DpomFile=$pom", '-Dclassifier=local-source'))
    Invoke-Checked $maven ($common + @('verify'))
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
}
