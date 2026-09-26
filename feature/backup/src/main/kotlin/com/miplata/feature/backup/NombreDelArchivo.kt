package com.miplata.feature.backup

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * El nombre que se propone al guardar: `miplata-backup-2026-09-26-1042.mpb`.
 *
 * Con fecha y hora porque el usuario va a acabar con varios en la misma carpeta
 * de Drive, y tiene que poder saber cual es el ultimo sin abrirlos. En orden
 * año-mes-dia para que ordenar por nombre sea ordenar por fecha.
 *
 * La extension propia hace que ningun visor lo intente abrir por su cuenta, y
 * que una app de mensajeria no lo recomprima creyendo que es un ZIP cualquiera.
 */
fun nombreDelArchivo(
    instante: Instant,
    zona: TimeZone,
): String {
    val momento = instante.toLocalDateTime(zona)
    val fecha = momento.date.toString()
    val hora = "%02d%02d".format(momento.hour, momento.minute)
    return "miplata-backup-$fecha-$hora.$EXTENSION"
}

const val EXTENSION = "mpb"
