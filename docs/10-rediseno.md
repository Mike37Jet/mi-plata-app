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

**El cristal tapa el 92 %.** Con el 80 % que se probó primero, el texto
secundario de la barra bajaba a 3,1:1 si pasaba contenido muy opuesto al tema
por detrás. `ContrasteTest` comprueba ese peor caso. El desenfoque no ayuda: una
foto clara desenfocada sigue siendo clara.

## Los PRs

| PR | Qué | Estado |
|---|---|---|
| 1 | **Base**: escala áurea, Inter, paleta, formas, color dinámico apagado, `GrupoDeLista`/`FilaDeLista`, `CifraPrincipal`, cristal | ✅ |
| 2 | **Esqueleto**: barra inferior de cristal con el contenido pasando por detrás, títulos grandes que se encogen al hacer scroll, meses como "Marzo 2026" | este |
| 3 | **Movimientos**: lista agrupada por día ("Jueves 26 de marzo"), transferencias como "Nómina → Visa" | |
| 4 | **Cuentas**: lista agrupada, archivadas plegadas y la copia de seguridad fuera de esta pestaña | |
| 5 | **Plan**: filas limpias que abren una hoja de edición, borrar deslizando con "Deshacer", un solo "+" | |
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
