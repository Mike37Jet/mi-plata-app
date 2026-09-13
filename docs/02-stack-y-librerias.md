# 02 — Stack y librerías

> **Sobre las versiones:** este documento fija **qué** librería y **por qué**.
> Los números viven en `gradle/libs.versions.toml` y se mantienen con Renovate,
> para que este documento no envejezca. Las combinaciones verificadas y las
> incompatibilidades conocidas del entorno están en
> [docs/08](08-entorno-de-desarrollo.md).

## Base

| Área | Elección | Por qué |
|---|---|---|
| Lenguaje | **Kotlin** (última estable, K2) | Obvio en Android hoy. `value class`, `sealed`, coroutines. |
| UI | **Jetpack Compose + Material 3** | Declarativo, encaja con UDF, adiós XML. M3 trae dynamic color. |
| Build | **Gradle Kotlin DSL + Version Catalog + Convention Plugins** | Ver sección Build. |
| Min SDK | **26** (Android 8.0) | Cubre >95% del parque; `java.time` entra vía desugaring. |
| Target SDK | **36** | Limitado por AGP 8.x: la plataforma 37 usa el nuevo esquema `android-37.0` que AGP 8 no resuelve (ver docs/08). Subir exige AGP 9. |
| Java toolchain | **21** | Declarado en el catálogo y auto-aprovisionado con el foojay resolver, para que no dependa del JDK de cada máquina. |

## Librerías por responsabilidad

### Inyección de dependencias — **Hilt**
Estándar de facto en Android nativo, generación en tiempo de compilación
(errores en build, no en runtime), integración directa con `ViewModel`,
`WorkManager` y `NavHost`.

*Alternativa considerada:* **Koin** — más simple y multiplataforma, pero resuelve
en runtime; un grafo mal armado explota en producción, no al compilar. Para un
proyecto donde el objetivo declarado es *aplicar ingeniería*, la verificación en
compile-time gana.

### Persistencia — **Room** (+ SQLite)
- API de Kotlin con coroutines y `Flow` nativos.
- Verificación de las queries SQL **en tiempo de compilación**.
- Migraciones versionadas con `Migration` y esquemas exportados a JSON
  (`room.schemaLocation`) y **commiteados al repo** → los tests de migración
  pueden verificar cada salto de versión. Esto es innegociable en una app de
  finanzas: perder datos del usuario no es un bug aceptable.

*Alternativa considerada:* **SQLDelight** — SQL primero, multiplataforma, muy
elegante. Se descarta solo porque el proyecto es Android puro y Room tiene mejor
integración con el resto de Jetpack.

### Cifrado en reposo — **SQLCipher for Android** + **Android Keystore**
La base de datos va cifrada. La clave se genera en el Keystore respaldado por
hardware y **nunca** toca el disco en claro ni el código fuente.

### Preferencias — **DataStore (Proto)**
Reemplazo de `SharedPreferences`. Se usa Proto (no Preferences) por tipado
fuerte. Guarda: moneda, tema, fecha de último backup, primer día del mes
financiero.

### Navegación — **Navigation Compose type-safe** (rutas como `@Serializable`)
Rutas como objetos de Kotlin, no strings. Renombrar una pantalla es un refactor
del IDE, no un find-and-replace que se rompe en silencio.

*Nota:* Navigation 3 (back stack como estado observable) es muy prometedor para
esta app; se evaluará como migración cuando sea estable. Se registrará un ADR.

### Serialización — **kotlinx.serialization**
Para el backup JSON y para las rutas de navegación. Sin reflexión, sin reglas de
R8. Moshi/Gson no aportan nada aquí.

### Fechas — **kotlinx-datetime**
Multiplataforma, inmutable, y `LocalDate`/`YearMonth` encajan con `domain` puro.

### Trabajo en background — **WorkManager**
Recordatorio periódico de backup y generación de transacciones recurrentes.
Sobrevive a reinicios y a Doze.

### Gráficas — **Vico**
Nativo de Compose, sin `AndroidView` envuelto, soporta theming M3 y modo oscuro.

*Alternativa considerada:* MPAndroidChart — potente pero es una View clásica,
sin mantenimiento activo, y obliga a un `AndroidView` con estado imperativo en
medio de un árbol Compose.

### Biometría — **androidx.biometric**
Bloqueo de la app con huella / PIN del dispositivo.

### Imágenes — **Coil 3**
Solo si se agregan adjuntos de recibos. No entra en el MVP.

## Testing

| Nivel | Herramienta | Objetivo |
|---|---|---|
| Unitario (domain) | **JUnit 5 + Kotest assertions** | Los use cases de cálculo. Meta: **>90% de cobertura en `:core:domain`**. |
| Coroutines/Flow | **Turbine** + `kotlinx-coroutines-test` | Verificar emisiones de `Flow` sin `Thread.sleep`. |
| Dobles de prueba | **Fakes escritos a mano** > MockK | Un `FakeTransaccionRepository` en memoria es más legible y más robusto que un mock con 8 `every {}`. MockK solo donde el fake no valga la pena. |
| Data | Room **in-memory** + tests de **migración** | Cada migración se prueba con datos reales. |
| UI | **Compose UI Test** (`createAndroidComposeRule`) | Flujos críticos: crear plan, registrar gasto, restaurar backup. |
| Screenshot | **Roborazzi** (Robolectric) | Detecta regresiones visuales en CI sin emulador. Opcional, v0.2+. |

**Regla:** la pirámide es ancha abajo. Cientos de tests de dominio rápidos, unos
pocos de UI para los caminos felices críticos. No al revés.

## Calidad de código

- **ktlint** vía **Spotless** — formato automático, no se discute en PRs.
- **detekt** — complejidad ciclomática, code smells, funciones gigantes.
- **Android Lint** con `warningsAsErrors` en CI.
- Todos corren en el hook de pre-commit *y* en CI. El hook es conveniencia; CI es
  la verdad.

## Build: convention plugins

Con varios módulos, copiar 60 líneas de `android { }` en cada `build.gradle.kts`
es deuda técnica inmediata. Se usa `build-logic/` con plugins propios:

```
build-logic/
└── convention/
    ├── AndroidApplicationConventionPlugin.kt
    ├── AndroidLibraryConventionPlugin.kt
    ├── AndroidComposeConventionPlugin.kt
    ├── AndroidHiltConventionPlugin.kt
    └── JvmLibraryConventionPlugin.kt
```

Cada módulo queda así:

```kotlin
plugins {
    alias(libs.plugins.miplata.android.library.compose)
    alias(libs.plugins.miplata.android.hilt)
}
```

Configurar el toolchain de Java o subir el targetSdk pasa a ser **un cambio en un
archivo**. Es el patrón de Now in Android (app de referencia oficial de Google) y
es probablemente la pieza de ingeniería con mejor retorno de todo el proyecto.

## Rendimiento (desde temprano, no al final)

- **Baseline Profiles** generados con Macrobenchmark → mejora real de arranque.
- **R8 full mode** + shrinking de recursos en release.
- **Compose compiler metrics** activadas para detectar composables inestables.
- Nunca `LazyColumn` sin `key` estable.
