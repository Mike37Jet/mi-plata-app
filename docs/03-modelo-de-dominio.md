# 03 — Modelo de dominio

Todo lo de este documento vive en `:core:domain` como **Kotlin puro**. Cero
anotaciones de Room, cero `androidx`.

## 1. Dinero: `Money`

**Nunca** se usa `Double` ni `Float` para dinero. `0.1 + 0.2 != 0.3` en punto
flotante; en una app de finanzas eso es un saldo que no cuadra.

```kotlin
@JvmInline
value class Money private constructor(val centavos: Long) : Comparable<Money> {
    operator fun plus(otro: Money) = Money(centavos + otro.centavos)
    operator fun minus(otro: Money) = Money(centavos - otro.centavos)
    val esNegativo get() = centavos < 0

    companion object {
        val ZERO = Money(0)
        fun deCentavos(c: Long) = Money(c)
        fun deDecimal(d: BigDecimal) = Money(d.movePointRight(2).setScale(0, HALF_EVEN).longValueExact())
    }
}
```

- Se guarda en Room como `INTEGER` (centavos). Exacto, ordenable, sumable en SQL.
- `value class` → cero costo en runtime, pero **imposible** pasar un `Long` de
  "días" donde se espera dinero. El compilador lo impide.
- El formateo a texto (`$ 1.234,56`) es responsabilidad de la capa de
  presentación con `NumberFormat`, nunca del dominio.

La moneda (`ISO 4217`) se guarda por cuenta. v1 asume una sola moneda activa,
pero el campo existe desde el día 1 — agregarlo después es una migración dolorosa.

## 2. El plan mensual (el núcleo de tu caso de uso)

El mes se representa con un tipo propio, `Mes`, y no con el `YearMonth` de una
librería de fechas: es un concepto del negocio con operaciones propias
(`siguiente()`, `anterior()`, `contiene(fecha)`), y tenerlo en el dominio evita
atar el modelo a la API de una dependencia externa. Por dentro es un solo entero
—los meses transcurridos—, de donde salen gratis el orden natural y la
aritmética sin casos especiales en diciembre.

```kotlin
data class PlanMensual(
    val id: PlanId,
    val mes: Mes,
    val lineas: List<LineaDePlan>,
)

data class LineaDePlan(
    val id: LineaId,
    val nombre: String,              // "Sueldo", "Arriendo", "Comida"
    val tipo: TipoDeLinea,
    val montoPlanificado: Money,
    val categoriaId: CategoriaId?,   // para cruzar con transacciones reales
    val cuentaId: CuentaId?,         // de dónde sale / a dónde entra
    val diaDelMes: Int?,             // 1..31, para ordenar y proyectar flujo de caja
    val activa: Boolean = true,
)

enum class TipoDeLinea {
    INGRESO,          // sueldo, freelance
    GASTO_FIJO,       // arriendo, internet, cuota del carro
    GASTO_VARIABLE,   // comida, transporte, ocio (es un presupuesto estimado)
    AHORRO,           // aporte a una meta; se trata como gasto en el disponible
}
```

### Decisión clave: cómo se maneja "el plan cambia a mitad de mes"

Pediste explícitamente que **todo sea editable en cualquier momento**. Hay dos
formas de hacerlo y la diferencia importa mucho:

| | A. Plantilla única mutable | B. **Snapshot por mes** ← elegida |
|---|---|---|
| Cómo funciona | Una sola lista de líneas; editarla afecta a todos los meses | Cada mes copia las líneas al abrirse; editar marzo no toca febrero |
| Subir el arriendo en marzo | Reescribe la historia: febrero también muestra el valor nuevo | Febrero conserva el valor viejo. Correcto. |
| Comparar meses | Imposible y engañoso | Real |
| Costo | Menos filas | ~20 filas más por mes. Irrelevante. |

**Se elige B.** Los datos históricos son inmutables *de hecho*; una app de
finanzas que reescribe el pasado no sirve para nada. El mes en curso y los
futuros son libremente editables.

Mecanismo: al abrir un mes sin plan, se **materializa** copiando las líneas
activas del mes anterior. El usuario ve su plan ya armado y solo ajusta lo que
cambió. Esto es lo que hace que la app se sienta "flexible" sin perder historia.

## 3. La realidad

```kotlin
data class Transaccion(
    val id: TransaccionId,
    val fecha: LocalDate,
    val monto: Money,                 // siempre positivo
    val tipo: TipoDeTransaccion,      // INGRESO | GASTO | TRANSFERENCIA
    val cuentaOrigenId: CuentaId,
    val cuentaDestinoId: CuentaId?,   // solo en TRANSFERENCIA
    val categoriaId: CategoriaId?,
    val lineaDePlanId: LineaId?,      // ← el puente entre plan y realidad
    val nota: String?,
)
```

`monto` siempre positivo + `tipo` explícito, en vez de montos con signo. Un signo
negativo perdido es un bug silencioso que cuadra mal el saldo; un `when` sobre un
`enum` sin rama es un error de compilación.

`lineaDePlanId` es la pieza que permite decir *"planificaste 400 de comida, llevas
gastados 520"*. Es nullable: un gasto imprevisto no pertenece a ninguna línea.

## 4. Cuentas y categorías

```kotlin
data class Cuenta(
    val id: CuentaId,
    val nombre: String,
    val tipo: TipoDeCuenta,        // EFECTIVO | BANCARIA | TARJETA_CREDITO | AHORRO | INVERSION
    val saldoInicial: Money,
    val moneda: Moneda,
    val incluirEnTotal: Boolean,   // p.ej. excluir una cuenta de inversión del "disponible"
    val archivada: Boolean = false,
)

data class Categoria(
    val id: CategoriaId,
    val nombre: String,
    val padreId: CategoriaId?,     // jerarquía de 2 niveles, no más
    val icono: String,
    val color: Int,
)
```

**El saldo de una cuenta no se guarda como campo mutable.** Se deriva:
`saldoInicial + Σ(ingresos) − Σ(gastos) ± transferencias`. Un campo `saldo`
actualizado a mano se desincroniza en cuanto falle una transacción a medias.
Room lo calcula con una query agregada y lo emite como `Flow`.

Si el rendimiento lo exigiera (no lo hará con datos de una persona), la
optimización sería una tabla de saldos precalculados como **caché derivada**,
nunca como fuente de verdad.

## 5. Nunca borrar: `SoftDelete` + auditoría

Un `DELETE` real en una app de finanzas es pérdida de datos irreversible. Nada se
borra de verdad: se marca como eliminado. Eso además habilita deshacer y hace el
backup incremental trivial.

**Dónde viven esos campos: en la capa de datos, no en los modelos de dominio.**

La versión inicial de este documento los ponía en cada entidad del dominio
(`creadoEn`, `actualizadoEn`, `eliminadoEn`). Al implementarlo quedó claro que es
el sitio equivocado: son metadatos de *cómo se guarda* una entidad, no parte de
las reglas de negocio. Ninguna regla financiera los consulta —el disponible de
marzo no depende de cuándo se creó la fila— y en cambio ensucian cada
constructor, cada test y cada `copy`.

El repositorio los gestiona y filtra lo eliminado, así que el dominio solo ve lo
vigente. Cuando una regla de negocio necesite de verdad una marca temporal (por
ejemplo, "deshacer lo borrado en los últimos 30 días"), entra en el dominio en
ese momento y con esa justificación.

Lo que sí es del dominio es el estado de negocio que se le parece: `archivada` en
`Cuenta` y `activa` en `LineaDePlan`. Esos sí los consultan las reglas.

## 6. Los cálculos (use cases de `domain`)

```kotlin
data class ResumenMensual(
    val mes: YearMonth,
    val ingresosPlanificados: Money,
    val ingresosReales: Money,
    val gastosFijosPlanificados: Money,
    val gastosVariablesPlanificados: Money,
    val gastosReales: Money,
    val disponiblePlanificado: Money,   // ingresos plan − gastos plan
    val disponibleReal: Money,          // ingresos reales − gastos reales
    val desviacionPorLinea: List<DesviacionLinea>,
    val enSobregiro: Boolean,
)
```

Producido por `CalcularResumenMensualUseCase`. Función pura: recibe plan +
transacciones, devuelve resumen. Sin I/O, sin Android, testeable exhaustivamente.

### Casos borde que los tests deben cubrir (lista viva)

- Mes sin plan y sin transacciones → todo en cero, sin dividir por cero en %
- Transacción sin línea de plan asociada
- Línea de plan desactivada a mitad de mes
- Transferencia entre cuentas propias → **no** es ingreso ni gasto, no afecta el disponible
- Gasto con fecha fuera del mes del plan
- Ingreso planificado que nunca llegó (sobregiro por ingreso faltante, no por gasto)
- Mes con primer día financiero distinto de 1 (p.ej. el mes va del 25 al 24)
- Montos que exceden `Int` (hiperinflación / monedas sin decimales)
