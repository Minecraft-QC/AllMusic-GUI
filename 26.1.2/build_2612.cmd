@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.3
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_OPTS=-Dcom.sun.security.enableAIAcaIssuers=true
cd /d C:\Users\QC\Doubao\chats\2026-09-21\new-chat-1\allmusic-gui\26.1.2
C:\Gradle\gradle-9.7.0\bin\gradle.bat build --console=plain --no-daemon > build_log.txt 2>&1
echo EXIT=%ERRORLEVEL% >> build_log.txt
