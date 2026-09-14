package com.miplata.core.data.ajustes

import androidx.datastore.core.DataStoreFactory
import app.cash.turbine.test
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Tema
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AjustesEnDataStoreTest {
    @get:Rule
    val carpeta = TemporaryFolder()

    // Se pasa la RUTA, no un archivo ya creado: DataStore lo crea al escribir por
    // primera vez, y un archivo vacio no es lo mismo que un archivo inexistente.
    private fun repositorio(nombre: String = "ajustes.json") =
        AjustesEnDataStore(
            DataStoreFactory.create(serializer = SerializadorDeAjustes) {
                File(carpeta.root, nombre)
            },
        )

    // Puede pasar si una escritura se interrumpe: el archivo existe pero esta
    // vacio. Arrancar con los valores por defecto es mejor que arrancar con un
    // error por cuatro preferencias.
    @Test
    fun `un archivo vacio da los valores por defecto en vez de fallar`() =
        runTest {
            val archivo = File(carpeta.root, "vacio.json").apply { createNewFile() }
            val repo =
                AjustesEnDataStore(
                    DataStoreFactory.create(serializer = SerializadorDeAjustes) { archivo },
                )

            repo.obtener() shouldBe Ajustes()
        }

    @Test
    fun `sin nada guardado devuelve los valores por defecto`() =
        runTest {
            repositorio().obtener() shouldBe Ajustes()
        }

    // Lo que importa: lo que se guarda es lo que se lee.
    @Test
    fun `los ajustes sobreviven al ida y vuelta`() =
        runTest {
            val repo = repositorio()
            val elegidos =
                Ajustes(
                    moneda = Moneda("COP"),
                    primerDiaDelMesFinanciero = 25,
                    tema = Tema.OSCURO,
                    ultimoBackupEnMillis = 1_772_000_000_000L,
                )

            repo.guardar(elegidos)

            repo.obtener() shouldBe elegidos
        }

    @Test
    fun `el flujo emite al guardar`() =
        runTest {
            val repo = repositorio()

            repo.observar().test {
                awaitItem() shouldBe Ajustes()

                repo.guardar(Ajustes(tema = Tema.CLARO))
                awaitItem().tema shouldBe Tema.CLARO
            }
        }

    // El tema se guarda por nombre. Si llegara un valor que no existe -archivo
    // editado a mano, o una version futura- se vuelve al valor por defecto en vez
    // de reventar: son preferencias, no datos financieros.
    @Test
    fun `un tema desconocido cae al valor por defecto`() {
        val guardados = AjustesGuardados(tema = "ARCOIRIS")

        guardados.aDominio().tema shouldBe Tema.SEGUN_EL_SISTEMA
    }

    @Test
    fun `el DTO y el dominio se corresponden en ambos sentidos`() {
        val original =
            Ajustes(moneda = Moneda("EUR"), primerDiaDelMesFinanciero = 15, tema = Tema.OSCURO)

        original.aGuardados().aDominio() shouldBe original
    }
}
