package com.miplata.core.designsystem.formato

import com.miplata.core.domain.model.Mes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * El idioma en que se escriben las fechas: el de la app, no el del telefono.
 *
 * La app solo habla español. Con el telefono en ingles, "March 2026" saldria
 * en mitad de una pantalla en español (lo mismo que pasaba con los nombres de
 * las monedas en la bienvenida).
 */
private val ESPANOL: Locale = Locale.forLanguageTag("es")

private val MES_Y_ANIO = DateTimeFormatter.ofPattern("LLLL yyyy", ESPANOL)
private val DIA_Y_MES = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ESPANOL)

/**
 * "Marzo 2026".
 *
 * Sustituye a `2026-03`, que es como lo guarda la base y no como lo dice una
 * persona (Nielsen, relacion con el mundo real; docs/10).
 */
fun nombreDelMes(mes: Mes): String = YearMonth.of(mes.anio, mes.numeroDeMes).format(MES_Y_ANIO).conMayuscula()

/** "Jueves, 26 de marzo". Sin año: lo dice el mes que se esta viendo. */
fun fechaLarga(fecha: LocalDate): String = fecha.toJavaLocalDate().format(DIA_Y_MES).conMayuscula()

// En español los meses y los dias van en minuscula, pero al empezar una
// etiqueta va mayuscula, como en cualquier frase.
private fun String.conMayuscula() = replaceFirstChar { it.uppercase(ESPANOL) }
