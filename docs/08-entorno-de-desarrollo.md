# 08 — Entorno de desarrollo

Estado: **el proyecto compila**. Verificado el 2026-09-13 con
`./gradlew :app:assembleDebug`, `spotlessCheck` y `detekt` en verde.

## Requisitos

| Pieza | Versión | Nota |
|---|---|---|
| Android Studio | 2026.1+ | Trae el SDK y el emulador |
| JDK | **21** | `brew install openjdk@21` |
| Android SDK Platform | 36 | AGP lo descarga solo |
| Build Tools | 35.0.0 | AGP las descarga solas |
| Gradle | 8.14.3 | Vía el wrapper del repo, no hace falta instalarlo |

### Por qué JDK 21 y no el JBR de Android Studio

Android Studio 2026.1 incluye **JBR 25**, y en la máquina también hay un
OpenJDK 26 de Homebrew. **Ninguno de los dos sirve** para AGP 8.x. Por eso se
instala un JDK 21 aparte:

```bash
brew install openjdk@21
```

Se usa la fórmula `openjdk@21`, no el cask `temurin@21`: la fórmula no necesita
permisos de administrador.

Para la terminal:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

En Android Studio: **Settings → Build, Execution, Deployment → Build Tools →
Gradle → Gradle JDK** → seleccionar el 21.

> El build declara su toolchain en `libs.versions.toml` (`javaToolchain = "21"`)
> y `settings.gradle.kts` aplica el *foojay resolver*, así que en una máquina sin
> JDK 21 Gradle lo descarga solo. El `JAVA_HOME` de arriba es solo para arrancar
> Gradle, no para compilar.

### No instales Gradle globalmente

Se usó una vez para generar el wrapper y ya no hace falta. Todo se invoca con
`./gradlew`. De hecho el Gradle 9.7.1 de Homebrew **no puede** construir este
proyecto: Gradle 9.6 eliminó una API interna que AGP 8.x todavía usa. El wrapper
existe precisamente para que la versión de Gradle no dependa de la máquina.

Si quieres liberar el espacio: `brew uninstall gradle`.

## Trampas del entorno que ya costaron tiempo

Documentadas para no volver a tropezar:

1. **`compileSdk = 37` no funciona con AGP 8.12.** El SDK instala la plataforma
   como `android-37.0` (el nuevo esquema con versión menor) pero AGP 8.x busca
   `android-37` literal y falla con *"Failed to find target with hash string"*.
   El proyecto usa `compileSdk = 36`. Subir a 37 exige AGP 9.
2. **El `rm` de la shell es interactivo** (`rm -i`): en scripts, usar `/bin/rm -f`
   o se queda esperando confirmación.

## Comandos

```bash
./gradlew :app:assembleDebug        # APK de debug
./gradlew spotlessApply             # formatear (automático en pre-commit)
./gradlew spotlessCheck detekt      # puertas de calidad
./gradlew :core:domain:test         # tests del dominio, sin emulador
```

## La comprobación que no se debe perder

Lo que demuestra que la arquitectura la sujeta el compilador y no la buena
voluntad. Añadir esto a `:core:domain`:

```kotlin
import android.content.Context
```

debe fallar con:

```
e: Unresolved reference 'android'.
```

Verificado. Si algún día eso compila, `:core:domain` dejó de ser un módulo
Kotlin/JVM puro y hay que revisar `docs/01-arquitectura.md`.

> Esta comprobación se automatizará como test en la Etapa 0.2, junto con el resto
> de CI.
