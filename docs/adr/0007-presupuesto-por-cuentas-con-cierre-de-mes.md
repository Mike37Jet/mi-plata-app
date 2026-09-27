# 0007 — Presupuesto por cuentas, con cierre de mes contra el saldo del banco

- **Estado:** Propuesto
- **Fecha:** 2026-09-27

## Contexto

Con la 1.0 instalada, el uso real mostró un problema de fondo: **la realidad
de la app depende de anotar cada movimiento, y nadie lo anota todo**. El día que
se olvida un gasto, el resumen miente. Desde ahí la app deja de merecer
confianza, y deja de usarse.

El método que sí funcionaba, en una hoja de cálculo, es otro. Trabaja con las
cuentas reales del banco (Banco Pichincha):

| Cuenta | Qué recibe | Para qué |
|---|---|---|
| **Normal** | Todos los ingresos (el sueldo, ~633) | Gastos fijos: arriendo, luz, agua, internet, comida, deudas |
| **Libertad financiera** | 10% del ingreso | Solo acumula. **No debería bajar nunca** |
| **Ahorros** | 10% del ingreso | Gastos planeados menores: pasajes, Spotify, plan de megas, corte de cabello |
| **Diversión** | 10% del ingreso | Ocio |
| **Entrenamiento** | 10% del ingreso | Formación: la suscripción a Claude, cursos… |

Al terminar el mes se compara, por cuenta, lo planeado con lo que de verdad
quedó. Lo que sobra o falta en cada cuenta **se queda en ella**, y el mes
siguiente arranca con ese saldo. Si Normal no alcanza, se cubre con dinero de
Ahorros, Diversión o Entrenamiento.

La fuente de verdad de ese método es el **saldo de cada cuenta en el banco**.
Se consulta en segundos y no se equivoca. El detalle de cada gasto es útil
cuando existe, pero no es imprescindible.

Restricciones que se mantienen:

- Sin internet: no hay conexión con el banco. El saldo lo copia el usuario.
- El saldo de una cuenta **se deriva**, no se guarda (docs/03): un campo de
  saldo editable es una segunda fuente de verdad que se desincroniza.
- El plan de cada mes es un snapshot propio (ADR 0003).

## Decisión

El plan se organiza **por cuenta**, y la realidad se mide con un **cierre de
mes** en el que el usuario copia el saldo real de cada cuenta. Anotar
movimientos pasa a ser **opcional**: sirve para saber *en qué* se fue el
dinero, no para saber *cuánto*.

En concreto:

1. **Roles de cuenta.**
   - Una cuenta es la **principal**: recibe los ingresos (Normal).
   - Las demás pueden ser **sobres**. Cada sobre recibe **un porcentaje del
     ingreso o un monto fijo**, a elegir por cuenta. Por defecto es el 10%.
   - Es una cosa o la otra, nunca las dos. En el modelo es un tipo cerrado,
     `Reparto.Porcentaje(10)` o `Reparto.Monto(50)`, así que tener ambos a la
     vez no se puede representar. No hace falta validarlo ni mostrar un error.
     En pantalla es un selector "% / $" con un solo campo.
   - Un sobre puede marcarse como **intocable** (Libertad financiera). Cualquier
     salida de esa cuenta se señala aparte y nunca se sugiere para cubrir a
     otra.
2. **Reparto.**
   - Del ingreso planeado, la app calcula lo que va a cada sobre. Son líneas de
     plan de un tipo nuevo, **reparto**, que representan una transferencia de la
     principal al sobre: no son gasto.
   - La app dice cuánto hay que transferir en el banco.
3. **Plan por cuenta.** Cada línea de gasto pertenece a una cuenta
   (`LineaDePlan.cuentaId`, que ya existe). Por cuenta, la app muestra:
   - con cuánto empieza el mes;
   - qué le entra;
   - qué planeas gastar desde ella;
   - **con cuánto debería terminar**.
4. **Cierre de mes.**
   - El usuario escribe el saldo real de cada cuenta, copiado del banco.
   - Por cada cuenta donde el saldo derivado no coincide, se crea **un
     movimiento de ajuste "Sin detalle"** por la diferencia. El saldo sigue
     siendo derivado, con la misma fórmula, y ahora cuadra con el banco.
   - El resumen compara, por cuenta, el saldo esperado con el real.
5. **Cobertura.**
   - Si en el cierre la principal quedó por encima de lo esperado y un sobre
     por debajo, la app pregunta si fue una cobertura, por ejemplo "¿moviste 30
     de Diversión a Normal?". Si la respuesta es sí, se registra como
     transferencia, no como dos ajustes.
   - Durante el mes, cubrir la principal es una transferencia normal. La app
     propone los sobres que no son intocables.
6. **Arrastre.** El mes siguiente parte del saldo real del cierre, en cada
   cuenta. Lo que sobró o faltó ya está en ese saldo; el plan nuevo lo muestra
   como "vienes con…" junto al reparto del mes.

## Alternativas consideradas

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| Seguir como está: la realidad es la suma de lo anotado | Sin cambios | Es justo lo que falló: en cuanto falta un movimiento, todo miente | Rechazada |
| Leer las notificaciones o SMS del banco para anotar solo | Casi sin esfuerzo, y funciona sin internet | Depende del formato de cada banco. Aun así, un movimiento perdido descuadra | Aplazada: complementa esto, no lo sustituye |
| Guardar el saldo real como punto de control (saldo = último cierre + movimientos posteriores) | El cierre queda explícito | Dos formas de calcular un saldo. Un movimiento anotado tarde, con fecha anterior al cierre, se pierde sin avisar | Rechazada |
| **Cierre con movimiento de ajuste "Sin detalle"** | Una sola fórmula de saldo. Los resúmenes lo cuentan sin código especial. Lo no anotado queda visible con un nombre honesto | Añade movimientos que el usuario no escribió; hay que marcarlos y dejar deshacer el cierre | **Aceptada** |
| Reparto con dos campos opcionales, porcentaje y monto | Fácil de guardar | Permite el estado "los dos" y el estado "ninguno", que hay que validar en cada sitio | Rechazada: el tipo cerrado lo hace imposible |
| Sobres virtuales dentro de una sola cuenta | No depende de tener varias cuentas en el banco | No es como funciona el dinero de verdad aquí: las cuentas existen en el banco y se comparan con él | Rechazada |

## Consecuencias

- **Lo bueno.** El resumen cuadra con el banco aunque no se anote nada. La
  línea "Sin detalle" dice cuánto se escapó y dónde mirar. Anotar un gasto
  concreto ahora *reduce* "Sin detalle", un incentivo en lugar de una
  obligación.
- **El modelo crece.**
  - `Cuenta`: rol (principal / sobre), reparto (porcentaje o monto) e intocable.
  - `TipoDeLinea`: reparto, con cuenta de destino.
  - `Transaccion`: una marca de ajuste de cierre.
  - Hay que registrar qué meses están cerrados.
  - El formato de la copia de seguridad sube de versión, con su migración y su
    test (docs/05).
- **El cierre es deshacible.** Reabrir un mes borra sus ajustes y, si ya se
  cerraron, los de los meses posteriores, porque parten de él.
- **La bienvenida cambia.** Propone crear las cuentas del método (principal +
  sobres) en lugar de una sola cuenta.
- **Lo que ya existe sigue sirviendo.**
  - La desviación por línea sigue funcionando para quien anote.
  - El resumen gana una vista por cuenta.
  - Los tipos de línea actuales no desaparecen.
- **Queda para después:** leer las notificaciones del banco para que anotar
  cueste un toque (la alternativa aplazada arriba).
