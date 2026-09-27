# 10 — Rediseño visual

El objetivo es que la app se vea y se sienta como una app insignia de Apple
**sin dejar de ser una app de Android**. Se adopta el lenguaje visual de Apple:
tipografía, espacio, listas agrupadas, cristal y sobriedad de color. Los gestos
y la navegación siguen siendo los de Android.

Se construye en varios PRs pequeños. Este documento es el plano de todos.

## Auditoría del estado anterior (heurísticas de Nielsen)

Hecha sobre las cuatro pestañas con los datos de la copia de referencia.

| # | Heurística | Hallazgo |
|---|---|---|
| 1 | Visibilidad del estado | Resumen da cifras sueltas y no muestra cuánto del plan se lleva gastado. "¿Me alcanza?" no se ve de un vistazo. |
| 2 | Relación con el mundo real | Meses y fechas en formato de base de datos (`2026-03`, `2026-03-26`). Una transferencia se ve como "Sin categoría −€100", y su día suma €0.00 en verde. |
| 3 | Control y libertad | Borrar una línea del plan es una papelera sin deshacer. |
| 4 | Consistencia | Resumen y Plan tienen la misma cabecera: mes, cifra verde gigante y etiqueta. Los importes salen como "2000.00" en un sitio y "€2,000.00" en otro. El "+" crea cosas distintas según la pantalla, y en Plan no existe. |
| 5 | Prevención de errores | La papelera, siempre visible junto al interruptor, invita al toque accidental. |
| 6 | Reconocer antes que recordar | La copia de seguridad está escondida como texto en una esquina de Cuentas. |
| 8 | Estético y minimalista | Cuatro "+ Añadir" y cuatro divisores en Plan. Una tarjeta por movimiento, con más peso el marco que el dato. Verde en totales que no son ingresos. Las cuentas archivadas ocupan el mismo sitio que las activas. |
| — | Affordances | Los campos del plan parecen texto con una raya debajo. Las filas que se pueden tocar no lo indican. |

## Decisiones

| Decisión | Por qué |
|---|---|
| **Inter**, no SF Pro | La licencia de SF Pro la limita a plataformas de Apple. Inter es la libre (SIL OFL) más parecida. Va empaquetada, en tres pesos (Regular, Medium y SemiBold), porque la app no tiene permiso de internet. La licencia está en `core/designsystem/LICENCIA-INTER.txt`. |
| Aspecto de Apple, **gestos de Android** | Copiar la navegación de iOS en Android rompería la heurística 4 de Nielsen: quien usa Android espera el "atrás" del sistema y el atrás predictivo. |
| **Haze 1.6.10** para el cristal | Es el desenfoque real de lo que pasa por detrás, en Android 12 o superior. Por debajo queda solo el velo. La 2.x exige Compose 1.12 y Kotlin 2.4: subirlos es otro trabajo. |
| **Color dinámico apagado** | El lenguaje busca un solo acento sobre neutros, y el color dinámico teñía toda la interfaz del tono del fondo de pantalla. Los colores de dinero ya no participaban antes. |
| Rediseño en **varios PRs** | Uno por pieza, revisable y reversible (ADR 0005). |

## La escala áurea

**Espacio:** 8 × φⁿ → **8 · 13 · 21 · 34 · 55 · 89 dp** (`Espacio`).

| Uso | Valor |
|---|---|
| Márgenes laterales, relleno horizontal de una fila | 21 |
| Relleno vertical de una fila, separación entre hermanos | 13 |
| Entre secciones | 34 |
| Altura mínima de una fila; aire sobre la cifra principal | 55 |
| Esquinas: grupos / hojas | 13 / 21 |

**Tipografía:** cada tamaño es el anterior × √φ: **13 · 17 · 21 · 27 · 34 · 44
· 55 sp**. Dos pasos dan φ exacto (13 → 21 → 34 → 55), y esos son los niveles
que marcan jerarquía: texto secundario, sección, título grande y cifra
principal. Sale casi igual que la escala de iOS (13, 17, 22, 28, 34).

**Proporción vertical de las pantallas con cifra principal:** la zona de la
cifra ocupa el 38,2 % de la altura útil (1/φ²) y el contenido el 61,8 %.

Sobre la proporción áurea, para ser honestos: lo que se percibe como armonía es
que todo salga de **una misma escala** con saltos lo bastante grandes para leerse
como jerarquía. No hay evidencia sólida de un efecto "subconsciente" propio del
número 1.618. φ es una buena fuente de esa escala, no una propiedad mágica.

`EscalaAureaTest` impide que la escala se degrade: un padding "ajustado" a 16
porque se veía mejor rompe el build.

## Color

Neutros al estilo de iOS: fondo agrupado gris claro (#F2F2F7) con celdas
blancas, o negro con celdas grafito (#1C1C1E). Un solo acento, el verde azulado.
La selección (pestaña activa, chip elegido) usa un tinte del acento.

**El texto secundario no es el de Apple.** El `secondaryLabel` de iOS (#8A8A8E)
se queda en 3,08:1 sobre el fondo agrupado y no llega al 4,5:1 de WCAG AA
(docs/09). Se usan #636366 en claro y #98989F en oscuro, los más claros que
pasan en todos los fondos.

**El cristal tapa el 55 %, desenfoca 34 dp, y sobre él solo va texto
principal.** Llegar ahí costó tres intentos:

1. **92 %:** las pestañas inactivas iban en gris secundario, como en iOS, y ese
   gris pedía un velo casi opaco para leerse.
2. **70 %:** con todas las etiquetas en el color principal, pero exigiendo que se
   leyeran con **blanco puro** detrás. La barra apenas dejaba ver nada.
3. **55 %:** el peor caso es lo que la app de verdad pinta detrás de la barra:
   - sus colores sólidos (acento, contenedores, celdas y colores de dinero, estos
     tratados como sólidos porque la cifra principal es gruesa);
   - su texto, que con 34 dp de desenfoque queda como una mancha con, como mucho,
     la mitad de tinta.

   El caso más exigente es el gris de gasto bajo el tema oscuro, que pide un 49 %.

La pestaña activa se distingue por la pastilla, el color del icono y la
seminegrita. `ContrasteTest` comprueba cada caso: con un velo del 45 % falla con
el par exacto ("ingreso detrás: 4,38").

En el tema oscuro, más transparencia se ve como una barra más negra, porque lo
que pasa por detrás es sobre todo fondo negro. El cristal se nota cuando pasa
contenido de color, y mucho más en el tema claro.

**El aspecto de Liquid Glass sin su refracción.** Lo que hace que el cristal
parezca vidrio y no una capa gris es la luz:

- **Línea de brillo** en el canto superior, más intensa en el centro: 50 % de
  blanco en oscuro y 90 % en claro. No va detrás de ningún texto.
- **Reflejo** que baja desde el canto y se desvanece antes de la mitad. Va detrás
  de las etiquetas, así que tiene como mucho un 4 % de blanco (`REFLEJO_MAXIMO`).
  Con el 8 %, el texto del tema oscuro bajaba a 4,42:1. `ContrasteTest` lo cuenta
  entero.

La refracción de verdad (la lente que deforma lo que hay detrás) queda fuera. En
Android necesita shaders AGSL de Android 13, y la única librería que la hace para
Compose (Backdrop, de Kyant) exige Compose 1.10 y Kotlin 2.3. El proyecto va por
Compose 1.9 y Kotlin 2.2. Tampoco se cambió la forma de la barra: sigue de lado a
lado, no en cápsula flotante.

## Los PRs

| PR | Qué | Estado |
|---|---|---|
| 1 | **Base**: escala áurea, Inter, paleta, formas, color dinámico apagado, `GrupoDeLista`/`FilaDeLista`, `CifraPrincipal`, cristal | ✅ |
| 2 | **Esqueleto**: barra inferior de cristal con el contenido pasando por detrás, títulos grandes que se encogen al hacer scroll, meses como "Marzo 2026" | ✅ |
| 3 | **Movimientos**: lista agrupada por día ("Hoy", "Ayer", "Jueves, 26 de marzo"), transferencias como "Nómina → Visa" | ✅ |
| — | **Ajustes**: tema (según el teléfono, claro u oscuro) y la copia de seguridad, que sale de Cuentas; engranaje en las cuatro pestañas | ✅ |
| 4 | **Cuentas**: lista agrupada y archivadas plegadas | ✅ |
| 5 | **Plan**: filas limpias que abren una hoja de edición, borrar deslizando con "Deshacer", un solo "+" | este |
| 6 | **Resumen**: cifra principal con su barra de progreso, "Comida · €42 de €400" con minibarras | |

## El esqueleto (PR 2)

- **`PantallaConTituloGrande`** es el armazón de las cuatro pestañas. Tiene un
  título grande que se encoge al hacer scroll. Mide 55 dp plegado y 110 dp
  desplegado, en lugar de los 152 dp de Material, que dejaban casi un quinto de
  pantalla vacío encima.
- **La barra inferior flota sobre el contenido** en `:app`: un `Box`, no un
  `Scaffold`. Su altura se mide y llega a las pantallas como
  `LocalEspacioDeLaBarraInferior`. Con ese valor, cada lista deja al final el
  hueco que tapa la barra y el botón "+" sube por encima.
  `PantallaConTituloGrandeTest` lo comprueba. Al principio usaba una barra de
  80 dp, pero el margen extra del final (89 dp) ya la cubría solo y el test no
  veía si faltaba el hueco. Ahora usa 120 dp, que es lo que mide de verdad.
- **Comprobar el cristal tiene truco.** Detrás de la barra solo hay contenido si
  la lista es más larga que la pantalla y no se ha llegado al final: al final,
  lo que queda debajo es el hueco vacío. Además, el dump de accesibilidad no
  sirve para verlo, porque Compose recorta los límites de cada nodo por lo que
  tiene dibujado encima. Se comprobó alargando el Plan con líneas vacías y
  bajando el velo al 30 %: el verde de un interruptor que pasa por detrás se ve
  difuminado a través de la barra.
- **Meses y fechas en palabras** (`nombreDelMes`, `fechaLarga`), siempre en
  español, como la app. Un solo `SelectorDeMes` sustituye a las tres copias que
  había.

## Ajustes

- **Acceso:** un engranaje arriba a la derecha en las cuatro pestañas, siempre en
  el mismo sitio. Si solo estuviera en una pestaña, sería igual de difícil de
  encontrar que lo era la copia en Cuentas.
- **Apariencia:** según el teléfono (por defecto), claro u oscuro, como las apps
  modernas. El cambio se aplica al momento. El tema viaja en el backup como
  cualquier otro ajuste, y si una copia trae uno que no gusta, se cambia aquí.
  Antes el campo existía, pero no había pantalla para cambiarlo: una copia con
  el tema oscuro dejaba la app atrapada en oscuro.
- **Copia de seguridad:** una fila con la fecha de la última copia, o "Aún no has
  hecho ninguna".
- **Pantallas internas** (Ajustes, Copia y Restaurar): usan el mismo armazón con
  título grande y una flecha para volver **fija** arriba. Antes, en Copia y
  Restaurar, la flecha iba dentro de lo que se desplaza, y al bajar por el
  formulario no quedaba ninguna forma visible de volver.
- **Pestañas desde una pantalla interna.** La barra marca la pestaña desde la que
  se entró, y tocarla lleva a su raíz. Antes, desde la copia, tocar "Resumen"
  devolvía a la copia: al salir se guardaba la pila de pantallas internas, y al
  volver a la pestaña se restauraba. Ahora esa pila no se guarda al salir.
- La moneda y el día de inicio del mes siguen fuera. Cambiar la moneda con
  cuentas ya creadas pide decidir qué pasa con ellas, y eso merece su propio ADR.

## Movimientos (PR 3)

- **Un grupo por día**, con cabecera "Hoy", "Ayer" o "Jueves, 26 de marzo" y el
  total del día a la derecha. El total sale en verde solo si el día fue a más.
  Un día con solo transferencias no muestra total: antes enseñaba "€0.00" en
  verde, como un día de ingresos.
- **Filas de la lista agrupada**, sin chevron (todas llevan al editor), con el
  tipo en un círculo de 34 dp tintado: entra, sale o cambia de cuenta.
- **Transferencias:** "Cuenta nómina → Visa", sin signo y en color neutro. Antes
  salían como "Sin categoría −€100": una transferencia no tiene categoría, y el
  signo la hacía pasar por un gasto.
- **Nombres de lo ya anotado:** se resuelven con todas las cuentas y todas las
  líneas del plan. Archivar una cuenta o desactivar una línea las quita del
  editor, no de lo ya anotado. Antes una transferencia a una cuenta archivada
  salía sin destino, y un gasto de una línea desactivada, como "Sin categoría".
- **Por el camino salió un fallo grave de datos,** arreglado en su propio PR:
  editar el plan soltaba todos los movimientos del mes de sus líneas.

## Cuentas (PR 4)

- **Lista agrupada** con las cuentas activas. Cada una lleva el icono de su tipo
  (efectivo, banco, tarjeta, ahorro o inversión) en el mismo círculo que
  Movimientos, que ahora es `IconoEnCirculo` en el design system.
- **Archivadas plegadas** bajo una fila "Archivadas" con su número, que se
  despliega al tocarla y anuncia a un lector de pantalla si está desplegada.
  Antes ocupaban el mismo sitio que las activas.
- **Sin verde en los saldos.** Un saldo positivo no es un ingreso, y en esta app
  el verde dice "entró dinero". Saldos y total van en el color del texto; solo lo
  negativo, en rojo.

## Plan (PR 5)

- **Filas limpias:** el nombre a la izquierda y el importe con su moneda a la
  derecha, agrupadas por tipo con el total junto al título. Se ocultan los tipos
  sin líneas. Una línea desactivada dice "Este mes no cuenta" en lugar de
  mostrar un interruptor. Antes cada fila era un formulario (dos campos, un
  interruptor y una papelera) que parecía texto subrayado.
- **Una hoja de edición** para crear y editar. Nada se guarda hasta pulsar
  Guardar, y cancelar deja el plan como estaba. Editar conserva lo que la hoja
  no muestra: categoría, cuenta y día de cobro. Con el plan vacío, la hoja
  propone "Ingreso".
- **Un solo "+"**, como en las demás pestañas, en lugar de uno por sección.
- **Borrar deslizando, con "Deshacer".** También se puede desde la hoja y como
  acción de accesibilidad, porque deslizar no se descubre ni se puede hacer con
  un lector de pantalla. "Deshacer" devuelve la línea a su sitio y **vuelve a
  enganchar sus movimientos**: la base los suelta al borrarla (`ON DELETE SET
  NULL`), y volver a crearla no bastaba.
- **Filas con clave:** sin `key(linea.id)`, Compose reutilizaba el estado del
  deslizamiento por posición, y la línea siguiente a una borrada se quedaba como
  una franja roja vacía. Lo encontró Miguel en el emulador.

## Plano por pantalla

- **Resumen:** "Te quedan **€1.599,50**", con "de €2.000 planeados" debajo y
  una barra de progreso. Después "Plan y realidad" como filas con minibarra, y
  las desviaciones.
- **Plan:** grupos por tipo de línea. En cada fila, el nombre a la izquierda y
  el importe con moneda a la derecha. Tocar abre una hoja. Las líneas
  desactivadas en gris con "Este mes no". Borrar deslizando, con "Deshacer". Un
  único "+" arriba.
- **Movimientos:** "Entró / Salió" arriba. Cabecera fija por día. Filas de 55 dp
  con el icono de la categoría en un círculo de 34.
- **Cuentas:** el total, un grupo con las activas y una fila "Archivadas (n)".
- **Editores:** hojas con esquinas de 21 dp y el importe grande y centrado.
