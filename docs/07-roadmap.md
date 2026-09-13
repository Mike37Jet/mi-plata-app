# 07 — Roadmap de construcción

Orden pensado para que **cada etapa deje algo ejecutable y testeado**. Nada de
construir tres capas durante dos semanas sin poder abrir la app.

## Etapa 0 — Fundaciones (sin UI todavía)

| # | Entregable | Rama |
|---|---|---|
| 0.1 | ✅ Proyecto Gradle, version catalog, convention plugins, `:app` vacío que arranca | `chore/project-scaffolding` |
| 0.2 | ✅ Spotless + detekt + hooks + reglas de arquitectura + workflow `pr.yml` | `chore/quality-gates` |
| 0.3 | ✅ Repositorio publicado, `main` protegido, plantillas de issue/PR, validación de commits, Renovate configurado | `chore/repo-governance` |
| 0.4 | `:core:designsystem` — tema M3, color, tipografía, dynamic color, modo oscuro | `feat/design-system` |

**Criterio de salida:** un PR que rompe el formato o los tests es **bloqueado
por CI**, verificado a propósito con un PR de prueba.

## Etapa 1 — El dominio primero (la parte más valiosa)

| # | Entregable | Rama |
|---|---|---|
| 1.1 | ✅ `Money` como `value class` + su suite de tests | `feat/money-value-type` |
| 1.2 | ✅ Modelos: `Mes`, `Cuenta`, `Categoria`, `Transaccion`, `PlanMensual`, `LineaDePlan` | `feat/domain-models` |
| 1.3 | Interfaces de repositorio + fakes en memoria | `feat/domain-repositories` |
| 1.4 | ✅ `CalcularResumenMensualUseCase` + **todos** los casos borde de `03` | `feat/monthly-summary-usecase` |
| 1.5 | ✅ `MaterializarPlanDelMesUseCase` (copiar del mes anterior) | `feat/plan-materialization` |

**Criterio de salida:** `:core:domain` con >90% de cobertura, corriendo en <2s,
sin una sola dependencia de Android. La lógica financiera está probada **antes**
de que exista una pantalla.

## Etapa 2 — Persistencia

| # | Entregable | Rama |
|---|---|---|
| 2.1 | Room: entities, DAOs, esquema v1 exportado y commiteado | `feat/room-database` |
| 2.2 | SQLCipher + clave en Android Keystore | `feat/database-encryption` |
| 2.3 | Implementaciones de repositorio + mappers + tests con DB in-memory | `feat/data-repositories` |
| 2.4 | Módulos de Hilt, DataStore Proto de preferencias | `feat/di-and-preferences` |
| 2.5 | Seed de categorías por defecto en la primera ejecución | `feat/default-categories` |

## Etapa 3 — MVP usable

| # | Entregable | Rama |
|---|---|---|
| 3.1 | NavHost type-safe + scaffold con bottom bar | `feat/navigation` |
| 3.2 | **Pantalla Plan** — listas de ingresos / fijos / variables, editar en línea | `feat/plan-editor` |
| 3.3 | **Pantalla Resumen** — disponible, sobregiro, progreso del mes | `feat/monthly-summary-screen` |
| 3.4 | Pantalla Cuentas + CRUD | `feat/accounts-screen` |
| 3.5 | Pantalla Transacciones + alta rápida | `feat/transactions-screen` |
| 3.6 | Tests de UI de los 3 flujos críticos | `test/critical-ui-flows` |

**Aquí la app ya responde "¿me alcanza?" y es usable a diario.**

## Etapa 4 — Backup (el requisito que no puede esperar)

| # | Entregable | Rama |
|---|---|---|
| 4.1 | Serialización del dominio a `data.json` + manifest + checksum | `feat/backup-serialization` |
| 4.2 | Cifrado AES-256-GCM con frase de respaldo | `feat/backup-encryption` |
| 4.3 | Export vía SAF (`ACTION_CREATE_DOCUMENT`) | `feat/backup-export` |
| 4.4 | Import con preview, backup de seguridad previo y rollback atómico | `feat/backup-restore` |
| 4.5 | `WorkManager` con recordatorio periódico | `feat/backup-reminder` |
| 4.6 | Tests de round-trip y de migración de formato | `test/backup-roundtrip` |

> Se coloca temprano a propósito. Cuanto más tarde llegue el backup, más datos
> reales hay en riesgo — y este es un requisito explícito tuyo, no un extra.

## Etapa 5 — v0.2 Control

Presupuestos por categoría con alertas · metas de ahorro · recurrentes que
generan transacciones sugeridas · bloqueo biométrico · widget de pantalla de
inicio (Glance).

## Etapa 6 — v0.3 Análisis

Dashboard con Vico · gastos por categoría · tendencia mensual · flujo de caja
proyectado · comparativa mes a mes · deudas con amortización · tarjetas con
fecha de corte · exportación a CSV.

## Etapa 7 — Pulido para publicar

Baseline Profiles · R8 full mode · accesibilidad (TalkBack, tamaños de fuente,
contraste) · onboarding · ficha de Play Store · política de privacidad
("tus datos nunca salen del dispositivo" — y es verdad, ver `05`).

---

## Cómo se trabaja cada ítem

1. Issue en GitHub, con criterios de aceptación.
2. `git switch -c feat/xxx` desde `main` actualizado.
3. **Test primero** en todo lo que sea `domain`. Ahí TDD paga de verdad porque
   las reglas son puras y los casos borde son muchos.
4. Commits pequeños y convencionales.
5. PR → CI verde → squash merge → borrar rama.
6. Si la decisión fue arquitectónica → ADR en el mismo PR.
