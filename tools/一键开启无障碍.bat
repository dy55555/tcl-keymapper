@echo off
title 按键改键器 - 按键服务开关
cd /d "%~dp0"

set ADB=%~dp0adb.exe
if not exist "%ADB%" (
    echo.
    echo [错误] 找不到 adb.exe，请确认文件完整。
    echo.
    pause
    exit /b
)

if "%~1"=="" goto menu
if /i "%~1"=="on" goto do_on
if /i "%~1"=="off" goto do_off
if /i "%~1"=="status" goto do_status
goto menu

:menu
cls
echo.
echo ============================================================
echo 按键改键器  -  按键服务开关
echo   作者 22512199@qq.com
echo ============================================================
echo.
echo   [1] 开启按键服务
echo   [2] 关闭按键服务（不再改键，恢复原厂按键）
echo   [3] 查看当前状态
echo   [0] 退出
echo.
echo ------------------------------------------------------------
echo   提示：电视需先开 ADB（设置-关于本机-本机信息-按上下左右）
echo         电视 IP 在 设置 - 网络设置 里能看到
echo ------------------------------------------------------------
echo.
set /p CHOICE=请选择 (直接回车 = 开启): 
if "%CHOICE%"=="" set CHOICE=1
if "%CHOICE%"=="1" goto askip_on
if "%CHOICE%"=="2" goto askip_off
if "%CHOICE%"=="3" goto askip_status
if "%CHOICE%"=="0" exit /b
goto menu

rem ================= 开 =================
:needip
echo.
echo  [提示] 没有输入电视 IP。请打开电视的【设置】→【网络设置】查一下 IP，再重新运行。
echo         直接输入电视的 IP 地址，例如 192.168.1.100。
echo.
pause
exit /b

:askip_on
set /p TVIP=请输入电视 IP: 
if "%TVIP%"=="" goto needip
goto do_on

:do_on
cls
echo.
echo [1/3] 连接电视 %TVIP% ...
"%ADB%" connect %TVIP%:5555
timeout /t 3 /nobreak > nul
"%ADB%" devices
echo.
echo [2/3] 授权按键改键器自行开启服务 ...
"%ADB%" -s %TVIP%:5555 shell pm grant com.tclhotkey.redirect android.permission.WRITE_SECURE_SETTINGS
timeout /t 2 /nobreak > nul
echo.
echo [3/3] 启动按键改键器，让它自己开启服务 ...
"%ADB%" -s %TVIP%:5555 shell am start -n com.tclhotkey.redirect/com.tclhotkey.redirect.LaunchActivity
timeout /t 4 /nobreak > nul
echo.
echo ============================================================
echo   已开启。
echo.
echo   现在去电视上打开「按键改键器」，
echo   选一个按键，再选你要打开的应用就能用了。
echo ============================================================
echo.
pause
goto menu

rem ================= 关 =================
:askip_off
set /p TVIP=请输入电视 IP: 
if "%TVIP%"=="" goto needip
goto do_off

:do_off
cls
echo.
echo [1/4] 连接电视 %TVIP% ...
"%ADB%" connect %TVIP%:5555
timeout /t 3 /nobreak > nul

echo.
echo [2/4] 让按键改键器把自己从系统服务列表里移除 ...
"%ADB%" -s %TVIP%:5555 shell am start -n com.tclhotkey.redirect/com.tclhotkey.redirect.SetTargetActivity --es pkg __disable__
timeout /t 3 /nobreak > nul

echo.
echo [3/4] 撤销授权 ...
"%ADB%" -s %TVIP%:5555 shell pm revoke com.tclhotkey.redirect android.permission.WRITE_SECURE_SETTINGS
timeout /t 1 /nobreak > nul

echo.
echo [4/4] 停止按键改键器 ...
"%ADB%" -s %TVIP%:5555 shell am force-stop com.tclhotkey.redirect
timeout /t 2 /nobreak > nul

echo.
echo ============================================================
echo   已关闭。
echo.
echo   遥控器的三个键现在恢复成电视原来的样子
echo   （B站键开小电视、奇异果键开奇异果等）。
echo.
echo   按键映射还保留着，以后重新开启就能直接用。
echo   想连映射一起清掉：在电视上打开「按键改键器」，
echo   选最后一行「全部恢复出厂」即可。
echo.
echo   如果想彻底卸载：在电视的应用列表里
echo   找到「按键改键器」并卸载。
echo ============================================================
echo.
pause
goto menu

rem ================= 查 =================
:askip_status
set /p TVIP=请输入电视 IP: 
if "%TVIP%"=="" goto needip
goto do_status

:do_status
cls
"%ADB%" connect %TVIP%:5555 > nul
timeout /t 3 /nobreak > nul

rem ---- 连不上就直接给结论，不往下走 ----
"%ADB%" -s %TVIP%:5555 shell echo ok > nul 2>&1
if errorlevel 1 goto st_noconn

rem ---- 判断应用是否安装 ----
set "INSTALLED="
for /f "tokens=*" %%i in ('"%ADB%" -s %TVIP%:5555 shell pm list packages com.tclhotkey.redirect 2^>nul') do set "INSTALLED=%%i"
if not defined INSTALLED goto st_notinst

rem ---- 判断无障碍服务是否开启 ----
set "ACC="
for /f "tokens=*" %%i in ('"%ADB%" -s %TVIP%:5555 shell settings get secure enabled_accessibility_services 2^>nul') do set "ACC=%%i"
set "SVC=0"
echo "%ACC%" | findstr /i /c:"com.tclhotkey.redirect" > nul 2>&1
if not errorlevel 1 set "SVC=1"

rem ---- 读三个键当前的指向 ----
set "MAPFILE=%TEMP%\hotkey_map.txt"
"%ADB%" -s %TVIP%:5555 shell am start -n com.tclhotkey.redirect/com.tclhotkey.redirect.SetTargetActivity > nul
timeout /t 3 /nobreak > nul
"%ADB%" -s %TVIP%:5555 shell logcat -d -s HotkeyRedirect 2^>nul | findstr /c:"->" > "%MAPFILE%"
set "MAPN="
for /f %%n in ('type "%MAPFILE%" ^| find /c /v ""') do set "MAPN=%%n"
if not defined MAPN set "MAPN=0"

:st_show
cls
echo.
echo ============================================================
echo   当前状态
echo ============================================================
echo.
echo   应用安装情况：已安装
echo.
echo   按键服务状态：
if "%SVC%"=="1" echo         已开启 —— 遥控器那三个键已被接管
if "%SVC%"=="0" echo         未开启 —— 遥控器按键还是原厂行为
echo.
echo   三个键当前的指向：
if "%MAPN%"=="0" goto st_nomap
for /f "tokens=7,*" %%i in ('type "%MAPFILE%"') do echo         %%i %%j
goto st_mapdone
:st_nomap
echo         （没有读到映射记录）
:st_mapdone
echo.
echo ------------------------------------------------------------
if "%SVC%"=="1" goto st_yes

:st_no
echo   结论：应用装了，但按键服务没开，改键没有生效。
echo.
echo          现在按遥控器那三个键，还是电视原来的行为
echo          （比如哔哩哔哩键还是打开小电视）。
echo.
echo   怎么办：回到菜单选 [1] 开启按键服务，只要做一次。
echo.
echo          你之前设好的映射不会丢，开启后直接就生效，不用重新配。
echo ------------------------------------------------------------
echo.
pause
goto menu

:st_yes
echo   结论：按键服务正在运行，改键已经生效。
echo.
echo          上面「三个键当前的指向」就是现在按遥控器键的实际效果，
echo          改完立刻生效，不用重启电视。
echo.
echo   如果某个键按了没反应，多半是它指向的应用被卸载了，
echo   回到改键器界面重新给这个键选一个应用即可。
echo ------------------------------------------------------------
echo.
pause
goto menu

:st_notinst
cls
echo.
echo ============================================================
echo   当前状态
echo ============================================================
echo.
echo   结论：改键器还没有装到这台电视上。
echo.
echo          所以遥控器按键现在是电视原厂行为，没有任何改动。
echo.
echo   怎么办：先把 TVKeyMapper.apk 装到电视上（U 盘 + 电视自带
echo          文件管理器），装完再回到这个工具开启按键服务。
echo ============================================================
echo.
pause
goto menu

:st_noconn
cls
echo.
echo ============================================================
echo   当前状态
echo ============================================================
echo.
echo   结论：连不上这台电视，读不到状态。
echo.
echo   请检查三件事：
echo     1. 电视和电脑是不是连在同一个 WiFi 上
echo     2. 刚才输入的 IP 对不对（这次用的是 %TVIP%）
echo          电视 IP 在【设置】->【网络设置】里能看到
echo     3. 电视的 ADB 开关有没有打开
echo          路径：设置 - 关于 - 光标停在【本机信息】- 按 上下左右
echo.
echo          ADB 开关打开时电视会自动重启一次，没重启过就是没打开。
echo ============================================================
echo.
pause
goto menu
