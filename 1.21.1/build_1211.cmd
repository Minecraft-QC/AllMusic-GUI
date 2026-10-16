@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem ============================================================
rem  AllMusic GUI - one-click build (MC 1.21.1 / mod 3.x)
rem  Builds the project folder this script lives in, so it works
rem  from any location. Finds a suitable JDK and Gradle itself and
rem  falls back to the Gradle wrapper when no Gradle is installed.
rem ============================================================

set "MIN_MAJOR=21"
set "PROJ=1.21.1"

echo === AllMusic GUI build: %PROJ% ^(needs JDK %MIN_MAJOR%+^) ===

rem ---- 1) find a JDK %MIN_MAJOR%+ ----
rem PATH often resolves to an older JDK, so the version is verified
rem against the JDK "release" file instead of trusting javac on PATH.
set "JDK="
if defined JAVA_HOME call :probe "%JAVA_HOME%"
if not defined JDK for /f "delims=" %%j in ('where javac 2^>nul') do if not defined JDK call :probe "%%~dpj.."
if not defined JDK (
	echo [ERROR] no JDK %MIN_MAJOR% or newer found.
	echo         install a JDK and set JAVA_HOME, then run this script again.
	exit /b 1
)
set "JAVA_HOME=%JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
rem On machines behind a TLS-inspecting proxy (Steam++, Clash, corporate MITM)
rem the proxy's root CA lives in the Windows store but not in the JDK's
rem cacerts, so Gradle cannot reach the network. WINDOWS-ROOT makes the JDK
rem trust the Windows store as well; it is harmless where the JDK works.
set "GRADLE_OPTS=-Dcom.sun.security.enableAIAcaIssuers=true -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
echo JDK    : %JAVA_HOME%

rem ---- 2) find Gradle: GRADLE_HOME, then PATH, then the wrapper ----
set "GRADLE_CMD="
if defined GRADLE_HOME if exist "%GRADLE_HOME%\bin\gradle.bat" set "GRADLE_CMD=%GRADLE_HOME%\bin\gradle.bat"
if not defined GRADLE_CMD for /f "delims=" %%g in ('where gradle.bat 2^>nul') do if not defined GRADLE_CMD set "GRADLE_CMD=%%g"
if not defined GRADLE_CMD if exist "%~dp0gradlew.bat" set "GRADLE_CMD=%~dp0gradlew.bat"
if not defined GRADLE_CMD (
	echo [ERROR] Gradle not found. Install Gradle 9.x, set GRADLE_HOME,
	echo         or keep gradlew.bat in this folder, then run again.
	exit /b 1
)
echo Gradle : %GRADLE_CMD%

rem ---- 3) build ----
rem "call" is required: invoking a .bat without it transfers control away
rem and none of the lines below would ever run.
call "%GRADLE_CMD%" build --console=plain --no-daemon > build_log.txt 2>&1
set "RC=%ERRORLEVEL%"
>>build_log.txt echo EXIT=%RC%

if "%RC%"=="0" (
	echo [OK] BUILD SUCCESSFUL
	for %%f in ("build\libs\*.jar") do echo      %%~nxf
) else (
	echo [FAILED] exit code %RC% - full log below and in build_log.txt
	type build_log.txt
)
exit /b %RC%

rem ---- accept a JDK folder only when it really is JDK %MIN_MAJOR%+ ----
:probe
if "%~1"=="" exit /b 1
for %%d in ("%~1") do set "PDIR=%%~fd"
if not exist "!PDIR!\bin\javac.exe" exit /b 1
if not exist "!PDIR!\release" exit /b 1
set "JV="
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"JAVA_VERSION=" "!PDIR!\release" 2^>nul') do set "JV=%%~v"
if not defined JV exit /b 1
for /f "tokens=1 delims=." %%m in ("!JV!") do set "JMAJOR=%%m"
if !JMAJOR! GEQ %MIN_MAJOR% set "JDK=!PDIR!"
exit /b 0
