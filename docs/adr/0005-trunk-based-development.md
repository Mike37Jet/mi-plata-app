# 0005 — Trunk-Based Development con ramas cortas, no GitFlow

- **Estado:** Aceptado
- **Fecha:** 2026-09-13

## Contexto

Hay que definir el flujo de ramas. El proyecto tiene un solo desarrollador, una
sola plataforma y releases a Play Store.

## Decisión

Una sola rama de larga vida (`main`), protegida. Ramas de feature de vida corta
(≤3 días) integradas por PR con squash merge. Releases marcadas con tags SemVer.

## Alternativas consideradas

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| GitFlow | Muy documentado; soporta releases paralelas | `develop`+`release/*`+`hotfix/*` es puro overhead con un dev; merges constantes sin valor | Rechazado |
| GitHub Flow / Trunk-based | Simple, integración continua real, historial limpio | Exige disciplina de partir el trabajo en piezas pequeñas | **Aceptado** |
| Commits directos a `main` | Máxima velocidad | Sin CI previa; nada impide romper `main` | Rechazado |

## Consecuencias

- "Include administrators" queda activado en la protección de rama: la regla
  aplica también al único desarrollador, o no es una regla.
- El PR en solitario no es code review: es la puerta donde corre CI y donde se
  documenta el *por qué* del cambio.
- Squash merge ⇒ una feature = un commit en `main` ⇒ `git revert` y `git bisect`
  útiles de verdad.
- El trabajo debe partirse en piezas de ≤3 días. Es una restricción deliberada:
  obliga a un roadmap con entregables reales.
