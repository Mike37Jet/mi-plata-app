# mi-plata-app

App Android de finanzas personales, **offline-first**, centrada en responder una
sola pregunta antes que ninguna otra: **"¿me alcanza este mes?"**

> Estado: **scaffolding, compilando**. El esqueleto de Gradle y los módulos ya
> existen y `./gradlew :app:assembleDebug` pasa; la lógica de la app aún no.
> Para montar el entorno: [docs/08](docs/08-entorno-de-desarrollo.md).

## La idea

La mayoría de apps de finanzas te obligan a registrar cada gasto para enterarte
al final del mes de que te pasaste. `mi-plata-app` separa **el plan** (mis
ingresos, mis gastos fijos, mis gastos variables) de **la realidad** (lo que de
verdad ocurrió), y calcula saldos y sobregiros cruzando ambos.

Se puede usar sin registrar una sola transacción.

## Stack

Kotlin · Jetpack Compose + Material 3 · Clean Architecture (3 capas) + MVVM/UDF ·
Room + SQLCipher · Hilt · Coroutines/Flow · WorkManager · Navigation type-safe ·
Vico · Gradle KTS con convention plugins.

Sin backend. Sin cuentas de usuario. Sin permiso de INTERNET.

## Documentación

| Doc | Contenido |
|---|---|
| [00 — Visión y alcance](docs/00-vision-y-alcance.md) | El problema, la idea Plan vs. Realidad, qué entra y qué no |
| [01 — Arquitectura](docs/01-arquitectura.md) | Capas, regla de dependencia, UDF, manejo de errores, anti-overengineering |
| [02 — Stack y librerías](docs/02-stack-y-librerias.md) | Qué librería, por qué, y qué se descartó. Testing, calidad, build |
| [03 — Modelo de dominio](docs/03-modelo-de-dominio.md) | `Money`, plan mensual, transacciones, cuentas, casos borde |
| [04 — Modularización](docs/04-modularizacion.md) | Estructura de módulos en 3 etapas y reglas de dependencia |
| [05 — Backup y restauración](docs/05-backup-restore.md) | Formato, cifrado, SAF, restauración atómica |
| [06 — Git y flujo de trabajo](docs/06-git-y-flujo-de-trabajo.md) | Trunk-based, Conventional Commits, CI/CD, protección de `main` |
| [07 — Roadmap](docs/07-roadmap.md) | Etapas de construcción, cada una con entregable ejecutable |
| [ADRs](docs/adr/) | Registro de decisiones arquitectónicas |
| [08 — Entorno de desarrollo](docs/08-entorno-de-desarrollo.md) | Qué instalar, versiones verificadas y trampas del entorno |

## Convenciones de contribución

Reglas que aplican a **todo** commit de este repositorio, sin excepción.

### Mensajes de commit — Conventional Commits

```
<tipo>(<ámbito>): <descripción en imperativo, minúscula, sin punto final>
```

Tipos: `feat` · `fix` · `refactor` · `chore` · `docs` · `test` · `perf`
Ámbitos: `plan` · `transacciones` · `resumen` · `cuentas` · `backup` · `domain` ·
`data` · `ui` · `build` · `ci`

```
feat(plan): materializar plan del mes desde el mes anterior
fix(money): usar HALF_EVEN al convertir decimales a centavos
```

El cuerpo explica el **por qué**; el qué ya está en el diff. Se valida en CI con
`commitlint`. De aquí salen el CHANGELOG y el versionado semántico
automáticamente, así que no es cosmética.

### Autoría

**Los commits llevan un solo autor: el humano que los hace.**

No se añaden líneas `Co-Authored-By:` de asistentes de IA ni ninguna otra forma
de atribución automática, ni en los commits ni en las descripciones de PR. Si
usas una herramienta de IA para escribir código, el trabajo —y la
responsabilidad sobre él— sigue siendo de quien firma el commit.

Ver también [docs/06 — Git y flujo de trabajo](docs/06-git-y-flujo-de-trabajo.md).

## Principios

1. **Offline-first absoluto.** La app nunca depende de la red.
2. **Los datos son del usuario.** Backup abierto, documentado y portable.
3. **Editable siempre**, sin reescribir la historia.
4. **Cero dinero perdido por redondeo.** El dinero nunca es un `Double`.
