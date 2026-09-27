package com.miplata.core.domain.model

/**
 * Que papel tiene una cuenta en el presupuesto por cuentas (docs/adr/0007).
 *
 * El metodo es el de una cuenta que recibe todo el ingreso y varios sobres que
 * reciben su parte: Normal paga el arriendo y la comida, Diversion lo suyo, y
 * Libertad financiera solo acumula.
 */
sealed interface RolDeCuenta {
    /**
     * Fuera del metodo: la cartera, una tarjeta, una cuenta en otra moneda.
     *
     * Es el rol por defecto para que quien no use sobres no note nada: sus
     * cuentas siguen como estaban.
     */
    data object Independiente : RolDeCuenta

    /** Recibe los ingresos y reparte a los sobres. Solo hay una. */
    data object Principal : RolDeCuenta

    /**
     * Recibe cada mes su [reparto] desde la principal.
     *
     * @property intocable una cuenta que solo deberia subir, como Libertad
     *   financiera. Nunca se propone para cubrir a otra, y si baja se avisa.
     */
    data class Sobre(
        val reparto: Reparto = Reparto.PorDefecto,
        val intocable: Boolean = false,
    ) : RolDeCuenta
}

/**
 * Cuanto recibe un sobre cada mes: **un porcentaje del ingreso o un monto
 * fijo**.
 *
 * Es un tipo cerrado y no dos campos opcionales a proposito. Con dos campos
 * caben los estados "los dos" y "ninguno", y habria que validarlos en cada
 * sitio que los lea; aqui no se pueden escribir.
 */
sealed interface Reparto {
    /** Lo que le toca al sobre de un mes con [ingreso] planeado. */
    fun de(ingreso: Money): Money

    /** El [valor] por ciento del ingreso. Sigue al sueldo si este cambia. */
    data class Porcentaje(
        val valor: Int,
    ) : Reparto {
        init {
            require(valor in 1..MAXIMO) { "Un reparto es de 1 a $MAXIMO por ciento, no $valor" }
        }

        override fun de(ingreso: Money): Money = if (ingreso.esPositivo) ingreso.porcentaje(valor) else Money.ZERO

        private companion object {
            const val MAXIMO = 100
        }
    }

    /** Lo mismo todos los meses, cobre lo que cobre. */
    data class Monto(
        val monto: Money,
    ) : Reparto {
        init {
            require(monto.esPositivo) { "Un reparto fijo tiene que ser mayor que cero, no $monto" }
        }

        override fun de(ingreso: Money): Money = monto
    }

    companion object {
        /** El 10% de cada ingreso, que es como empieza el metodo. */
        val PorDefecto: Reparto = Porcentaje(10)
    }
}
