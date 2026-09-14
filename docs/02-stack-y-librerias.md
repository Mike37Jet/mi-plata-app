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

### Preferencias — **DataStore + kotlinx.serialization**
Reemplazo de `SharedPreferences`. Guarda moneda, tema, fecha del último backup y
primer día del mes financiero.

**Se descartó Proto DataStore**, que era la elección original de este documento.
El motivo que se daba para Proto era el tipado fuerte, y eso ya lo da un
`data class` serializable: no hace falta añadir el plugin de protobuf, ni
`protoc`, ni un esquema `.proto`, ni código generado, para cuatro preferencias.
Además el proyecto ya usa kotlinx.serialization para el backup (docs/05), así que
son **un** mecanismo de serialización y no dos.

Lo que sí se conserva de la idea original es lo importante: el almacén está
tipado, no es un saco de claves y strings.

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
| Unitario (domain) | **JUnit 5 + Kotest assertions** | Los use cases de cálculo. |
| Cobertura | **Kover** | Solo en `:core:domain`, con un suelo del **90%** que CI hace cumplir. Un suelo para que una regresión se note, no un objetivo que perseguir con tests de relleno. En los módulos de UI no se exige: allí el porcentaje empuja a escribir tests de humo. |
| Coroutines/Flow | **Turbine** + `kotlinx-coroutines-test` | Verificar emisiones de `Flow` sin `Thread.sleep`. |
| Dobles de prueba | **Fakes escritos a mano** > MockK | Un `FakeTransaccionRepository` en memoria es más legible y más robusto que un mock con 8 `every {}`. MockK solo donde el fake no valga la pena. |
| Data | Room **in-memory** + tests de **migración** | Cada migración se prueba con datos reales. |
| UI | **Compose UI Test + Robolectric** (`createComposeRule`) | Flujos críticos: armar el plan, anotar un movimiento, ver si me alcanza. Corren **en la JVM**, en cada PR, por la misma razón que los de Room: un test que necesite un emulador acaba sin ejecutarse nunca. Ver las dos trampas del entorno más abajo. |
| Instrumentado | **androidTest en emulador o móvil** | Lo que la JVM no puede probar: que la base quede **realmente cifrada**. SQLCipher usa librerías nativas que Robolectric no carga. **No corre en CI** —sí se compila, para que no se pudra— y se ejecuta a mano con `./gradlew :core:data:connectedDebugAndroidTest`. |
| Screenshot | **Roborazzi** (Robolectric) | Detecta regresiones visuales en CI sin emulador. Opcional, v0.2+. |

**Regla:** la pirámide es ancha abajo. Cientos de tests de dominio rápidos, unos
pocos de UI para los caminos felices críticos. No al revés.

### Dos trampas de los tests de UI con Robolectric

Las dos hacen que un test pase **sin haber probado nada**, que es peor que un
test en rojo. Ambas están documentadas en el código, en los tests de flujo:

1. **Una hoja modal es otra ventana.** `ModalBottomSheet` se dibuja en una
   ventana aparte y, bajo Robolectric, un `performClick()` —que toca unas
   coordenadas— no se enruta a ella: el gesto se pierde en silencio. Dentro de
   una hoja hay que invocar la acción por semántica
   (`performSemanticsAction(SemanticsActions.OnClick)`). A cambio se prueba el
   cableado pantalla-ViewModel-dominio pero **no** el gesto físico, que se
   comprueba a mano en el emulador.
2. **Pulsar antes de tiempo no hace nada.** Un botón que solo se habilita
   cuando el estado ha llegado a la pantalla —Guardar, por ejemplo— ignora la
   pulsación si se hace antes, y el test sigue adelante creyendo que guardó.
   Hay que esperar a que el control esté habilitado, no solo a que exista.

Y una consecuencia menor: la ventana de Robolectric es pequeña, así que lo que
cae fuera de una `LazyColumn` no se compone. Para una fila que solo importa que
esté en la lista, `assertExists()` dice la verdad; `assertIsDisplayed()` ataría
el test al tamaño de la ventana.

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
