package com.miplata.core.data.categorias

import android.content.Context
import com.miplata.core.domain.usecase.SembrarCategoriasPorDefectoUseCase

/**
 * Siembra las categorias iniciales al arrancar.
 *
 * Envuelve el use case para darle los nombres traducidos, que es lo unico que el
 * dominio no puede saber. Se llama desde `Application` en una corrutina: sembrar
 * en el hilo principal retrasaria el primer pixel por algo que al usuario le da
 * igual que tarde 50ms mas.
 */
class SembradorDeCategorias(
    private val context: Context,
    private val sembrar: SembrarCategoriasPorDefectoUseCase,
) {
    suspend fun sembrarSiHaceFalta(): Int = sembrar(CatalogoDeCategorias.porDefecto(context))
}
