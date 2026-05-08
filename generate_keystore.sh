#!/bin/bash
# Corre este script UMA VEZ para gerar o keystore de release
# Requer Java (keytool) instalado

keytool -genkeypair \
  -v \
  -alias "IPC" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 36500 \
  -keystore app/release.keystore \
  -storepass "@jonas00" \
  -keypass "@jonas00" \
  -dname "CN=Nexa, OU=Dev, O=Nexa, L=Lisboa, ST=Lisboa, C=AO"

echo ""
echo "✅ Keystore gerado em app/release.keystore"
echo "   Alias:          IPC"
echo "   Key password:   @jonas00"
echo "   Store password: @jonas00"
echo ""
echo "⚠️  Faz backup deste ficheiro — sem ele não consegues fazer update da app na Play Store!"