package com.miplata.core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Una cantidad de dinero, representada exactamente como un numero entero de
 * centavos.
 *
 * **Nunca se usa `Double` ni `Float` para dinero** (ver `docs/adr/0002`).
 * `0.1 + 0.2 != 0.3` en punto flotante, y en una app de finanzas eso es un saldo
 * que no cuadra: un fallo de producto, no un detalle de implementacion.
 *
 * Al ser un `value class` sobre `Long` no cuesta nada en tiempo de ejecucion
 * -el compilador la borra y deja el `Long` desnudo- pero **si** impide pasar un
 * `Long` de dias, de indices o de milisegundos donde se espera dinero. Ese error
 * pasa a ser de compilacion.
 *
 * El formateo a texto para el usuario (`$ 1.234,56`) es responsabilidad de la
 * capa de presentacion, que conoce el locale y la moneda. El dominio no produce
 * cadenas de dinero.
 *
 * La aritmetica falla de forma ruidosa si desborda: un saldo que da la vuelta en
 * silencio es peor que un crash.
 */
@JvmInline
value class Money private constructor(
    val centavos: Long,
) : Comparable<Money> {
    val esCero: Boolean get() = centavos == 0L
    val esPositivo: Boolean get() = centavos > 0L
    val esNegativo: Boolean get() = centavos < 0L

    operator fun plus(otro: Money): Money = Money(Math.addExact(centavos, otro.centavos))

    operator fun minus(otro: Money): Money = Money(Math.subtractExact(centavos, otro.centavos))

    operator fun times(factor: Int): Money = Money(Math.multiplyExact(centavos, factor.toLong()))

    operator fun unaryMinus(): Money = Money(Math.negateExact(centavos))

    fun valorAbsoluto(): Money = if (esNegativo) -this else this

    /**
     * Que porcentaje representa esta cantidad respecto de [total].
     *
     * Devuelve `null` cuando [total] es cero: "he gastado 50 de un presupuesto
     * de 0" no tiene respuesta numerica, y forzar un 0, un 100 o un infinito
     * seria inventarse una. Quien llama decide como mostrar ese caso.
     *
     * El resultado es `Double` a proposito: un porcentaje no es dinero.
     */
    fun porcentajeDe(total: Money): Double? =
        if (total.esCero) null else centavos.toDouble() * PORCENTAJE / total.centavos.toDouble()

    override fun compareTo(other: Money): Int = centavos.compareTo(other.centavos)

    /**
     * Representacion para depuracion y mensajes de test, deliberadamente
     * independiente del locale. No es formateo para el usuario.
     */
    override fun toString(): String {
        val signo = if (esNegativo) "-" else ""
        val absoluto = if (esNegativo) -centavos else centavos
        val unidades = absoluto / CENTAVOS_POR_UNIDAD
        val resto = absoluto % CENTAVOS_POR_UNIDAD
        return "$signo$unidades.${resto.toString().padStart(2, '0')}"
    }

    companion object {
        private const val CENTAVOS_POR_UNIDAD = 100L
        private const val PORCENTAJE = 100.0
        private const val DECIMALES = 2

        val ZERO: Money = Money(0L)

        fun deCentavos(centavos: Long): Money = Money(centavos)

        /** Atajo legible: `Money.deUnidades(5)` son 500 centavos. */
        fun deUnidades(unidades: Long): Money = Money(Math.multiplyExact(unidades, CENTAVOS_POR_UNIDAD))

        /**
         * Convierte un decimal a centavos redondeando con **HALF_EVEN**
         * (redondeo bancario).
         *
         * HALF_EVEN y no HALF_UP porque al redondear muchas cantidades el
         * HALF_UP sesga sistematicamente hacia arriba: sumado sobre cientos de
         * transacciones, el total se desvia. HALF_EVEN reparte los empates y el
         * sesgo se cancela.
         *
         * Es el punto de entrada desde la UI, que es donde el usuario teclea
         * "12,50". Dentro del dominio el dinero ya no vuelve a ser decimal.
         */
        fun deDecimal(valor: BigDecimal): Money =
            Money(valor.setScale(DECIMALES, RoundingMode.HALF_EVEN).movePointRight(DECIMALES).longValueExact())
    }
}

/**
 * Suma una coleccion de cantidades. Una coleccion vacia suma [Money.ZERO].
 *
 * Existe porque sumar transacciones es la operacion mas repetida del dominio, y
 * `fold(Money.ZERO, Money::plus)` esparcido por todas partes se lee peor y es
 * mas facil de equivocar.
 */
fun Iterable<Money>.sumar(): Money = fold(Money.ZERO, Money::plus)
