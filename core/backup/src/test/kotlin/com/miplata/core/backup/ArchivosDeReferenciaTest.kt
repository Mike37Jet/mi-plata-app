package com.miplata.core.backup

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** La frase con la que se generaron los archivos de referencia. Es publica a proposito. */
private val FRASE_DE_REFERENCIA = FraseDeRespaldo.de("caballo correcto bateria grapa")

private fun referencia(version: Int) = "/backups/formato-$version.mpb"

private fun abrir(version: Int) =
    requireNotNull(ArchivosDeReferenciaTest::class.java.getResourceAsStream(referencia(version))) {
        "Falta el archivo de referencia ${referencia(version)}"
    }

/**
 * Copias de verdad, generadas por versiones anteriores de la app y congeladas
 * en `src/test/resources/backups/`.
 *
 * Un round-trip no protege las copias viejas: escribe y lee con el mismo codigo,
 * y si un cambio rompe el formato rompe los dos lados a la vez y el test sigue
 * en verde. Estos archivos no cambian nunca, asi que cualquier cambio que deje
 * de saber leerlos -renombrar un campo, cambiar un enum, tocar el cifrado- se
 * ve aqui.
 *
 * **Nunca se regeneran.** Si un test de aqui falla, lo que esta mal es el codigo,
 * no el archivo: ese archivo es lo que tiene un usuario en su Drive.
 */
class ArchivosDeReferenciaTest {
    @Test
    fun `una copia del formato 1 se sigue abriendo con todo su contenido`() {
        val leido = abrir(1).use { ArchivoDeBackup().leer(it, FRASE_DE_REFERENCIA) }

        leido.datos shouldBe DATOS_DEL_FORMATO_1
    }

    @Test
    fun `el manifiesto del formato 1 se sigue entendiendo`() {
        val manifiesto = abrir(1).use { ArchivoDeBackup().leerManifiesto(it) }

        manifiesto.versionDelFormato shouldBe 1
        manifiesto.versionDelEsquema shouldBe 1
        manifiesto.versionDeLaApp shouldBe "0.1.0"
        manifiesto.creadoEnMillis shouldBe 1_790_000_000_000L
        manifiesto.dispositivo shouldBe "Pixel de referencia"
        manifiesto.contenido shouldBe Recuento(cuentas = 3, categorias = 3, transacciones = 3, planes = 2)
    }

    /**
     * Leer el JSON no basta: los nombres de los enums tienen que seguir
     * existiendo en el dominio, o la restauracion fallaria despues de haber
     * dicho que la copia era buena.
     */
    @Test
    fun `el contenido del formato 1 se sigue convirtiendo al dominio`() {
        val leido = abrir(1).use { ArchivoDeBackup().leer(it, FRASE_DE_REFERENCIA) }

        leido.datos.aContenidoFinanciero().shouldNotBeNull()
    }

    /**
     * Subir [VERSION_DEL_FORMATO] obliga a commitear una copia del formato
     * nuevo, que sera la referencia cuando llegue el siguiente.
     */
    @Test
    fun `hay un archivo de referencia por cada version del formato`() {
        val faltan =
            (1..VERSION_DEL_FORMATO).filter {
                ArchivosDeReferenciaTest::class.java.getResource(referencia(it)) == null
            }

        faltan shouldBe emptyList()
    }
}

/** Lo que se metio en `formato-1.mpb` al generarlo. */
private val DATOS_DEL_FORMATO_1 =
    DatosDelBackup(
        ajustes =
            AjustesDto(
                moneda = "EUR",
                primerDiaDelMesFinanciero = 25,
                tema = "OSCURO",
                ultimoBackupEnMillis = 1_780_000_000_000L,
                frecuenciaDeRecordatorio = "SEMANAL",
            ),
        cuentas =
            listOf(
                CuentaDto("nomina", "Cuenta nómina", "BANCARIA", 250_000, "EUR"),
                CuentaDto("visa", "Visa", "TARJETA_CREDITO", -43_000, "EUR", incluirEnTotal = false, archivada = true),
                CuentaDto("cartera", "Cartera", "EFECTIVO", 5_075, "EUR"),
            ),
        categorias =
            listOf(
                CategoriaDto("comida", "Comida", icono = "🍽", color = -16711936),
                CategoriaDto("restaurantes", "Restaurantes", padreId = "comida"),
                CategoriaDto("sueldo", "Sueldo"),
            ),
        planes =
            listOf(
                PlanDto(
                    "plan-2026-03",
                    "2026-03",
                    listOf(
                        LineaDePlanDto(
                            "l-sueldo",
                            "Sueldo",
                            "INGRESO",
                            200_000,
                            categoriaId = "sueldo",
                            cuentaId = "nomina",
                            diaDelMes = 25,
                        ),
                        LineaDePlanDto(
                            "l-comida",
                            "Comida",
                            "GASTO_VARIABLE",
                            40_050,
                            categoriaId = "comida",
                            activa = false,
                        ),
                    ),
                ),
                PlanDto("plan-2026-04", "2026-04"),
            ),
        transacciones =
            listOf(
                TransaccionDto(
                    "t-cena",
                    "2026-03-15",
                    4_275,
                    "GASTO",
                    "cartera",
                    categoriaId = "restaurantes",
                    lineaDePlanId = "l-comida",
                    nota = "Cena con Ana 🎉",
                ),
                TransaccionDto("t-sueldo", "2026-03-25", 200_000, "INGRESO", "nomina", lineaDePlanId = "l-sueldo"),
                TransaccionDto("t-a-visa", "2026-03-26", 10_000, "TRANSFERENCIA", "nomina", cuentaDestinoId = "visa"),
            ),
    )
