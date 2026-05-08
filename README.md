# IPC

## Informações do projeto

| Campo        | Valor                    |
|--------------|--------------------------|
| Package ID   | `com.nexa.ipc.app`            |
| Versão       | 1.0.0 (1) |
| Min SDK      | API 24            |
| Target SDK   | API 35         |
| Linguagem    | Kotlin                   |
| Developer    | Nexa               |

## Ícone

1. Coloca o ficheiro `ic_launcher.png` (mínimo 192×192 px) na raiz do projeto.
2. Corre o script de redimensionamento:

**Linux / macOS:**
```bash
chmod +x resize_icon.sh
./resize_icon.sh
```

**Windows:**
```
resize_icon.bat
```

## Keystore

```bash
chmod +x generate_keystore.sh
./generate_keystore.sh
```

Ver `KEYSTORE_README.md` para mais detalhes.

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## Dependências incluídas

- `androidx.core:core-ktx:1.13.1`
- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `com.github.bumptech.glide:glide:4.16.0`
- `testImplementation(junit:junit:4.13.2`
- `androidTestImplementation(androidx.test.ext:junit:1.2.1`
- `androidTestImplementation(androidx.test.espresso:espresso-core:3.6.1`

---
*Projeto gerado automaticamente por Android Project Generator*
