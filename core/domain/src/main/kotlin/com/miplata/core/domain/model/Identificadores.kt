package com.miplata.core.domain.model

// Identificadores del dominio.
//
// Cada entidad tiene su propio tipo de identificador en vez de compartir un
// String suelto. Envolverlos cuesta cero en ejecucion -son value class- pero
// hace imposible pasar el id de una cuenta donde se espera el de una categoria.
// Con Strings desnudos ese error compila, pasa los tests y aparece en
// produccion como un saldo que no cuadra.
//
// El valor es una cadena y no un entero autoincremental a proposito: el backup
// se exporta y se reimporta en otro telefono (docs/05), y para poder fusionar
// datos algun dia los identificadores tienen que ser estables y no depender de
// la secuencia de una base de datos concreta.

@JvmInline
value class CuentaId(
    val valor: String,
) {
    init {
        require(valor.isNotBlank()) { "El id de una cuenta no puede estar en blanco" }
    }

    override fun toString(): String = valor
}

@JvmInline
value class CategoriaId(
    val valor: String,
) {
    init {
        require(valor.isNotBlank()) { "El id de una categoria no puede estar en blanco" }
    }

    override fun toString(): String = valor
}

@JvmInline
value class TransaccionId(
    val valor: String,
) {
    init {
        require(valor.isNotBlank()) { "El id de una transaccion no puede estar en blanco" }
    }

    override fun toString(): String = valor
}

@JvmInline
value class PlanId(
    val valor: String,
) {
    init {
        require(valor.isNotBlank()) { "El id de un plan no puede estar en blanco" }
    }

    override fun toString(): String = valor
}

@JvmInline
value class LineaId(
    val valor: String,
) {
    init {
        require(valor.isNotBlank()) { "El id de una linea de plan no puede estar en blanco" }
    }

    override fun toString(): String = valor
}
