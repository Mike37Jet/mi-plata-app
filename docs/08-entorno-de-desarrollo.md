# 08 — Entorno de desarrollo

Estado: **el proyecto compila**. Verificado el 2026-09-13 con
`./gradlew :app:assembleDebug`, `spotlessCheck` y `detekt` en verde.

## Requisitos

| Pieza | Versión | Nota |
|---|---|---|
| Android Studio | 2026.1+ | Trae el SDK y el emulador |
| JDK | **21** | Android Studio lo descarga solo al abrir el proyecto |
| Android SDK Platform | 36 | AGP lo descarga solo |
| Build Tools | 35.0.0 | AGP las descarga solas |
| Gradle | 8.14.3 | Vía el wrapper del repo, no hace falta instalarlo |

### Por qué JDK 21

Android Studio 2026.1 se ejecuta sobre **JBR 25**, y la máquina puede tener
además un OpenJDK 26. **Ninguno de los dos sirve** para compilar con AGP 8.x,
que necesita 17 o 21.

**No hay que hacer nada:** al abrir el proyecto, Android Studio detecta el
toolchain que declara el build y se descarga un **JBR 21** aparte, que deja en
`~/Library/Java/JavaVirtualMachines/jbr-21.x/`. Luego apunta Gradle ahí mediante
`.gradle/config.properties` (fuera de git, porque es específico de cada máquina).

Para compilar desde la terminal hay que darle un JDK 21 explícito. Cualquiera de
los dos sirve — ambos verificados en verde:

```bash
# el que ya descargó Android Studio
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home

# o uno propio, si prefieres no depender de Studio
brew install openjdk@21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

Se usa la fórmula `openjdk@21` y no el cask `temurin@21` porque la fórmula no
pide permisos de administrador.

> El toolchain se declara en `libs.versions.toml` (`javaToolchain = "21"`) y
> `settings.gradle.kts` aplica el *foojay resolver*, así que en una máquina sin
> JDK 21 Gradle lo descarga solo. El `JAVA_HOME` de arriba solo sirve para
> **arrancar** Gradle, no para compilar: eso lo decide el toolchain.

### No instales Gradle globalmente

Se usó una vez para generar el wrapper y ya no hace falta. Todo se invoca con
`./gradlew`. De hecho el Gradle 9.7.1 de Homebrew **no puede** construir este
proyecto: Gradle 9.6 eliminó una API interna que AGP 8.x todavía usa. El wrapper
existe precisamente para que la versión de Gradle no dependa de la máquina.

Si quieres liberar el espacio: `brew uninstall gradle`.

## Trampas del entorno que ya costaron tiempo

Documentadas para no volver a tropezar:

1. **`compileSdk = 37` no funciona con AGP 8.x.** El SDK instala la plataforma
   como `android-37.0` (el nuevo esquema con versión menor) pero AGP 8.x busca
   `android-37` literal y falla con *"Failed to find target with hash string"*.
   El proyecto usa `compileSdk = 36`. Subir a 37 exige AGP 9.
2. **El `rm` de la shell es interactivo** (`rm -i`): en scripts, usar `/bin/rm -f`
   o se queda esperando confirmación.
3. **Android Studio edita `libs.versions.toml` por su cuenta.** Su asistente de
   actualización sube versiones (subió AGP de 8.12.0 a 8.13.2 en el primer
   sync). No es malo, pero **revisa el diff antes de commitear**: `git add -A`
   sin mirar mete cambios que no son tuyos en un commit que dice otra cosa.
4. **`.idea/` y `local.properties` están ignorados** y así deben quedarse: son
   específicos de cada máquina. Android Studio los regenera al abrir el proyecto,
   igual que `.gradle/config.properties`.

## Comandos

```bash
./gradlew :app:assembleDebug        # APK de debug
./gradlew spotlessApply             # formatear (automático en pre-commit)
./gradlew spotlessCheck detekt      # puertas de calidad
./gradlew :core:domain:test         # tests del dominio, sin emulador
```

Para reproducir en local exactamente lo que hará CI, incluidos los warnings
como errores:

```bash
CI=true ./gradlew spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug
```

## Hooks de git

Los hooks viven en `.githooks/` y están versionados. Git no los activa solo al
clonar, así que **el propio build los instala** en su primera ejecución sobre un
clon nuevo (ver `settings.gradle.kts`). No hay nada que hacer a mano.

Si alguna vez necesitas activarlos o comprobarlos sin construir:

```bash
git config core.hooksPath .githooks   # activar
git config --get core.hooksPath       # comprobar
```

Se omite cuando la variable `CI` está definida: en CI la verificación la hace el
workflow, no los hooks.

| Hook | Qué hace |
|---|---|
| `pre-commit` | `spotlessApply` sobre los archivos staged |
| `commit-msg` | valida Conventional Commits |
| `pre-push` | tests de `:core:domain` |

Son shell puro, sin Node ni dependencias: `commitlint` habría exigido meter npm
en un proyecto que no tiene JavaScript por ninguna parte.

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

> **Ya automatizado.** Desde la Etapa 0.2 no hace falta comprobarlo a mano: si
> alguien aplica un plugin de Android a `:core:domain`, el build falla durante la
> configuración con un mensaje que explica la regla. Lo mismo con las
> dependencias prohibidas entre `:feature:*`. Ver `ArchitectureRules.kt` en
> `build-logic`.
