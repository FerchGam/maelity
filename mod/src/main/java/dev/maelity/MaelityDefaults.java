package dev.maelity;

/**
 * Valores incluidos en el jar para que el jugador NO tenga que configurar nada.
 * ANTES DE PUBLICAR: pon aqui la URL donde despliegues /backend y el mismo MAELITY_TOKEN.
 * (Un token dentro de un mod no es secreto: sirve para filtrar trafico casual; la proteccion real
 * es el limite de peticiones del backend y que las claves API viven solo en el servidor.)
 */
public final class MaelityDefaults {
    public static final String BACKEND_URL = "http://127.0.0.1:8787";   // <-- CAMBIAR
    public static final String BACKEND_TOKEN = "cambia-esto-por-un-secreto-largo"; // <-- CAMBIAR

    private MaelityDefaults() {}
}
