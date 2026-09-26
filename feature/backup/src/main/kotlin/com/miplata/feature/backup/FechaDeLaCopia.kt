package com.miplata.feature.backup

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * `2026-09-26 10:42`: el mismo estilo de fecha que el resto de la app, con hora
 * porque dos copias el mismo dia son un caso normal y hay que distinguirlas.
 */
fun fechaDeLaCopia(
    millis: Long,
    zona: TimeZone,
): String {
    val momento = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zona)
    return "${momento.date} %02d:%02d".format(momento.hour, momento.minute)
}
