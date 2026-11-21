<#
Bird Theatre Windows desktop installer builder.
Requires Windows, JDK 21+, Maven 3.9+, and a WiX Toolset compatible with jpackage.
Run from repository root: .\scripts\build-windows.ps1 -Version 0.1.1
Optional: $env:UPDATE_SIGNING_PUBLIC_KEY_BASE64 = '<X.509 DER public key base64>'
#>
param(
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version = '0.1.1',
    [string]$PublicKeyBase64 = $env:UPDATE_SIGNING_PUBLIC_KEY_BASE64
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$work = Join-Path $root 'desktop-client'
Push-Location $work
try {
    & mvn -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }

    New-Item -ItemType Directory -Force -Path target/installer | Out-Null
    $args = @(
        '--type', 'msi', '--name', 'BirdTheatre',
        '--app-version', $Version,
        '--vendor', 'GFH',
        '--description', 'Bird Theatre community wildlife monitoring',
        '--input', 'target/jpackage-input',
        '--main-jar', 'bird-theatre-desktop-0.1.0.jar',
        '--main-class', 'ca.gfh.birdtheatre.Launcher',
        '--dest', 'target/installer',
        '--add-modules', 'jdk.crypto.ec,java.net.http,jdk.jsobject,java.desktop',
        '--win-per-user-install',
        '--win-upgrade-uuid', 'a97ee0d9-a576-4ab4-a03e-afc40d4aac13',
        '--win-menu', '--win-shortcut',
        '--java-options', "-Dbirdtheatre.version=$Version"
    )
    if (-not [string]::IsNullOrWhiteSpace($PublicKeyBase64)) {
        $args += @('--java-options', "-Dbirdtheatre.updatePublicKey=$PublicKeyBase64")
    }
    & jpackage @args
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
    $msi = Get-ChildItem -Path target/installer -Filter 'BirdTheatre*.msi' |
        Where-Object { $_.Name -ne 'BirdTheatre-Windows-Setup.msi' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $msi) { throw 'No installer was produced' }
    $dest = Join-Path (Join-Path $work 'target/installer') 'BirdTheatre-Windows-Setup.msi'
    Copy-Item $msi.FullName $dest -Force
    Write-Host "Installer: $dest"
    Get-FileHash $dest -Algorithm SHA256 | Format-Table
} finally { Pop-Location }
