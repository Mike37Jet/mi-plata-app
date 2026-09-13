# 0002 — El dinero se representa con `Money` (value class sobre `Long` de centavos)

- **Estado:** Aceptado
- **Fecha:** 2026-09-13

## Contexto

Representar dinero con punto flotante produce errores de redondeo acumulativos
(`0.1 + 0.2 != 0.3`). En una app de finanzas eso significa saldos que no cuadran,
que es un fallo de producto, no un detalle.

## Decisión

`@JvmInline value class Money(val centavos: Long)`. Persistido como `INTEGER` en
SQLite. `BigDecimal` solo en los bordes de entrada/salida, con redondeo
`HALF_EVEN` explícito.

## Alternativas consideradas

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| `Double` | Trivial | Errores de redondeo, comparaciones poco fiables | Rechazado |
| `BigDecimal` en todo el dominio | Exacto | Objeto pesado, lento, no persiste bien en SQLite, no es `value class` | Rechazado |
| `Long` de centavos crudo | Exacto y rápido | Un `Long` de días se puede pasar donde va dinero, sin error de compilación | Rechazado |
| **`value class` sobre `Long`** | Exacto, rápido, cero costo en runtime, **type-safe** | Hay que escribir operadores y formateo | **Aceptado** |

## Consecuencias

- Se implementan `plus`, `minus`, `times`, `compareTo` y helpers de porcentaje.
- El formateo a texto vive en presentación con `NumberFormat` según el locale.
  El dominio nunca produce strings de dinero.
- La moneda (ISO 4217) se guarda por cuenta desde v1, aunque la app opere en una
  sola. Añadirla después sería una migración dolorosa.
