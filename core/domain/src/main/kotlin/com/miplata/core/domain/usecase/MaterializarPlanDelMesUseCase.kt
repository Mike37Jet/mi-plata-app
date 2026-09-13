package com.miplata.core.domain.usecase

import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.PlanMensual

/**
 * Crea el plan de un mes copiando las lineas activas del mes anterior.
 *
 * Es lo que hace que la app se sienta flexible sin reescribir la historia. Al
 * abrir un mes nuevo el usuario se encuentra su plan ya armado -su arriendo, su
 * internet, su estimacion de comida- y solo ajusta lo que haya cambiado, en vez
 * de teclearlo todo otra vez.
 *
 * **Las lineas copiadas son lineas nuevas, con identificadores nuevos**
 * (docs/adr/0003). Esa es la diferencia entre un snapshot y una plantilla
 * compartida: si el arriendo sube en marzo, febrero conserva el valor que tenia.
 * Con lineas compartidas, editar marzo reescribiria febrero y comparar dos meses
 * dejaria de significar nada.
 *
 * Solo se copian las lineas **activas**. Una linea desactivada es un gasto que
 * este mes no toca, y arrastrarla mes tras mes iria llenando el plan de ruido.
 *
 * Deliberadamente **no** se guarda de que linea viene cada copia. Enlazar una
 * linea con su equivalente del mes pasado haria falta para decir "el arriendo
 * subio de 400 a 450", pero esa comparativa es de la v0.3 y todavia no sabemos
 * si el eje correcto es la linea o la categoria. Anadir el enlace despues es una
 * migracion asumible; inventarse ahora el modelo de la comparativa, no.
 */
class MaterializarPlanDelMesUseCase(
    private val ids: GeneradorDeIds,
) {
    /**
     * @param mes el mes que se quiere materializar.
     * @param planAnterior el plan del que copiar, o `null` si no hay ninguno,
     *   en cuyo caso el mes arranca vacio. No tiene por que ser el mes
     *   inmediatamente anterior: si el usuario no abre la app en tres meses, se
     *   copia del ultimo plan que exista.
     */
    operator fun invoke(
        mes: Mes,
        planAnterior: PlanMensual?,
    ): PlanMensual =
        PlanMensual(
            id = ids.nuevoPlanId(),
            mes = mes,
            lineas =
                planAnterior
                    ?.lineasActivas
                    .orEmpty()
                    .map { it.copy(id = ids.nuevaLineaId()) },
        )
}
