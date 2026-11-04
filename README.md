# Maelity (mod de Minecraft, NeoForge 1.21.1)

Compañera flotante con IA: conversa por voz (tecla **V**, español/inglés), cambia de personalidad según cómo fluya la charla,
te sigue, te guía y encuentra minerales, estructuras y biomas según la **seed** de tu mundo. Si la molestas, puede darte un golpe (con límites).

## Estructura
- `mod/` – el mod (Java, NeoForge). Es lo que se compila.
- `backend/` – servidor Node que guarda las claves de IA (chat, voz a texto, texto a voz). Sin dependencias.
- `core-tests/` – pruebas del núcleo sin Minecraft. `.github/workflows/build.yml` – compila el .jar en GitHub.
- `legacy/` – versión anterior archivada (no se usa).

## 1. Obtener el .jar
**Local** (Java 21): `cd mod && ./gradlew build` → `mod/build/libs/maelity-1.0.0.jar`.
**Sin instalar nada**: sube esta carpeta a GitHub; la pestaña *Actions* compila y deja el .jar como artefacto `maelity-mod`.
Antes de compilar, confirma las versiones de `mod/gradle.properties` (`moddev_version`, `neo_version`).

## 2. Que cada jugador use su propia clave (todo gratis, sin servidor)
El mod habla directo con Groq; no hace falta backend. Cada jugador:
1. Crea una cuenta gratis en https://console.groq.com y en *API Keys* pulsa **Create API Key** (empieza con `gsk_`).
2. Pégala en el juego: **Mods > Maelity > Config > IA (conversación) > Clave de Groq**, o escribe `/maelity key gsk_TU_CLAVE`
3. Listo: mantén **V** para hablar o escribe `maelity, hola`.
La voz de Maelity usa Piper por defecto (gratis, sin internet; se descarga sola la primera vez). Mira la sección Voz más abajo.
(`backend/` es la opcion antigua con servidor propio; ya no se usa.)


## Lo que Maelity sabe hacer
- **Regalos (como en creativo):** pídele cosas ("Maelity, ¿me das pan?"). Si te da algo o no depende de su humor y de cuánto te quiere:
  amigable es generosa, juguetona a veces te gasta una broma, protectora te da comida, manzanas doradas o un tótem si estás en peligro,
  sarcástica solo da cosas baratas, enfadada te da algo barato de mala gana (y furiosa no te da nada). Las cosas valiosas tienen
  espera (raras: 5 min, épicas y de comandos: 30 min). Puede dar **todo lo de creativo**, incluso objetos de comandos
  (bloques de comandos, huevos de spawn, barreras...), pero solo si te quiere muchísimo. Se limita en *Config > Comportamiento > Regalo más valioso*.
- **Combate:** si te atacan, decide sola: te defiende, huye (creeper a punto de explotar, jefes, demasiados monstruos o ella muy herida),
  o, si está muy enfadada contigo, te deja solo salvo que estés a punto de morir. También puedes decirle "ataca" o "huye". No muere en combate: huye antes.
- **Atenta a todo:** conoce tu vida, comida, armadura, inventario, monstruos cerca y lo que pasó hace poco; avisa cuando cae la noche,
  te da pan si tienes hambre, antorchas si estás a oscuras bajo tierra, y comenta por su cuenta.
- **Apariencia (Mods > Maelity > Config > Apariencia):** todo se elige con botones (clic para cambiar). Forma *cubo* o *pelota redonda*,
  5 caras (clásica, tierna, gatito, robot, mínima) y un color por cada humor (16 colores para elegir; por defecto amigable amarillo,
  juguetona rosa, sarcástica verde menta, protectora azul, misteriosa morado, enfadada rojo). El color cambia suavemente y suelta
  partículas al cambiar de humor. También puedes usar un color fijo.
- **Lenguaje según la edad (Config > Lenguaje (edad)):** por defecto Maelity **no dice groserías** (enfadada solo usa insultos suaves
  como "inútil" o "torpe"). Para activarlas pon tu edad y activa *Groserías cuando está muy enfadada*. La primera edad que pongas queda
  guardada: si es menor de 18, las groserías quedan bloqueadas para siempre en esa instalación, aunque luego cambies la edad.
  Para reiniciarlo hay que borrar la carpeta `.minecraft/maelity`.
- **Memoria que se guarda sola:** cada pocos mensajes Maelity resume lo importante (tu nombre, tus gustos, dónde está tu base,
  lo que te pasó...) y lo guarda en `saves/<mundo>/maelity/`. Lo recuerda en las siguientes partidas. Di "recuerda que..." para
  que guarde algo al instante. `/maelity memory` muestra lo que recuerda y `/maelity forget` lo borra.
- **Voz:** ver la sección *Voces* más abajo.
- **Luz de noche:** de noche, bajo tierra o con tormenta, Maelity brilla e ilumina a su alrededor (pone un bloque de luz invisible
  que la sigue y lo quita al moverse). Se apaga en *Config > Comportamiento > Brilla e ilumina de noche*.
- **Movimiento tranquilo (Config > Movimiento):** si estás quieto se queda quieta mirándote a la cara; solo te sigue cuando te alejas.
  - *Cómo te sigue:* `calm` (por defecto: se acerca por su lado, sin cruzarse delante) o `behind` (se pone detrás de ti).
  - *Distancia* (2–10 bloques) y *velocidad* (0.5–2.0).
  - Dile "muévete" / "apártate" y se quita del medio unos segundos. Si activas *Se aparta sola si la miras con un bloque*,
    también se aparta sola (apagado por defecto).
- **Una sola Maelity:** si se queda muy atrás (por ejemplo peleando), reaparece a tu lado. `/maelity summon` trae a la que ya tienes
  aunque esté lejos o en otra dimensión, y si alguna vez aparecen dos, se fusionan solas en una.
- Todo se ajusta en **Mods > Maelity > Config > Comportamiento**.

## Voces (Mods > Maelity > Config > Voz)
Todo se elige con botones. Primero el **motor de voz** y luego si la voz es de **mujer** u **hombre**. Por defecto no hay que poner
ninguna clave: se usa Piper, que se instala solo. Las claves e ID solo hacen falta si eliges ElevenLabs o Fish Audio.

| Motor | Calidad | Costo |
|---|---|---|
| **`piper`** (por defecto) | Buena, natural | Gratis siempre y sin internet. La primera vez descarga sola unos 80 MB (programa + voz) en `.minecraft/maelity/piper/` y avisa en el chat. Solo Windows. |
| **`elevenlabs`** | Muy realista | Plan gratis con unos 10 000 créditos al mes que se renuevan cada mes (unos 10–20 minutos de voz). Pon tu clave de elevenlabs.io en *Clave de ElevenLabs*. |
| **`fish`** | Muy realista | El modelo `s2.1-pro-free` es gratis solo por tiempo limitado (según Fish Audio, hasta el 30 de noviembre de 2026); después es de pago. Pon tu clave de fish.audio en *Clave de Fish Audio*. |
| **`windows`** | Robótica | Gratis. Usa las voces que ya trae Windows. |

Si un motor falla (sin clave, sin créditos o sin internet), Maelity avisa en el chat y usa la voz de Windows, así nunca se queda muda.

Opcional:
- **Piper:** puedes elegir otra voz en *Voz Piper en español / inglés* (México, Argentina, España, EE. UU., Reino Unido).
  *Automática* = según el género.
- **ElevenLabs:** *ID de voz ElevenLabs* para usar otra voz. Vacío = automática según el género.
- **Fish Audio:** copia el ID de una voz de su biblioteca en *ID de voz Fish Audio* para elegir mujer u hombre. Vacío = voz por defecto.

## 3. Usar
Receta: 4 fragmentos de amatista + 1 ojo de ender (en forma de rombo), o `/maelity summon`.
Mantén **V** para hablar · clic derecho = quedarse/seguirte · `/maelitylang es|en` cambia el idioma · chat: `maelity, ...`

## Pruebas (sin Minecraft)
```
javac -d /tmp/o mod/src/main/java/dev/maelity/core/*.java core-tests/CoreTest.java && java -cp /tmp/o CoreTest
cd backend && node test/smoke.js
```
