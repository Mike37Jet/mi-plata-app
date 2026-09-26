package com.miplata.core.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

private val DATOS_DE_EJEMPLO =
    DatosDelBackup(
        ajustes = AjustesDto(moneda = "EUR", primerDiaDelMesFinanciero = 25, tema = "OSCURO"),
        cuentas =
            listOf(
                CuentaDto(
                    id = "banco",
                    nombre = "Cuenta del banco",
                    tipo = "BANCARIA",
                    saldoInicialEnCentavos = 250_000,
                    moneda = "EUR",
                ),
            ),
        transacciones =
            listOf(
                TransaccionDto(
                    id = "t1",
                    fecha = "2026-03-15",
                    montoEnCentavos = 4_200,
                    tipo = "GASTO",
                    cuentaOrigenId = "banco",
                ),
            ),
    )

private fun contenido(datos: DatosDelBackup = DATOS_DE_EJEMPLO) =
    ContenidoDelBackup(
        datos = datos,
        versionDelEsquema = 1,
        versionDeLaApp = "0.1.0",
        creadoEnMillis = 1_772_000_000_000L,
        dispositivo = "Pixel de prueba",
    )

/** El formato del archivo: piezas, manifiesto, versiones y checksum. */
class ArchivoDeBackupTest {
    private val archivo = archivoRapido()

    private fun bytesDe(datos: DatosDelBackup = DATOS_DE_EJEMPLO) = archivo.escribirABytes(contenido(datos), FRASE)

    private fun leer(bytes: ByteArray) = archivo.leer(ByteArrayInputStream(bytes), FRASE)

    // El test que justifica el modulo entero: lo que entra es lo que sale.
    @Test
    fun `lo que se escribe es lo que se lee`() {
        leer(bytesDe()).datos shouldBe DATOS_DE_EJEMPLO
    }

    @Test
    fun `el manifiesto describe lo que hay dentro`() {
        val manifiesto = leer(bytesDe()).manifiesto

        manifiesto.versionDelFormato shouldBe VERSION_DEL_FORMATO
        manifiesto.versionDeLaApp shouldBe "0.1.0"
        manifiesto.dispositivo shouldBe "Pixel de prueba"
        manifiesto.contenido.cuentas shouldBe 1
        manifiesto.contenido.transacciones shouldBe 1
        manifiesto.contenido.planes shouldBe 0
    }

    // Sirve para el resumen previo -"se restauraran 4 cuentas, 312 movimientos"-
    // y para rechazar un archivo demasiado nuevo antes de pedir la frase.
    @Test
    fun `el manifiesto se lee sin la frase`() {
        archivo.leerManifiesto(ByteArrayInputStream(bytesDe())).contenido.cuentas shouldBe 1
    }

    // Una copia hecha antes de que existiera la frecuencia de recordatorio no
    // trae el campo, y tiene que seguir leyendose.
    @Test
    fun `una copia sin la frecuencia de recordatorio se lee con la mensual`() {
        val sinCampo = AjustesDto(moneda = "EUR", primerDiaDelMesFinanciero = 1, tema = "CLARO")

        sinCampo.aDominio().frecuenciaDeRecordatorio shouldBe
            com.miplata.core.domain.model.FrecuenciaDeRecordatorio.MENSUAL
    }

    @Test
    fun `un backup vacio se escribe y se lee igual`() {
        val vacio = DatosDelBackup(ajustes = AjustesDto("USD", 1, "SEGUN_EL_SISTEMA"))

        val leido = leer(bytesDe(vacio))

        leido.datos shouldBe vacio
        leido.manifiesto.contenido shouldBe Recuento()
    }

    // Quien abre un stream lo cierra. Si escribir lo cerrara, quien llama no
    // podria hacer fsync despues: paso de verdad en el telefono, donde guardar la
    // copia previa fallaba con "sync failed" y la de restaurar se quedaba sin
    // la garantia de haber llegado al disco.
    @Test
    fun `escribir no cierra el stream de quien llama`() {
        var cerrado = false
        val destino =
            object : java.io.ByteArrayOutputStream() {
                override fun close() {
                    cerrado = true
                    super.close()
                }
            }

        archivo.escribir(contenido(), FRASE, destino)

        cerrado shouldBe false
        leer(destino.toByteArray()).datos shouldBe DATOS_DE_EJEMPLO
    }

    // Elegir el archivo equivocado en el selector es facil de hacer, asi que el
    // mensaje tiene que decir que pasa sin hablar de zips ni de manifiestos.
    @Test
    fun `un archivo cualquiera se rechaza con un mensaje entendible`() {
        val error =
            shouldThrow<BackupInvalido> {
                leer("esto es una foto, no un backup".toByteArray())
            }

        error.message!! shouldContain "no parece un backup"
    }

    // Un backup de una version futura tiene que decir "actualiza la app" y no
    // "archivo dañado". Por eso la version se comprueba antes que el checksum.
    @Test
    fun `un backup de un formato mas nuevo pide actualizar la app`() {
        val delFuturo = conVersionDeFormato(bytesDe(), VERSION_DEL_FORMATO + 1)

        val error = shouldThrow<BackupInvalido> { leer(delFuturo) }

        error.message!! shouldContain "Actualiza la app"
    }

    @Test
    fun `un backup sin manifiesto se rechaza`() {
        val soloDatos = zipCon(mapOf(Piezas.DATOS to ByteArray(32)))

        val error = shouldThrow<BackupInvalido> { leer(soloDatos) }

        error.message!! shouldContain "no parece un backup"
    }

    @Test
    fun `un backup sin datos se rechaza`() {
        val piezas = piezasDe(bytesDe())
        piezas.remove(Piezas.DATOS)

        shouldThrow<BackupInvalido> { leer(zipCon(piezas)) }
    }
}
