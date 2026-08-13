@echo off
REM ============================================================
REM XtoXray Multi-Version Build Script (Windows)
REM ============================================================
REM Builds the mod for ALL Minecraft versions defined in versions.json
REM Output goes to: dist/<mc_version>/xtoxray-mc<version>.jar
REM ============================================================

setlocal enabledelayedexpansion

echo.
echo ============================================
echo  XtoXray Multi-Version Builder
echo ============================================
echo.

REM Check if versions.json exists
if not exist "versions.json" (
    echo ERROR: versions.json not found!
    echo Please run this script from the project root directory.
    pause
    exit /b 1
)

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found!
    echo Please run this script from the project root directory.
    pause
    exit /b 1
)

REM Parse versions.json and build each version
REM We use PowerShell to parse JSON since batch doesn't support it natively

echo Reading versions from versions.json...
echo.

set "COUNT=0"
set "SUCCESS=0"
set "FAILED=0"

REM Use PowerShell to extract version info and build each one
for /f "tokens=*" %%i in ('powershell -NoProfile -Command ^
    "$json = Get-Content 'versions.json' | ConvertFromJson; ^
     foreach ($v in $json.versions) { ^
         if ($v.enabled) { ^
             Write-Output \"$($v.mc_version)|$($v.fabric_api)|$($v.fabric_loader)|$($v.java_version)|$($v.modmenu_version)\" ^
         } ^
     }"') do (
    
    REM Parse the pipe-separated values
    for /f "tokens=1-5 delims=|" %%a in ("%%i") do (
        set "MC_VER=%%a"
        set "FABRIC_API=%%b"
        set "LOADER=%%c"
        set "JAVA_VER=%%d"
        set "MODMENU=%%e"
    )
    
    set /a COUNT+=1
    echo.
    echo --------------------------------------------
    echo  Building for MC !MC_VER!...
    echo    Fabric API: !FABRIC_API!
    echo    Loader:     !LOADER!
    echo    Java:       !JAVA_VER!
    echo --------------------------------------------
    
    REM Clean first to avoid conflicts
    call gradlew.bat clean >nul 2>&1
    
    REM Build with version-specific properties
    call gradlew.bat build ^
        -Pmc_version=!MC_VER! ^
        -Pfabric_api_version=!FABRIC_API! ^
        -Ploader_version=!LOADER! ^
        -Pjava_version=!JAVA_VER! ^
        -Pmodmenu_version=!MODMENU! ^
        --no-daemon
    
    if !ERRORLEVEL! EQU 0 (
        echo  [SUCCESS] MC !MC_VER! built successfully!
        set /a SUCCESS+=1
    ) else (
        echo  [FAILED]  MC !MC_VER! build failed!
        set /a FAILED+=1
    )
)

echo.
echo ============================================
echo  Build Summary
echo ============================================
echo  Total versions: !COUNT!
echo  Successful:     !SUCCESS!
echo  Failed:         !FAILED!
echo ============================================
echo.

REM List the built JARs
echo Built JARs in dist/:
echo.
if exist "dist" (
    for /d %%d in (dist\*) do (
        echo   dist\%%~nxd\
        for %%f in (dist\%%~nxd\*.jar) do (
            echo     %%~nxf
        )
    )
) else (
    echo   (no builds found)
)

echo.
echo Done!
pause
