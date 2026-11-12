@echo off
REM Build + install the App Store replica debug APK on Windows.
setlocal
set "PROJ=%~dp0.."
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if "%JAVA_HOME%"=="" set "JAVA_HOME=%USERPROFILE%\.jdks\jbr-21.0.11"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

echo ==^> Building debug APK
call "%PROJ%\gradlew.bat" -p "%PROJ%" :androidApp:assembleDebug --console=plain
if errorlevel 1 exit /b 1

set "APK=%PROJ%\androidApp\build\outputs\apk\debug\androidApp-debug.apk"
if not exist "%APK%" (
  echo !! APK not found: %APK%
  exit /b 1
)
echo ==^> APK: %APK%

if "%SKIP_INSTALL%"=="1" exit /b 0

echo ==^> Installing to device
"%ANDROID_HOME%\platform-tools\adb.exe" install -r "%APK%"
echo ==^> Launching
"%ANDROID_HOME%\platform-tools\adb.exe" shell am start -n com.dsh.appstore/com.kyant.backdrop.catalog.MainActivity
echo ==^> Done
endlocal
