package com.miplata.core.domain.usecase

import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.repository.CategoriaRepository

/**
 * Una categoria del juego inicial, con sus subcategorias.
 *
 * El [nombre] llega **ya traducido**: los textos visibles al usuario viven en
 * `strings.xml` (docs/04), no en el dominio. El dominio decide la estructura -que
 * haya una categoria de vivienda con arriendo y servicios dentro- y quien llama
 * pone las palabras.
 */
data class CategoriaSemilla(
    val nombre: String,
    val icono: String = "",
    val color: Int = 0,
    val hijas: List<CategoriaSemilla> = emptyList(),
)

/**
 * Crea las categorias iniciales la primera vez que se abre la app.
 *
 * Una app de finanzas que arranca con la lista de categorias vacia obliga al
 * usuario a inventarse una taxonomia antes de poder registrar su primer gasto.
 * Es la forma mas rapida de que cierre la app y no vuelva.
 *
 * **Es idempotente**: si ya hay categorias no hace nada. Se ejecuta en cada
 * arranque, asi que sin esa comprobacion el usuario acumularia un juego nuevo
 * cada vez que abre la app. Tambien respeta al usuario que las borro todas a
 * proposito... salvo que entonces vuelven a aparecer; ver la nota de abajo.
 */
class SembrarCategoriasPorDefectoUseCase(
    private val repositorio: CategoriaRepository,
    private val ids: GeneradorDeIds,
) {
    /**
     * @return cuantas categorias se crearon. Cero significa que ya habia.
     */
    suspend operator fun invoke(semillas: List<CategoriaSemilla>): Int {
        // Si el usuario borro todas sus categorias a proposito, esto se las
        // devuelve en el siguiente arranque. Es el mal menor frente a la
        // alternativa -guardar una marca de "ya sembrado"- que dejaria sin
        // categorias a quien restaure un backup antiguo donde no las hubiera.
        if (repositorio.cuantasHay() > 0) return 0

        var creadas = 0

        semillas.forEach { semilla ->
            val madre =
                Categoria(
                    id = ids.nuevaCategoriaId(),
                    nombre = semilla.nombre,
                    icono = semilla.icono,
                    color = semilla.color,
                )
            // La madre primero: las hijas la referencian, y la clave foranea de
            // la base no admite apuntar a una fila que aun no existe.
            repositorio.guardar(madre)
            creadas++

            semilla.hijas.forEach { hija ->
                repositorio.guardar(
                    Categoria(
                        id = ids.nuevaCategoriaId(),
                        nombre = hija.nombre,
                        padreId = madre.id,
                        icono = hija.icono,
                        // Las hijas heredan el color de su madre: asi un informe
                        // por categorias se lee de un vistazo.
                        color = semilla.color,
                    ),
                )
                creadas++
            }
        }

        return creadas
    }
}
