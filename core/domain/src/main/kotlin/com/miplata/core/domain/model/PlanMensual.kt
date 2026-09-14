package com.miplata.core.domain.model

enum class TipoDeLinea {
    /** Sueldo, freelance, alquileres cobrados. */
    INGRESO,

    /** Arriendo, internet, la cuota del carro. Importe conocido de antemano. */
    GASTO_FIJO,

    /** Comida, transporte, ocio. Es una estimacion, no un compromiso. */
    GASTO_VARIABLE,

    /** Aporte a una meta de ahorro. Resta del disponible como un gasto mas. */
    AHORRO,
    ;

    /** Si resta del disponible del mes. Todo lo que no es ingreso, resta. */
    val restaDelDisponible: Boolean get() = this != INGRESO
}

/**
 * Una linea del plan del mes: "Arriendo, 450, todos los dias 5".
 *
 * El [montoPlanificado] es una **magnitud sin signo**; que sume o reste lo dice
 * el [tipo], igual que en [Transaccion].
 */
data class LineaDePlan(
    val id: LineaId,
    /**
     * Etiqueta para el usuario. **Puede estar vacia.**
     *
     * Al anadir una linea el usuario la crea antes de saber como llamarla y
     * escribe encima. Exigir un nombre obligaria a inventarse uno de relleno
     * -"Nueva linea"- que habria que borrar antes de escribir el de verdad.
     *
     * Lo que define una linea es su tipo y su monto; el nombre es como la
     * reconoce. Una linea sin nombre sigue sumando correctamente.
     *
     * Es distinto de [Cuenta], donde el nombre SI es obligatorio: una cuenta se
     * elige de una lista, y una sin nombre no se puede elegir.
     */
    val nombre: String,
    val tipo: TipoDeLinea,
    /** Siempre positivo o cero. El signo lo determina [tipo]. */
    val montoPlanificado: Money,
    /** Para cruzar la linea con las transacciones reales de esa categoria. */
    val categoriaId: CategoriaId? = null,
    val cuentaId: CuentaId? = null,
    /** Dia del mes en que se espera, de 1 a 31. Sirve para proyectar el flujo de caja. */
    val diaDelMes: Int? = null,
    /**
     * Si la linea cuenta este mes.
     *
     * Desactivar no es borrar, y la diferencia importa:
     * - **Desactivar** dice "este mes no toca". La linea deja de sumar en los
     *   totales, pero sigue existiendo y se copia al materializar el mes
     *   siguiente, conservando su estado. Es el seguro trimestral o la matricula
     *   de septiembre: no se pagan todos los meses, pero no han desaparecido.
     * - **Borrar** dice "esto ya no existe". Entonces si deja de copiarse.
     */
    val activa: Boolean = true,
) {
    init {
        require(!montoPlanificado.esNegativo) {
            "El monto planificado es una magnitud sin signo; " +
                "el tipo decide si suma o resta. Recibido: $montoPlanificado"
        }
        if (diaDelMes != null) {
            require(diaDelMes in 1..DIA_MAXIMO) {
                "El dia del mes debe estar entre 1 y $DIA_MAXIMO, no $diaDelMes"
            }
        }
    }

    private companion object {
        const val DIA_MAXIMO = 31
    }
}

/**
 * Lo que esperas que pase este mes: tus ingresos, tus gastos fijos y tu
 * estimacion de los variables.
 *
 * **Cada mes tiene su propio plan, con sus propias lineas** (docs/adr/0003). No
 * es una plantilla unica compartida: si el arriendo sube en marzo, febrero
 * conserva el valor que tenia. Con una plantilla mutable, editarla reescribiria
 * la historia y comparar dos meses dejaria de significar nada.
 *
 * El plan de un mes nuevo se crea copiando las lineas activas del mes anterior,
 * asi que el usuario se encuentra su plan ya armado y solo ajusta lo que cambio.
 * Eso es lo que hace que la app se sienta flexible sin perder el pasado.
 */
data class PlanMensual(
    val id: PlanId,
    val mes: Mes,
    val lineas: List<LineaDePlan> = emptyList(),
) {
    val lineasActivas: List<LineaDePlan> get() = lineas.filter { it.activa }

    init {
        val repetidos =
            lineas
                .groupingBy { it.id }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(repetidos.isEmpty()) {
            "El plan de $mes tiene lineas con el mismo id: ${repetidos.joinToString()}"
        }
    }

    fun lineasDe(tipo: TipoDeLinea): List<LineaDePlan> = lineasActivas.filter { it.tipo == tipo }

    /** Suma de las lineas activas de un tipo. Solo cuentan las activas. */
    fun totalPlanificadoDe(tipo: TipoDeLinea): Money = lineasDe(tipo).map { it.montoPlanificado }.sumar()

    /**
     * Lo que queda despues de restar a los ingresos todo lo demas.
     *
     * Negativo significa que el plan ya no cuadra sobre el papel: el mes esta en
     * sobregiro antes incluso de empezar a gastar.
     */
    fun disponiblePlanificado(): Money {
        val ingresos = totalPlanificadoDe(TipoDeLinea.INGRESO)
        val salidas =
            lineasActivas
                .filter { it.tipo.restaDelDisponible }
                .map { it.montoPlanificado }
                .sumar()
        return ingresos - salidas
    }
}
