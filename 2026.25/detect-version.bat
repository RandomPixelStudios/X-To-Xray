@echo off
REM ============================================================
REM XtoXray Version Detector (Windows)
REM ============================================================
REM Detects the Minecraft version from a XtoXray JAR file
REM Usage: detect-version.bat <path-to-jar>
REM ============================================================

setlocal enabledelayedexpansion

echo.
echo ============================================
echo  XtoXray Version Detector
echo ============================================
echo.

if "%~1"=="" (
    echo Usage: %~nx0 ^<path-to-xtoxray-jar^>
    echo.
    echo Examples:
    echo   %~nx0 dist\26.2\xtoxray-mc26_2-2026.22+26.2.jar
    echo   %~nx0 dist\26.1\xtoxray-mc26_1-2026.22+26.1.jar
    echo.
    echo You can also drag-and-drop a JAR file onto this script.
    pause
    exit /b 1
)

set "JAR_PATH=%~1"

if not exist "%JAR_PATH%" (
    echo ERROR: File not found: %JAR_PATH%
    pause
    exit /b 1
)

echo Checking JAR: %JAR_PATH%
echo.

REM Method 1: Check JAR filename for MC version
for %%f in ("%JAR_PATH%") do set "JAR_NAME=%%~nf"

echo JAR filename: %JAR_NAME%

REM Extract MC version from filename pattern: xtoxray-mcXX_Y-modversion
echo %JAR_NAME% | findstr /R "mc[0-9]" >nul
if !ERRORLEVEL! EQU 0 (
    for /f "tokens=2 delims=-" %%a in ("%JAR_NAME%") do (
        set "MC_PART=%%a"
    )
    echo Detected from filename: !MC_PART!
)

REM Method 2: Check JAR manifest for MC version
echo.
echo Checking JAR manifest...
jar tf "%JAR_PATH%" META-INF/MANIFEST.MF >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    for /f "tokens=*" %%a in ('jar xf "%JAR_PATH%" META-INF/MANIFEST.MF -C "%TEMP%" 2^>nul ^&^& type "%TEMP%\META-INF\MANIFEST.MF"') do (
        echo %%a | findstr "XtoXray-MC-Version" >nul
        if !ERRORLEVEL! EQU 0 (
            for /f "tokens=2 delims=: " %%b in ("%%a") do (
                echo Detected MC version from manifest: %%b
            )
        )
    )
)

REM Method 3: Check fabric.mod.json inside the JAR
echo.
echo Checking fabric.mod.json inside JAR...
jar tf "%JAR_PATH%" fabric.mod.json >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    powershell -NoProfile -Command ^
        "Add-Type -AssemblyName System.IO.Compression.FileSystem; ^
         $zip = [System.IO.Compression.ZipFile]::OpenRead('%JAR_PATH%'); ^
         $entry = $zip.GetEntry('fabric.mod.json'); ^
         if ($entry) { ^
             $stream = $entry.Open(); ^
             $reader = New-Object System.IO.StreamReader($stream); ^
             $json = $reader.ReadToEnd(); ^
             $reader.Close(); ^
             $zip.Dispose(); ^
             $obj = $json ^| ConvertFrom-Json; ^
             Write-Host 'Mod version:' $obj.version; ^
             $depends = $obj.depends; ^
             if ($depends.'minecraft') { ^
                 Write-Host 'MC dependency:' $depends.'minecraft' ^
             } ^
         } else { ^
             $zip.Dispose(); ^
             Write-Host 'fabric.mod.json not found in JAR' ^
         }"
)

echo.
echo ============================================
echo  Supported XtoXray versions in dist/:
echo ============================================
echo.

if exist "dist" (
    for /d %%d in (dist\*) do (
        echo   %%~nxd
        for %%f in (dist\%%~nxd\*.jar) do (
            echo     %%~nxf
        )
    )
) else (
    echo   (dist/ directory not found)
)

echo.
pause
