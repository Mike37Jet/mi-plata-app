package com.miplata.core.data.repository

import androidx.room.withTransaction
import com.miplata.core.data.database.MiPlataDatabase
import com.miplata.core.data.mapper.aEntidad
import com.miplata.core.data.mapper.lineasAEntidades
import com.miplata.core.domain.model.ContenidoFinanciero
import com.miplata.core.domain.repository.RepositorioDeRestauracion

/**
 * Sustituye toda la base por el contenido de una copia, en **una sola
 * transaccion**.
 *
 * Es la pieza que convierte "restaurar" en una operacion que no puede salir a
 * medias. Todo -el borrado y cada insercion- ocurre dentro de `withTransaction`:
 * si cualquier paso lanza, SQLite deshace la transaccion entera y la base queda
 * como estaba. No hace falta ningun codigo de "deshacer a mano", que es justo
 * el tipo de codigo que falla el dia que hace falta.
 *
 * Las claves foraneas trabajan a favor: una transaccion de la copia que apunte a
 * una cuenta que la copia no trae hace fallar la insercion, y con ella toda la
 * restauracion. Una copia incoherente no entra, en vez de entrar a medias.
 */
class RoomRepositorioDeRestauracion(
    private val db: MiPlataDatabase,
    private val reloj: Reloj = Reloj.DEL_SISTEMA,
) : RepositorioDeRestauracion {
    override suspend fun reemplazarTodo(contenido: ContenidoFinanciero) {
        // La copia no trae marcas de creacion ni de edicion -no son datos del
        // usuario-, asi que todo queda fechado en el momento de restaurar.
        val ahora = reloj.ahoraEnMillis()
        val vaciado = db.vaciadoDeRestauracionDao()
        val carga = db.cargaDeRestauracionDao()

        db.withTransaction {
            vaciado.borrarTransacciones()
            vaciado.borrarLineasDePlan()
            vaciado.borrarPlanes()
            vaciado.borrarSubcategorias()
            vaciado.borrarCategorias()
            vaciado.borrarCuentas()

            carga.insertarCuentas(contenido.cuentas.map { it.aEntidad(creadaEn = ahora, actualizadaEn = ahora) })
            carga.insertarCategorias(
                ordenadasPorJerarquia(contenido).map { it.aEntidad(creadaEn = ahora, actualizadaEn = ahora) },
            )
            carga.insertarPlanes(contenido.planes.map { it.aEntidad(creadoEn = ahora, actualizadoEn = ahora) })
            carga.insertarLineas(
                contenido.planes.flatMap { it.lineasAEntidades(creadaEn = ahora, actualizadaEn = ahora) },
            )
            carga.insertarTransacciones(
                contenido.transacciones.map { it.aEntidad(creadaEn = ahora, actualizadaEn = ahora) },
            )
        }
    }

    /**
     * Las categorias raiz antes que sus hijas.
     *
     * Una subcategoria apunta a su madre con una clave foranea, asi que la madre
     * tiene que existir cuando se inserta la hija. La copia no garantiza ese
     * orden -sale del orden alfabetico de la base-, y sin esto una subcategoria
     * que empezara por "A" haria fallar la restauracion entera.
     */
    private fun ordenadasPorJerarquia(contenido: ContenidoFinanciero) = contenido.categorias.sortedBy { !it.esRaiz }
}
