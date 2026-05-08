@echo off
keytool -genkeypair ^
  -v ^
  -alias "IPC" ^
  -keyalg RSA ^
  -keysize 4096 ^
  -validity 36500 ^
  -keystore app\release.keystore ^
  -storepass "@jonas00" ^
  -keypass "@jonas00" ^
  -dname "CN=Nexa, OU=Dev, O=Nexa, L=Lisboa, ST=Lisboa, C=AO"
echo.
echo Keystore gerado em app\release.keystore
pause