@echo off
REM Coloca o ficheiro ic_launcher.png na mesma pasta que este script e corre-o.
REM Requer ImageMagick instalado: https://imagemagick.org
set SRC=ic_launcher.png
if not exist "%SRC%" ( echo Ficheiro %SRC% nao encontrado. && exit /b 1 )
if not exist "app\src\main\res\mipmap-ldpi" mkdir "app\src\main\res\mipmap-ldpi"
magick "%SRC%" -resize 36x36 "app\src\main\res\mipmap-ldpi\ic_launcher.png"
magick "%SRC%" -resize 36x36 "app\src\main\res\mipmap-ldpi\ic_launcher_round.png"
if not exist "app\src\main\res\mipmap-mdpi" mkdir "app\src\main\res\mipmap-mdpi"
magick "%SRC%" -resize 48x48 "app\src\main\res\mipmap-mdpi\ic_launcher.png"
magick "%SRC%" -resize 48x48 "app\src\main\res\mipmap-mdpi\ic_launcher_round.png"
if not exist "app\src\main\res\mipmap-hdpi" mkdir "app\src\main\res\mipmap-hdpi"
magick "%SRC%" -resize 72x72 "app\src\main\res\mipmap-hdpi\ic_launcher.png"
magick "%SRC%" -resize 72x72 "app\src\main\res\mipmap-hdpi\ic_launcher_round.png"
if not exist "app\src\main\res\mipmap-xhdpi" mkdir "app\src\main\res\mipmap-xhdpi"
magick "%SRC%" -resize 96x96 "app\src\main\res\mipmap-xhdpi\ic_launcher.png"
magick "%SRC%" -resize 96x96 "app\src\main\res\mipmap-xhdpi\ic_launcher_round.png"
if not exist "app\src\main\res\mipmap-xxhdpi" mkdir "app\src\main\res\mipmap-xxhdpi"
magick "%SRC%" -resize 144x144 "app\src\main\res\mipmap-xxhdpi\ic_launcher.png"
magick "%SRC%" -resize 144x144 "app\src\main\res\mipmap-xxhdpi\ic_launcher_round.png"
if not exist "app\src\main\res\mipmap-xxxhdpi" mkdir "app\src\main\res\mipmap-xxxhdpi"
magick "%SRC%" -resize 192x192 "app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"
magick "%SRC%" -resize 192x192 "app\src\main\res\mipmap-xxxhdpi\ic_launcher_round.png"
echo Icones gerados com sucesso.