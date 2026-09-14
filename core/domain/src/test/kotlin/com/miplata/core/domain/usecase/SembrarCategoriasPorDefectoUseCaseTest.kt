package com.miplata.core.domain.usecase

import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.repository.FakeCategoriaRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private val SEMILLAS =
    listOf(
        CategoriaSemilla(nombre = "Sueldo", color = 1),
        CategoriaSemilla(
            nombre = "Vivienda",
            color = 2,
            hijas =
                listOf(
                    CategoriaSemilla(nombre = "Arriendo"),
                    CategoriaSemilla(nombre = "Servicios"),
                ),
        ),
    )

@DisplayName("SembrarCategoriasPorDefectoUseCase")
class SembrarCategoriasPorDefectoUseCaseTest {
    private val repositorio = FakeCategoriaRepository()
    private val sembrar = SembrarCategoriasPorDefectoUseCase(repositorio, GeneradorDeIdsSecuencial())

    @Nested
    @DisplayName("primera ejecucion")
    inner class PrimeraVez {
        @Test
        fun `crea las madres y sus hijas`() =
            runTest {
                val creadas = sembrar(SEMILLAS)

                creadas shouldBe 4
                repositorio.observarTodas().first().map { it.nombre } shouldBe
                    listOf("Sueldo", "Vivienda", "Arriendo", "Servicios")
            }

        @Test
        fun `las hijas apuntan a su madre`() =
            runTest {
                sembrar(SEMILLAS)

                val todas = repositorio.observarTodas().first()
                val vivienda = todas.first { it.nombre == "Vivienda" }
                val arriendo = todas.first { it.nombre == "Arriendo" }

                vivienda.esRaiz shouldBe true
                arriendo.padreId shouldBe vivienda.id
            }

        // La clave foranea de la base no admite apuntar a una fila que aun no
        // existe, asi que la madre tiene que guardarse antes que sus hijas.
        @Test
        fun `guarda cada madre antes que sus hijas`() =
            runTest {
                sembrar(SEMILLAS)

                val orden = repositorio.observarTodas().first().map { it.id }
                val vivienda = repositorio.observarTodas().first().first { it.nombre == "Vivienda" }
                val arriendo = repositorio.observarTodas().first().first { it.nombre == "Arriendo" }

                orden.indexOf(vivienda.id) shouldBe (orden.indexOf(arriendo.id) - 1)
            }

        // Asi un informe por categorias se lee de un vistazo.
        @Test
        fun `las hijas heredan el color de su madre`() =
            runTest {
                sembrar(SEMILLAS)

                val todas = repositorio.observarTodas().first()
                todas.first { it.nombre == "Arriendo" }.color shouldBe 2
                todas.first { it.nombre == "Servicios" }.color shouldBe 2
            }

        @Test
        fun `una lista vacia no crea nada`() =
            runTest {
                sembrar(emptyList()) shouldBe 0
            }
    }

    @Nested
    @DisplayName("ejecuciones siguientes")
    inner class YaHabia {
        // Se ejecuta en CADA arranque. Sin esto, el usuario acumularia un juego
        // completo de categorias cada vez que abre la app.
        @Test
        fun `no siembra si ya hay categorias`() =
            runTest {
                sembrar(SEMILLAS)

                val segundaVez = sembrar(SEMILLAS)

                segundaVez shouldBe 0
                repositorio.observarTodas().first().size shouldBe 4
            }

        @Test
        fun `una sola categoria basta para no volver a sembrar`() =
            runTest {
                repositorio.guardar(
                    com.miplata.core.domain.model
                        .Categoria(id = CategoriaId("mia"), nombre = "La mia"),
                )

                sembrar(SEMILLAS) shouldBe 0
                repositorio.observarTodas().first().size shouldBe 1
            }
    }
}
