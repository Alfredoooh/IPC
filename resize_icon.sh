#!/bin/bash
# Coloca o ficheiro ic_launcher.png na mesma pasta que este script e corre-o.
# Requer ImageMagick instalado: https://imagemagick.org
SRC="ic_launcher.png"
if [ ! -f "$SRC" ]; then echo "❌ Ficheiro $SRC não encontrado."; exit 1; fi
mkdir -p app/src/main/res/mipmap-ldpi
convert "$SRC" -resize 36x36 app/src/main/res/mipmap-ldpi/ic_launcher.png
convert "$SRC" -resize 36x36 app/src/main/res/mipmap-ldpi/ic_launcher_round.png
mkdir -p app/src/main/res/mipmap-mdpi
convert "$SRC" -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.png
convert "$SRC" -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher_round.png
mkdir -p app/src/main/res/mipmap-hdpi
convert "$SRC" -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.png
convert "$SRC" -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher_round.png
mkdir -p app/src/main/res/mipmap-xhdpi
convert "$SRC" -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.png
convert "$SRC" -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher_round.png
mkdir -p app/src/main/res/mipmap-xxhdpi
convert "$SRC" -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.png
convert "$SRC" -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
mkdir -p app/src/main/res/mipmap-xxxhdpi
convert "$SRC" -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
convert "$SRC" -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
echo "✅ Ícones gerados para todos os DPIs."