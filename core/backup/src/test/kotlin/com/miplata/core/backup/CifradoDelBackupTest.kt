package com.miplata.core.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

private val DATOS =
    DatosDelBackup(
        ajustes = AjustesDto(moneda = "EUR", primerDiaDelMesFinanciero = 1, tema = "CLARO"),
        cuentas =
            listOf(
                CuentaDto(
                    id = "banco",
                    nombre = "Cuenta nomina Santander",
                    tipo = "BANCARIA",
                    saldoInicialEnCentavos = 1_234_567,
                    moneda = "EUR",
                ),
            ),
        transacciones =
            listOf(
                TransaccionDto(
                    id = "t1",
                    fecha = "2026-03-15",
                    montoEnCentavos = 98_765,
                    tipo = "GASTO",
                    cuentaOrigenId = "banco",
                    nota = "Alquiler piso Calle Mayor",
                ),
            ),
    )

private val CONTENIDO =
    ContenidoDelBackup(
        datos = DATOS,
        versionDelEsquema = 1,
        versionDeLaApp = "0.1.0",
        creadoEnMillis = 1_772_000_000_000L,
        dispositivo = "Pixel de prueba",
    )

/**
 * El cifrado del backup: los tests que pide docs/05, y los que demuestran que
 * un archivo en la nube de un tercero no le cuenta nada a ese tercero.
 */
class CifradoDelBackupTest {
    private val archivo = archivoRapido()

    private fun backup() = archivo.escribirABytes(CONTENIDO, FRASE)

    private fun leer(
        bytes: ByteArray,
        frase: FraseDeRespaldo = FRASE,
    ) = archivo.leer(ByteArrayInputStream(bytes), frase)

    @Test
    fun `con la frase correcta se recupera todo`() {
        leer(backup()).datos shouldBe DATOS
    }

    // docs/05: frase incorrecta -> error claro. Y un tipo PROPIO, porque la
    // interfaz tiene que volver a pedir la frase en vez de dar el backup por
    // perdido.
    @Test
    fun `con otra frase falla como frase incorrecta`() {
        val error = shouldThrow<FraseIncorrecta> { leer(backup(), OTRA_FRASE) }

        error.message!! shouldContain "frase"
    }

    // Una sola letra de diferencia tiene que bastar. Si no, la frase no estaria
    // protegiendo nada.
    @Test
    fun `una mayuscula de diferencia ya no abre el backup`() {
        shouldThrow<FraseIncorrecta> {
            leer(backup(), FraseDeRespaldo.de("Correcto caballo bateria grapa"))
        }
    }

    // Lo que justifica cifrar: los nombres, las notas y los importes no pueden
    // aparecer en el archivo que acaba en Drive.
    @Test
    fun `el archivo no contiene ningun dato en claro`() {
        val todo = piezasDe(backup()).values.joinToString("") { it.decodeToString() }

        todo shouldNotContain "Santander"
        todo shouldNotContain "Calle Mayor"
        todo shouldNotContain "1234567"
        todo shouldNotContain "98765"
    }

    // El manifiesto va en claro, asi que se comprueba aparte que lo que expone
    // es solo lo imprescindible.
    @Test
    fun `el manifiesto no expone importes ni nombres`() {
        val manifiesto = piezasDe(backup()).getValue(Piezas.MANIFIESTO).decodeToString()

        manifiesto shouldNotContain "Santander"
        manifiesto shouldNotContain "Calle Mayor"
        manifiesto shouldNotContain "EUR"
    }

    // docs/05: archivo con un bit cambiado -> se detecta y se aborta. Y se
    // detecta SIN la frase, por el checksum, asi que el mensaje es "dañado" y no
    // "frase incorrecta": el usuario no se pone a probar frases para nada.
    @Test
    fun `un bit cambiado se detecta como archivo dañado, no como frase incorrecta`() {
        val error = shouldThrow<BackupInvalido> { leer(corromperLosDatos(backup())) }

        error.shouldNotBeFraseIncorrecta()
        error.message!! shouldContain "dañado"
    }

    // docs/05: archivo truncado -> se detecta y se aborta.
    @Test
    fun `un archivo cortado a medias se detecta`() {
        val error = shouldThrow<BackupInvalido> { leer(truncarLosDatos(backup())) }

        error.shouldNotBeFraseIncorrecta()
    }

    // Quien manipula el archivo sabiendo como esta hecho recalcula el checksum.
    // Lo que le para entonces es la autenticacion de GCM, que es justo por lo que
    // se eligio un cifrado autenticado.
    @Test
    fun `una manipulacion que recalcula el checksum la para GCM`() {
        shouldThrow<BackupInvalido> { leer(manipularConChecksumNuevo(backup())) }
    }

    // Los parametros van en claro pero autenticados: cambiarlos no puede pasar
    // desapercibido.
    @Test
    fun `cambiar el salt del manifiesto impide abrir el backup`() {
        val otroSalt = "\"AAAAAAAAAAAAAAAAAAAAAA==\""

        shouldThrow<BackupInvalido> { leer(conCampoDelManifiesto(backup(), "salt", otroSalt)) }
    }

    @Test
    fun `cambiar el vector de inicializacion impide abrir el backup`() {
        val otroIv = "\"AAAAAAAAAAAAAAAA\""

        shouldThrow<BackupInvalido> { leer(conCampoDelManifiesto(backup(), "iv", otroIv)) }
    }

    // Este es el caso que SOLO paran los datos asociados de GCM. Cambiar el salt
    // o el IV ya falla por si solo -la clave o la etiqueta salen distintas-, pero
    // rebajar la version del formato no toca nada criptografico: sin
    // autenticarla, el archivo se abriria y se leeria con las reglas de otra
    // version.
    @Test
    fun `rebajar la version del formato en el manifiesto impide abrir el backup`() {
        shouldThrow<BackupInvalido> { leer(conVersionDeFormato(backup(), VERSION_DEL_FORMATO - 1)) }
    }

    // Un archivo -malicioso o roto- que pida mil millones de iteraciones no
    // puede colgar la app durante minutos derivando una clave.
    @Test
    fun `unas iteraciones desorbitadas se rechazan sin intentar derivar la clave`() {
        val abusivo = conCampoDelManifiesto(backup(), "iteraciones", "2000000000")

        val error = shouldThrow<BackupInvalido> { leer(abusivo) }

        error.shouldNotBeFraseIncorrecta()
    }

    @Test
    fun `un cifrado desconocido pide actualizar la app`() {
        val desconocido = conCampoDelManifiesto(backup(), "algoritmo", "\"ChaCha20\"")

        val error = shouldThrow<BackupInvalido> { leer(desconocido) }

        error.message!! shouldContain "Actualiza la app"
    }

    // Salt e IV nuevos en cada backup: dos copias iguales con la misma frase no
    // se parecen en nada. Reutilizar un IV con la misma clave es la unica forma
    // de romper GCM sin romper AES.
    @Test
    fun `dos backups identicos con la misma frase no se parecen`() {
        val uno = piezasDe(backup())
        val otro = piezasDe(backup())

        uno.getValue(Piezas.DATOS).toList() shouldNotBe otro.getValue(Piezas.DATOS).toList()

        val parametrosUno = archivo.leerManifiesto(ByteArrayInputStream(backup())).cifrado
        val parametrosOtro = archivo.leerManifiesto(ByteArrayInputStream(backup())).cifrado
        parametrosUno.salt shouldNotBe parametrosOtro.salt
        parametrosUno.iv shouldNotBe parametrosOtro.iv
    }

    @Test
    fun `las iteraciones del archivo son las que se usan al abrirlo`() {
        val manifiesto = archivo.leerManifiesto(ByteArrayInputStream(backup()))

        manifiesto.cifrado.iteraciones shouldBe ITERACIONES_DE_TEST
    }

    // Un backup hecho con las iteraciones de hoy tiene que seguir abriendose el
    // dia que se suban: el lector usa las del archivo, no las suyas.
    @Test
    fun `un lector configurado con otras iteraciones abre igual el backup`() {
        val lectorDeOtraVersion = ArchivoDeBackup(CifradorDeBackup(iteraciones = ITERACIONES_DE_TEST * 2))

        lectorDeOtraVersion.leer(ByteArrayInputStream(backup()), FRASE).datos shouldBe DATOS
    }

    // El unico test con la configuracion real de produccion: el resto usa pocas
    // iteraciones para no tardar, y sin este nadie estaria comprobando que la de
    // verdad funciona.
    @Test
    fun `la configuracion de produccion funciona de punta a punta`() {
        val real = ArchivoDeBackup()

        val bytes = real.escribirABytes(CONTENIDO, FRASE)

        real.leerManifiesto(ByteArrayInputStream(bytes)).cifrado.iteraciones shouldBe
            CifradorDeBackup.ITERACIONES_POR_DEFECTO
        real.leer(ByteArrayInputStream(bytes), FRASE).datos shouldBe DATOS
    }

    @Test
    fun `las iteraciones por defecto siguen la recomendacion de OWASP`() {
        CifradorDeBackup.ITERACIONES_POR_DEFECTO shouldBeGreaterThanOrEqual 600_000
    }

    @Test
    fun `los parametros declaran el algoritmo que de verdad se usa`() {
        val cifrado = archivo.leerManifiesto(ByteArrayInputStream(backup())).cifrado

        cifrado.algoritmo shouldBe "AES-256-GCM"
        cifrado.derivacion shouldBe "PBKDF2-HMAC-SHA256"
    }
}

private fun BackupInvalido.shouldNotBeFraseIncorrecta() {
    (this is FraseIncorrecta) shouldBe false
}
