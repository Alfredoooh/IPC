# Instituto Politécnico de Cabinda

## Informações do projeto

| Campo        | Valor                    |
|--------------|--------------------------|
| Package ID   | `com.nexa.ipc.app`            |
| Versão       | 1.0.0 (1) |
| Min SDK      | API 24            |
| Target SDK   | API 35         |
| Linguagem    | Kotlin                   |
| Developer    | Nexa               |

## Estrutura

```
app/
├── src/main/
│   ├── java/com/nexa/ipc/app/
│   │   └── MainActiviy.kt
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── values/
│   │   └── mipmap-*/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro
build.gradle.kts
settings.gradle.kts
codemagic.yaml
```

## Como começar

### 1. Gerar o keystore

```bash
# Linux / macOS
chmod +x generate_keystore.sh
./generate_keystore.sh
```

### 2. Abrir no Android Studio

Abre a pasta raiz do projeto no Android Studio (File > Open).

### 3. Build de debug

```bash
./gradlew assembleDebug
```

### 4. Build de release

```bash
./gradlew assembleRelease
```

## Dependências incluídas

- `androidx.core:core-ktx:1.13.1`
- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `testImplementation(junit:junit:4.13.2`
- `androidTestImplementation(androidx.test.ext:junit:1.2.1`
- `androidTestImplementation(androidx.test.espresso:espresso-core:3.6.1`

---

*Projeto gerado automaticamente por Android Project Generator*
