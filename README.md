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
La voz de Maelity usa las voces que ya trae Windows (gratis, sin internet). Para mejor voz en español, instala una voz en
*Configuración de Windows > Hora e idioma > Voz*. En otros sistemas Maelity responde por texto.
(`backend/` es la opcion antigua con servidor propio; ya no se usa.)

## 3. Usar
Receta: 4 fragmentos de amatista + 1 ojo de ender (en forma de rombo), o `/maelity summon`.
Mantén **V** para hablar · clic derecho = quedarse/seguirte · `/maelitylang es|en` cambia el idioma · chat: `maelity, ...`

## Pruebas (sin Minecraft)
```
javac -d /tmp/o mod/src/main/java/dev/maelity/core/*.java core-tests/CoreTest.java && java -cp /tmp/o CoreTest
cd backend && node test/smoke.js
```
