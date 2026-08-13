@echo off
REM ============================================================
REM XtoXray Single-Version Build (Windows)
REM ============================================================
REM Usage: build.bat [mc_version]
REM
REM Examples:
REM   build.bat          (build default version from gradle.properties)
REM   build.bat 26.1     (build for MC 26.1)
REM   build.bat 26.1.2   (build for MC 26.1.2)
REM ============================================================

setlocal enabledelayedexpansion

echo.
echo ============================================
echo  XtoXray Single-Version Builder
echo ============================================
echo.

if "%~1"=="" (
    echo Building default version from gradle.properties...
    echo.
    call gradlew.bat build --no-daemon
) else (
    set "TARGET_VERSION=%~1"
    echo Building for MC !TARGET_VERSION!...
    
    REM Read config from versions.json
    for /f "tokens=*" %%i in ('powershell -NoProfile -Command ^
        "$json = Get-Content 'versions.json' ^| ConvertFromJson; ^
         $v = $json.versions ^| Where-Object { $_.mc_version -eq '!TARGET_VERSION!' -and $_.enabled }; ^
         if ($v) { ^
             Write-Output \"$($v.fabric_api)|$($v.fabric_loader)|$($v.java_version)|$($v.modmenu_version)\" ^
         } else { ^
             Write-Output 'NOT_FOUND' ^
         }"') do (
        
        if "%%i"=="NOT_FOUND" (
            echo ERROR: Version !TARGET_VERSION! not found or disabled in versions.json
            echo.
            echo Available versions:
            powershell -NoProfile -Command ^
                "$json = Get-Content 'versions.json' ^| ConvertFromJson; ^
                 $json.versions ^| Where-Object { $_.enabled } ^| ForEach-Object { Write-Host \"  - $($_.mc_version)\" }"
            pause
            exit /b 1
        )
        
        for /f "tokens=1-4 delims=|" %%a in ("%%i") do (
            set "FABRIC_API=%%a"
            set "LOADER=%%b"
            set "JAVA_VER=%%c"
            set "MODMENU=%%d"
        )
    )
    
    echo   Fabric API: !FABRIC_API!
    echo   Loader:     !LOADER!
    echo   Java:       !JAVA_VER!
    echo.
    
    call gradlew.bat build ^
        -Pmc_version=!TARGET_VERSION! ^
        -Pfabric_api_version=!FABRIC_API! ^
        -Ploader_version=!LOADER! ^
        -Pjava_version=!JAVA_VER! ^
        -Pmodmenu_version=!MODMENU! ^
        --no-daemon
)

if !ERRORLEVEL! EQU 0 (
    echo.
    echo ============================================
    echo  Build successful!
    echo ============================================
    echo.
    echo Output JARs in dist/:
    for /d %%d in (dist\*) do (
        for %%f in (dist\%%~nxd\*.jar) do (
            echo   %%~dpfx%%~nxf
        )
    )
) else (
    echo.
    echo ============================================
    echo  Build FAILED!
    echo ============================================
)

echo.
pause
