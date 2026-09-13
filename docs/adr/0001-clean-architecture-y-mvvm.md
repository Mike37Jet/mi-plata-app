# 0001 — Clean Architecture de 3 capas con MVVM en presentación

- **Estado:** Aceptado
- **Fecha:** 2026-09-13

## Contexto

La app maneja lógica financiera (saldos, sobregiros, desviaciones plan/real) que
es el activo más valioso del proyecto y la que más va a cambiar. Necesita ser
testeable de forma exhaustiva y rápida, e independiente de Room y de Compose.

## Decisión

Tres capas — `domain`, `data`, `presentation` — con la regla de dependencia
apuntando hacia `domain`. MVVM con flujo unidireccional en presentación.

`domain` se implementa como módulo **Kotlin/JVM puro** (`kotlin("jvm")`), no como
`com.android.library`.

## Alternativas consideradas

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| MVVM plano sin capa domain | Menos archivos, arranque rápido | La lógica financiera termina en ViewModels; tests necesitan Robolectric; imposible reusar | Rechazado |
| Clean Architecture 3 capas | Dominio puro y rápido de testear; Room/Compose reemplazables | Mappers duplicados entre entity y modelo | **Aceptado** |
| MVI con reducers/middleware | Estado muy predecible, time-travel debugging | Mucha ceremonia para una app de un solo usuario | Rechazado |

## Consecuencias

- Escribir mappers explícitos entre `Entity` y modelo de dominio. Costo asumido
  a conciencia.
- La regla de dependencia la impone el sistema de build, no la disciplina: meter
  un `Context` en `:core:domain` **no compila**.
- Los use cases triviales (un solo passthrough al repositorio) se omiten; el
  ViewModel llama al repositorio directamente. Un UseCase existe cuando hay
  lógica real o composición de varias fuentes.
