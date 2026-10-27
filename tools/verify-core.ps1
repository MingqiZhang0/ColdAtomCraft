$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('cac-smoke-' + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
try {
    $sourcePaths = @(
        Get-ChildItem (Join-Path $root 'physics-core/src/main/java') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
        Get-ChildItem (Join-Path $root 'experiment-core/src/main/java') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
        Join-Path $root 'verification/Smoke.java'
        Join-Path $root 'verification/OpticsSmoke.java'
        Join-Path $root 'verification/CoherentSmoke.java'
        Join-Path $root 'verification/OpticalPathSmoke.java'
        Join-Path $root 'verification/GeometryOpticsSmoke.java'
        Join-Path $root 'verification/PowerMeterSmoke.java'
    )
    & javac --release 25 -encoding UTF-8 -d $tmp @sourcePaths
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
    & java -cp $tmp dev.coldatomcraft.verification.Smoke
    if ($LASTEXITCODE -ne 0) { throw 'standalone smoke failed' }
    & java -cp $tmp dev.coldatomcraft.verification.OpticsSmoke
    if ($LASTEXITCODE -ne 0) { throw 'optics smoke failed' }
    & java -cp $tmp dev.coldatomcraft.verification.CoherentSmoke
    if ($LASTEXITCODE -ne 0) { throw 'coherent optics smoke failed' }
    & java -cp $tmp dev.coldatomcraft.verification.OpticalPathSmoke
    if ($LASTEXITCODE -ne 0) { throw 'optical-path smoke failed' }
    & java -cp $tmp dev.coldatomcraft.verification.GeometryOpticsSmoke
    if ($LASTEXITCODE -ne 0) { throw 'geometry optics smoke failed' }
    & java -cp $tmp dev.coldatomcraft.verification.PowerMeterSmoke
    if ($LASTEXITCODE -ne 0) { throw 'optical power smoke failed' }
} finally {
    Remove-Item $tmp -Force -Recurse -ErrorAction SilentlyContinue
}
