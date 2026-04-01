@echo off
title Tool Cap Quyen TileScan (V2)
color 0B

echo ======================================================
echo      TOOL CAP QUYEN TU DONG CHO TILESCAN (V2)
echo ======================================================
echo.

:: --- BƯỚC 1: TỰ TÌM ADB ---
:: Thu 1: Tim trong thu muc mac dinh cua Android Studio
set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools

:: Thu 2: Neu file nay duoc dat truc tiep trong thu muc platform-tools
if exist "adb.exe" (
    set ADB_PATH=%cd%
    echo [OK] Dang chay truc tiep tu thu muc hien tai.
    goto :FOUND
)

if exist "%ADB_PATH%\adb.exe" (
    cd /d "%ADB_PATH%"
    echo [OK] Da tim thay ADB trong thu muc cai dat mac dinh.
    goto :FOUND
)

:: Neu khong tim thay o dau ca
color 0C
echo [LOI] Khong tim thay ADB!
echo ------------------------------------------------------
echo GIAI PHAP:
echo Hay copy file .cmd nay bo vao ben trong thu muc "platform-tools"
echo (Noi co file adb.exe) roi chay lai.
echo ------------------------------------------------------
pause
exit

:FOUND
echo.
echo [1/3] Reset ADB Server...
adb kill-server
adb start-server
echo.

echo [2/3] Kiem tra thiet bi...
echo ------------------------------------------------------
adb devices
echo ------------------------------------------------------
echo LUY Y:
echo - Neu danh sach TRONG RON (List of devices attached...) -^> Rut cap cam lai.
echo - Neu thay chu "unauthorized" -^> Mo dien thoai bam "ALLOW/CHO PHEP".
echo - Neu thay chu "device" -^> OK, tiep tuc.
echo.
pause

echo.
echo [3/3] Dang cap quyen (Grant Permission)...
echo.

:: Chay lenh va luu ket qua vao file tam de kiem tra loi
:: LƯU Ý: Thay 'com.myapp' bằng package name thực tế của bạn
adb shell pm grant com.myapp android.permission.WRITE_SECURE_SETTINGS > temp_log.txt 2>&1

:: In ket qua thuc te ra man hinh de nguoi dung xem
type temp_log.txt

:: Quet xem log co chua chu "Exception" (bao loi bao mat) khong
findstr /i "Exception" temp_log.txt >nul

if %ERRORLEVEL% EQU 0 (
    :: Neu tim thay chu Exception -> Chuyen mau do, in loi
    color 0C
    echo.
    echo ======================================================
    echo        CO LOI XAY RA (FAILED)
    echo ======================================================
    echo [!] LUY Y KHI FAIL:
    echo 1. Kiem tra phien ban App: ADB thuong xuyen chan cap quyen neu ban dang chay ban Debug truc tiep tu Android Studio. Hay Build ra ban Release (APK) roi cai dat lai.
    echo 2. Kiem tra he thong: Vao Tuy chon nha phat trien (Developer Options) va chac chan rang muc "Disable permission monitoring" (Tat theo doi quyen) da duoc bat.
    echo.
) else (
    :: Neu khong co chu Exception -> Chuyen mau xanh la, bao thanh cong
    color 0A
    echo.
    echo ======================================================
    echo        DA CAP QUYEN THANH CONG! (SUCCESS)
    echo ======================================================
    echo Ban co the mo app TileScan va thu bam nut Extra Dim.
)

:: Xoa file log tam
del temp_log.txt
echo.
pause