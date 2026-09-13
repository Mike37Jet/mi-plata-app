# 00 — Visión y alcance

## Problema

Las apps de finanzas personales suelen obligarte a registrar cada gasto para
enterarte, al final del mes, de que te pasaste. Eso responde "¿qué gasté?", pero
no responde la pregunta que de verdad importa: **"¿me alcanza?"**.

`mi-plata-app` responde primero esa pregunta.

## Idea central: Plan vs. Realidad

Es la decisión de diseño que atraviesa todo el proyecto. Existen **dos mundos**
y se modelan por separado:

| | Plan (lo esperado) | Realidad (lo ocurrido) |
|---|---|---|
| Qué es | Mi sueldo, mi arriendo, mi internet, mi estimado de comida | Los movimientos que de verdad pasaron |
| Cuándo se define | Una vez, y se ajusta cuando cambie | Día a día |
| Entidad | `PlanMensual` + `LineaDePlan` | `Transaccion` |
| Responde | "¿Me alcanza este mes?" | "¿Cuánto llevo gastado?" |

La app cruza ambos y produce las tres cifras que el usuario mira a diario:

- **Disponible planificado** = ingresos planificados − gastos fijos − gastos variables planificados
- **Disponible real** = ingresos reales recibidos − gastos reales ejecutados
- **Sobregiro / desviación** = real vs. plan, por línea y total

> Consecuencia de ingeniería: el usuario puede usar la app **sin registrar ni una
> sola transacción** y aun así obtener valor (solo con el plan). Registrar
> transacciones es una capa que *enriquece*, no un requisito. Esto baja
> muchísimo la fricción de adopción.

## Alcance

### MVP (v0.1 — "¿me alcanza?")

1. **Plan mensual editable en cualquier momento**
   - Lista de ingresos mensuales (sueldo, freelance, otros)
   - Lista de gastos fijos (arriendo, servicios, suscripciones, cuotas)
   - Lista de gastos variables presupuestados (comida, transporte, ocio)
   - Cada línea se puede crear, editar, desactivar o eliminar sin romper meses pasados
2. **Cálculo de saldo y sobregiro** en tiempo real sobre el plan
3. **Cuentas** (efectivo, banco, tarjeta) con saldo
4. **Transacciones** (ingreso / gasto / transferencia) con categoría y cuenta
5. **Categorías y subcategorías**, con set inicial precargado y editable
6. **Copia de seguridad exportable/importable** (ver `05-backup-restore.md`)

### v0.2 — Control

7. Presupuestos por categoría con alertas de sobregiro
8. Metas de ahorro con progreso
9. Recurrentes automáticas (el plan genera transacciones sugeridas)

### v0.3 — Análisis

10. Dashboard con gastos por categoría, tendencia mensual, flujo de caja
11. Comparativas periodo a periodo
12. Deudas y créditos con tabla de amortización; tarjetas con fecha de corte

### Fuera de alcance (explícitamente, y por ahora)

- Conexión automática con bancos (scraping / Open Banking / Plaid): costoso,
  frágil, y en LATAM la cobertura es mala.
- Backend propio, cuentas de usuario y login: no hay multi-dispositivo
  simultáneo, no hace falta.
- Multi-moneda con conversión en vivo. Sí se **guarda** la moneda por cuenta
  (ver `03-modelo-de-dominio.md`), pero v1 opera en una sola moneda.
- iOS / web.

## Principios de producto

1. **Offline-first absoluto.** La app nunca depende de la red para funcionar.
2. **Los datos son del usuario.** Backup en formato abierto, documentado y
   portable. Nada de lock-in.
3. **Editable siempre.** Ningún dato queda congelado; cambiar el plan a mitad de
   mes es el caso de uso normal, no la excepción.
4. **Cero dinero perdido por redondeo.** El dinero jamás se representa con
   `Double` (ver `03-modelo-de-dominio.md`).
