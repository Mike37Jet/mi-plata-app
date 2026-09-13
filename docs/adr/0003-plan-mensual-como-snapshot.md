# 0003 — El plan mensual es un snapshot por mes, no una plantilla mutable

- **Estado:** Aceptado
- **Fecha:** 2026-09-13

## Contexto

El usuario pidió que los ingresos y gastos sean editables en cualquier momento.
La forma ingenua es una única lista de líneas que se edita y ya. El problema: si
el arriendo sube en marzo, editar la lista también cambia lo que la app muestra
para febrero. La historia queda reescrita.

## Decisión

Cada mes tiene su propio `PlanMensual` con sus propias `LineaDePlan`. Al abrir un
mes que no tiene plan, se **materializa** copiando las líneas activas del mes
anterior.

## Alternativas consideradas

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| Plantilla única mutable | Menos filas, edición trivial | Reescribe el pasado; comparar meses es imposible | Rechazado |
| Líneas con vigencia (`validFrom`/`validTo`) | No duplica datos | Toda query necesita resolver vigencia; complejidad alta y errores sutiles | Rechazado |
| **Snapshot por mes** | Historia correcta, queries simples, comparativas reales | ~20 filas extra por mes | **Aceptado** |

## Consecuencias

- Los meses pasados quedan inmutables de hecho; la UI debe advertir al editar uno.
- La materialización es un use case de dominio, testeable sin base de datos.
- Es la base directa de las comparativas mes a mes de la v0.3.
