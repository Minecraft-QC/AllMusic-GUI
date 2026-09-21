@echo off
set CACHE=%USERPROFILE%\.gradle\caches\fabric-loom\1.21.1
del /q "%CACHE%\minecraft-server.jar" "%CACHE%\minecraft-server.jar.part" 2>nul
echo [1/3] server.jar ...
curl.exe -L -sS -o "%CACHE%\minecraft-server.jar" --max-time 900 "https://bmclapi2.bangbang93.com/version/1.21.1/server"
echo server_done:%ERRORLEVEL%
echo [2/3] client_mappings ...
curl.exe -L -sS -o "%CACHE%\client_mappings.txt" --max-time 300 "https://bmclapi2.bangbang93.com/version/1.21.1/client_mappings"
echo client_map_done:%ERRORLEVEL%
echo [3/3] server_mappings ...
curl.exe -L -sS -o "%CACHE%\server_mappings.txt" --max-time 300 "https://bmclapi2.bangbang93.com/version/1.21.1/server_mappings"
echo server_map_done:%ERRORLEVEL%
echo ALL_DONE
