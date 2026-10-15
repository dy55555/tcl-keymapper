@echo off
chcp 936 >nul
title 安装 APK 到电视
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem ================= adb 定位 =================
rem 挨个地方找，找到第一个能用的就停
set "ADB="

if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
if defined ADB goto adb_ok

for %%a in (adb.exe) do if not "%%~$PATH:a"=="" set "ADB=%%~$PATH:a"
if defined ADB goto adb_ok

if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if defined ADB goto adb_ok
if exist "C:\platform-tools\adb.exe" set "ADB=C:\platform-tools\adb.exe"
if defined ADB goto adb_ok
if exist "C:\Android\platform-tools\adb.exe" set "ADB=C:\Android\platform-tools\adb.exe"
if defined ADB goto adb_ok
if exist "D:\platform-tools\adb.exe" set "ADB=D:\platform-tools\adb.exe"
if defined ADB goto adb_ok
if exist "D:\Android\platform-tools\adb.exe" set "ADB=D:\Android\platform-tools\adb.exe"
if defined ADB goto adb_ok
if exist "%ProgramFiles%\Android\platform-tools\adb.exe" set "ADB=%ProgramFiles%\Android\platform-tools\adb.exe"
if defined ADB goto adb_ok
goto no_adb

:no_adb
echo.
echo   [错误] 找不到 adb.exe。
echo.
echo   随便用一种办法：
echo     1. 把 adb.exe 放到本文件同一个目录里
echo     2. 把 platform-tools 目录加进系统 PATH
echo     3. 装一份 Android SDK（平台工具）
echo.
echo   adb.exe 来自 Android SDK 的 platform-tools，网上搜
echo   「platform-tools 下载」就能拿到，解压即用。
echo.
pause
exit /b

:adb_ok

rem ================= APK 定位 =================
rem 拖拽 APK 到本文件上 = 装拖进来的那个
rem 直接双击 = 装同目录里的 APK
set "APKFILE=%~1"
if not "%APKFILE%"=="" goto apk_ok

set "IDX=0"
for %%f in ("%~dp0*.apk") do (
    set /a IDX+=1
    set "APK!IDX!=%%~ff"
)
if "%IDX%"=="0" goto no_apk
if "%IDX%"=="1" (
    set "APKFILE=!APK1!"
    goto apk_ok
)
echo.
echo   本目录下有 %IDX% 个 APK：
echo.
for /l %%i in (1,1,%IDX%) do echo      [%%i] !APK%%i!
echo.
set /p PICK=  要装哪个？(直接回车 = 1):
if "%PICK%"=="" set PICK=1
set "APKFILE=!APK%PICK%!"
if "%APKFILE%"=="" goto bad_pick
goto apk_ok

:bad_pick
echo.
echo   [错误] 没有这个序号。
echo.
pause
exit /b

:no_apk
echo.
echo   [错误] 这个目录下没有 .apk 文件。
echo.
echo   把要装的 APK 放到本文件同一个目录里，
echo   或者干脆把 APK 拖到本文件上松手。
echo.
pause
exit /b

:apk_ok

rem ================= 电视 IP =================
set "IPFILE=%~dp0.tv-ip.txt"
set "TVIP="
if exist "%IPFILE%" set /p TVIP=<"%IPFILE%"
if "%TVIP%"=="" set "TVIP=192.168.1.100"

echo.
echo   电视 IP 会记在 .tv-ip.txt 里，下次直接回车就行。
echo.
set /p INPUT=  电视 IP (直接回车 = %TVIP%):
if not "%INPUT%"=="" set "TVIP=%INPUT%"
echo %TVIP%>"%IPFILE%"
set "SER=%TVIP%:5555"

cls
echo.
echo ============================================================
echo   安装到电视  %SER%
echo ============================================================
echo.
echo   要装的包：
echo     %APKFILE%
echo.

echo   [1/5] 连接电视 ...
"%ADB%" connect %SER%
"%ADB%" -s %SER% shell echo ok >nul 2>&1
if errorlevel 1 goto no_conn
echo         连上了
echo.

echo   [2/5] 临时关掉电视的安装校验 ...
"%ADB%" -s %SER% shell pm disable-user --user 0 com.android.packageinstaller
ping -n 2 127.0.0.1 >nul
echo.

echo   [3/5] 安装中，请稍候 ...
"%ADB%" -s %SER% install -r -g "%APKFILE%"
set "RC=%errorlevel%"
echo.

echo   [4/5] 恢复电视的安装校验 ...
"%ADB%" -s %SER% shell pm enable com.android.packageinstaller
echo         已恢复
echo.

echo   [5/5] 结果
echo.
if "%RC%"=="0" goto ok
echo   --------------------------------------------------------
echo   安装失败。adb 返回的原文在上面，常见原因：
echo.
echo     INSTALL_FAILED_ALREADY_EXISTS
echo         电视上已有同包名、但签名不同的应用，先在电视上卸掉旧的
echo     INSTALL_FAILED_VERSION_DOWNGRADE
echo         电视上的版本比这个新，加 -d 或者先卸载
echo     INSTALL_FAILED_UPDATE_INCOMPATIBLE
echo         签名对不上，先在电视上卸载旧版
echo     device offline / not found
echo         电视没连上，先查 IP 和 ADB 开关
echo   --------------------------------------------------------
echo.
echo   如果电视上还有旧版，先在电视上手动卸载一次，再跑本脚本。
echo.
pause
exit /b

:ok
echo   --------------------------------------------------------
echo   装好了。
echo.
echo   电视上就能直接用了 —— 不用重启，不用再碰电脑。
echo   --------------------------------------------------------
echo.
pause
exit /b

:no_conn
cls
echo.
echo ============================================================
echo   连不上电视 %SER%
echo ============================================================
echo.
echo   挨个查一下：
echo.
echo     1. 电视和电脑在不在同一个 WiFi 里
echo     2. IP 对不对（电视上：设置 - 网络设置 里能看）
echo     3. 电视的 ADB 开关是不是没打开
echo        路径：设置 - 关于 - 停在「本机信息」上，
echo              依次按 上 下 左 右
echo        打开时电视会自动重启一次，重启才说明真开了
echo     4. 有些电视第一次连会弹「允许 USB 调试」，
echo        要在电视上点「允许」
echo.
pause
exit /b
