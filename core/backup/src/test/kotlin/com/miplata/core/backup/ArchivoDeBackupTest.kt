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

class ArchivoDeBackupTest {
    private val archivo = ArchivoDeBackup()

    // El test que justifica el modulo entero: lo que entra es lo que sale.
    @Test
    fun `lo que se escribe es lo que se lee`() {
        val bytes = archivo.escribirABytes(contenido())

        val leido = archivo.leer(ByteArrayInputStream(bytes))

        leido.datos shouldBe DATOS_DE_EJEMPLO
    }

    @Test
    fun `el manifiesto describe lo que hay dentro`() {
        val bytes = archivo.escribirABytes(contenido())

        val manifiesto = archivo.leer(ByteArrayInputStream(bytes)).manifiesto

        manifiesto.versionDelFormato shouldBe VERSION_DEL_FORMATO
        manifiesto.versionDeLaApp shouldBe "0.1.0"
        manifiesto.dispositivo shouldBe "Pixel de prueba"
        manifiesto.contenido.cuentas shouldBe 1
        manifiesto.contenido.transacciones shouldBe 1
        manifiesto.contenido.planes shouldBe 0
    }

    // Sirve para el resumen previo -"se restauraran 4 cuentas, 312 movimientos"-
    // sin tener que descomprimir y parsear todo el contenido.
    @Test
    fun `el manifiesto se puede leer solo`() {
        val bytes = archivo.escribirABytes(contenido())

        archivo.leerManifiesto(ByteArrayInputStream(bytes)).contenido.cuentas shouldBe 1
    }

    @Test
    fun `un backup vacio se escribe y se lee igual`() {
        val vacio = DatosDelBackup(ajustes = AjustesDto("USD", 1, "SEGUN_EL_SISTEMA"))

        val leido = archivo.leer(ByteArrayInputStream(archivo.escribirABytes(contenido(vacio))))

        leido.datos shouldBe vacio
        leido.manifiesto.contenido shouldBe Recuento()
    }

    // Lo que hace util al checksum: detectar el destrozo ANTES de tocar la base.
    @Test
    fun `un byte cambiado en los datos se detecta`() {
        val bytes = archivo.escribirABytes(contenido())
        val corrupto = corromperLosDatos(bytes)

        val error = shouldThrow<BackupInvalido> { archivo.leer(ByteArrayInputStream(corrupto)) }

        error.message!! shouldContain "corrupto"
        error.message!! shouldContain "No se ha modificado nada"
    }

    // Elegir el archivo equivocado en el selector es facil de hacer, asi que el
    // mensaje tiene que decir que pasa sin hablar de zips ni de manifiestos.
    @Test
    fun `un archivo cualquiera se rechaza con un mensaje entendible`() {
        val error =
            shouldThrow<BackupInvalido> {
                archivo.leer(ByteArrayInputStream("esto es una foto, no un backup".toByteArray()))
            }

        error.message!! shouldContain "no parece un backup"
    }

    // Un backup de una version futura tiene que decir "actualiza la app" y no
    // "checksum incorrecto", que no le dice nada a nadie. Por eso la version se
    // comprueba antes que el checksum.
    @Test
    fun `un backup de un formato mas nuevo pide actualizar la app`() {
        val bytes = archivo.escribirABytes(contenido())
        val delFuturo = conVersionDeFormato(bytes, VERSION_DEL_FORMATO + 1)

        val error = shouldThrow<BackupInvalido> { archivo.leer(ByteArrayInputStream(delFuturo)) }

        error.message!! shouldContain "Actualiza la app"
    }

    @Test
    fun `un backup sin manifiesto se rechaza`() {
        val soloDatos = zipCon(mapOf(Piezas.DATOS to "{}".toByteArray()))

        val error = shouldThrow<BackupInvalido> { archivo.leer(ByteArrayInputStream(soloDatos)) }

        error.message!! shouldContain "no parece un backup"
    }

    @Test
    fun `el checksum cambia cuando cambian los datos`() {
        val uno = archivo.leerManifiesto(ByteArrayInputStream(archivo.escribirABytes(contenido())))
        val otro =
            archivo.leerManifiesto(
                ByteArrayInputStream(
                    archivo.escribirABytes(
                        contenido(DATOS_DE_EJEMPLO.copy(cuentas = emptyList())),
                    ),
                ),
            )

        (uno.checksum == otro.checksum) shouldBe false
    }
}
