# 06 — Git, ramas y flujo de trabajo

## Decisión: Trunk-Based Development con ramas cortas

**No GitFlow.** GitFlow (`develop` + `release/*` + `hotfix/*` + `master`) se
diseñó en 2010 para software con releases versionados y varios equipos en
paralelo. En una app móvil de un solo desarrollador produce merges constantes
entre `develop` y `main` que no aportan nada y sí generan conflictos.

### El modelo

```
main ───●───●───●───●───●───●──▶   siempre desplegable, protegida
         \     /     \   /
          ●───●       ●─●          feature/*  (vida: horas a 3 días)
```

- **`main`** es la única rama de larga vida. Siempre compila, siempre pasa tests,
  siempre es publicable.
- Cada cambio nace en una rama corta desde `main`, se integra por **Pull Request**,
  y se borra.
- **Regla de oro: si una rama vive más de 3 días, el cambio estaba mal partido.**
- Las releases se marcan con **tags** (`v0.1.0`), no con ramas.
- Un hotfix es simplemente `fix/*` → `main` → tag de patch. No hace falta un
  proceso especial.

## Convención de ramas

```
<tipo>/<descripcion-corta-en-kebab-case>
```

| Tipo | Uso |
|---|---|
| `feat/` | funcionalidad nueva → `feat/monthly-plan-editor` |
| `fix/` | corrección → `fix/negative-balance-rounding` |
| `refactor/` | sin cambio de comportamiento |
| `chore/` | build, deps, CI |
| `docs/` | documentación |
| `test/` | solo tests |
| `perf/` | rendimiento |

## Conventional Commits (obligatorio)

```
<tipo>(<ámbito>): <descripción en imperativo, minúscula, sin punto final>

[cuerpo: el POR QUÉ, no el qué — el qué ya está en el diff]

[footer: BREAKING CHANGE / Closes #12]
```

Ejemplos:

```
feat(plan): materializar plan del mes desde el mes anterior
fix(money): usar HALF_EVEN al convertir decimales a centavos
refactor(domain): extraer CalcularResumenMensualUseCase del ViewModel
chore(deps): actualizar Compose BOM
```

Ámbitos válidos: `plan`, `transacciones`, `resumen`, `cuentas`, `backup`,
`domain`, `data`, `ui`, `build`, `ci`.

### Autoría de los commits

Un commit, un autor: el humano que lo hace. **No se añaden trailers
`Co-Authored-By:` de asistentes de IA** ni en commits ni en descripciones de PR.
La herramienta con la que se escribió el código no es un coautor; la
responsabilidad sobre el cambio es de quien firma.

**Por qué importa:** habilita CHANGELOG automático, versionado semántico
automático y `git log --oneline` legible de verdad. Se valida en CI con
`commitlint`, no a ojo.

## Puesta en marcha del repositorio remoto

> **Pendiente.** A fecha de hoy el proyecto solo existe en local: no hay remoto
> configurado. Todo lo de esta seccion está listo para ejecutarse el día que se
> decida crearlo.

```bash
brew install gh
gh auth login                      # la autenticación es del humano

gh repo create mi-plata-app --private --source=. --remote=origin
git push -u origin main
git push -u origin chore/project-scaffolding
```

Después, la protección de rama (abajo) y activar Renovate desde
[github.com/apps/renovate](https://github.com/apps/renovate) — la configuración
ya está en `renovate.json`.

**Hasta que exista el remoto, el workflow de CI no se ha ejecutado nunca.** Está
verificado en local reproduciendo sus mismos comandos con `CI=true`, pero las
versiones de las actions y la caché solo se prueban en el primer push.

## Protección de `main`

En GitHub → Settings → Branches:

- [x] Require a pull request before merging
- [x] Require status checks to pass: `build`, `test`, `detekt`, `lint`
- [x] Require branches to be up to date before merging
- [x] Require linear history → fuerza **squash merge** o rebase
- [x] Include administrators ← **sí, incluso siendo el único dev.** Si te puedes
      saltar la regla, la regla no existe.
- [x] Do not allow force pushes

### PRs en solitario: no es teatro

Siendo un solo desarrollador, el PR no sirve para code review de otro. Sirve para
tres cosas concretas:

1. Es el punto donde CI corre **antes** de que el código toque `main`.
2. Es el lugar donde escribes *por qué* hiciste el cambio — tu yo de dentro de
   seis meses te lo agradecerá más que cualquier comentario en el código.
3. Te da un diff completo y revisable del cambio, que es cuando aparecen los
   errores tontos.

**Merge strategy: squash and merge.** Una feature = un commit en `main`.
Historial limpio, `git revert` de una feature entera es un solo comando,
`git bisect` es útil de verdad.

## Versionado

**SemVer** en tags: `vMAJOR.MINOR.PATCH`.

- `MAJOR` — cambio en el **formato de backup** que rompe compatibilidad hacia
  atrás. En esta app, el contrato público con el usuario es su archivo de backup.
- `MINOR` — funcionalidad nueva
- `PATCH` — correcciones

`versionCode` para Play Store se deriva del tag automáticamente en CI.

## CI/CD — GitHub Actions

**`.github/workflows/pr.yml`** (en cada PR):
```
1. setup-java 17 + gradle cache
2. ./gradlew spotlessCheck detekt
3. ./gradlew lint
4. ./gradlew testDebugUnitTest
5. ./gradlew assembleDebug
6. subir reportes de test como artifact
```

**`.github/workflows/release.yml`** (en tag `v*`):
```
1. todo lo anterior
2. ./gradlew bundleRelease  (firmado con keystore desde GitHub Secrets)
3. generar CHANGELOG desde los conventional commits
4. crear GitHub Release con el .aab adjunto
```

**`renovate.json`** — actualización automática de dependencias con PRs
agrupados. Mejor que Dependabot para el version catalog de Gradle.

## Hooks locales (lefthook o pre-commit)

```
pre-commit:  spotlessApply  (formato automático, sin discusión)
commit-msg:  commitlint     (valida el formato del mensaje)
pre-push:    testDebugUnitTest de :core:domain  (rápido, alto valor)
```

Rápidos a propósito. Un hook que tarda 3 minutos se termina saltando con
`--no-verify`, y entonces no sirve de nada.

## Issues y planificación

- GitHub Issues con plantillas (`bug`, `feature`, `tech-debt`).
- GitHub Projects como tablero: `Backlog → Listo → En curso → En revisión → Hecho`.
- Un issue por unidad entregable. Si un issue no se puede cerrar en ≤3 días,
  se parte.
- Cada PR referencia su issue (`Closes #12`).
- **Milestones = versiones** (`v0.1 MVP`, `v0.2 Control`, `v0.3 Análisis`).

## ADRs — Architecture Decision Records

Toda decisión técnica no trivial se registra en `docs/adr/` con el formato
`NNNN-titulo.md`. Estado: `Propuesto` / `Aceptado` / `Reemplazado por NNNN`.

Las decisiones se **reemplazan**, no se editan ni se borran. El valor de un ADR
está en preservar el contexto de *por qué* algo se decidió, incluso cuando esa
decisión luego cambia.
