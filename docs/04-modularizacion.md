# 04 — Modularización

## Principio

Modularizar tiene un costo real (build más complejo, navegación entre módulos,
ceremonia de DI). Se paga ese costo solo cuando compra algo concreto:
**build incremental más rápido** y **reglas de dependencia impuestas por el
compilador**.

Por eso la modularización es **gradual**, en tres etapas.

## Etapa 1 — Scaffolding del MVP

```
mi-plata-app/
├── build-logic/convention/      # convention plugins de Gradle
├── gradle/libs.versions.toml    # version catalog
├── app/                         # solo Application, MainActivity, NavHost, DI root
├── core/
│   ├── domain/                  # kotlin("jvm") ← SIN Android. La regla se impone aquí.
│   ├── data/                    # Room, DataStore, repos
│   ├── designsystem/            # tema M3, tipografía, color, componentes base
│   └── common/                  # utils, dispatchers, Result
└── feature/
    ├── resumen/                 # dashboard "¿me alcanza?"
    ├── plan/                    # editar ingresos y gastos del mes
    ├── transacciones/
    └── ajustes/                 # incluye backup/restore
```

Cinco a nueve módulos. Manejable desde el primer día, y ya da el beneficio
grande: `:core:domain` como módulo JVM puro.

## Etapa 2 — Cuando `:core:data` crezca

Partir por fuente de datos, no por feature:

```
core/
├── database/        # Room: entities, DAOs, migraciones
├── datastore/       # preferencias
└── data/            # repositorios (dependen de database y datastore)
```

## Etapa 3 — Solo si hace falta (probablemente nunca)

Partir cada feature en `:feature:x:ui` + `:feature:x:domain`. Tiene sentido con
un equipo grande y builds de varios minutos. Con un solo desarrollador, es
ceremonia pura. **Se documenta aquí para dejar claro que se consideró y se
descartó a conciencia** — no por desconocimiento.

## Reglas de dependencia

```
app ──▶ feature:* ──▶ core:domain
 │           │            ▲
 │           └──▶ core:designsystem
 │                        │
 └──────────▶ core:data ──┘
```

1. Un `feature` **jamás** depende de otro `feature`. Si dos features necesitan lo
   mismo, eso baja a `core`.
2. Solo `:app` conoce `:core:data`. Los features dependen de `:core:domain`
   (interfaces); Hilt inyecta las implementaciones en runtime.
3. `:core:domain` no depende de nada del proyecto.

Estas reglas se verifican en CI con un test de Konsist o con una tarea de Gradle
que inspeccione el grafo de dependencias. Una regla que no se verifica
automáticamente se rompe en tres semanas.

## Convención de paquetes dentro de un feature

```
feature/plan/src/main/kotlin/com/miplata/feature/plan/
├── navigation/     PlanRoute, planScreen(navGraphBuilder)
├── ui/             PlanScreen.kt, componentes, PlanUiState.kt
├── viewmodel/      PlanViewModel.kt
└── di/             PlanModule.kt (si hace falta)
```

## Nomenclatura

- **Código, tipos, nombres de archivo, ramas, commits, docs técnicos: inglés.**
  Es lo que hace el ecosistema y evita el spanglish (`getUsuarioById`).
- **Excepción deliberada:** los modelos de dominio usan los términos del negocio
  en español (`PlanMensual`, `LineaDePlan`, `Money.deCentavos`) porque son
  conceptos del dominio del usuario, y el lenguaje ubicuo vale más que la
  uniformidad. Está documentado en un ADR para que sea una decisión, no un
  accidente.
- **Textos visibles al usuario: siempre en `strings.xml`.** Nunca hardcodeados.
