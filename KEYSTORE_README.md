# Keystore de Release

## Como gerar o keystore

**Linux / macOS:**
```bash
chmod +x generate_keystore.sh
./generate_keystore.sh
```

**Windows:**
```
generate_keystore.bat
```

## Credenciais

| Campo          | Valor              |
|----------------|--------------------|
| Alias          | `IPC`        |
| Key password   | `@jonas00`      |
| Store password | `@jonas00`    |
| Validade       | 100 anos   |
| Ficheiro       | `app/release.keystore` |

## ⚠️ Importante

- **Faz SEMPRE backup** do ficheiro `app/release.keystore`
- **Nunca** commites o keystore para repositórios públicos (já está no `.gitignore`)
- Sem este ficheiro não consegues publicar atualizações na Google Play
