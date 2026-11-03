package dev.maelity.net;

import dev.maelity.MaelityConfig;
import dev.maelity.core.BackendClient;

/** Acceso al cliente HTTP segun la configuracion (se reconstruye si cambia la URL/token). */
public final class Backend {
    private static volatile BackendClient cached;
    private static volatile String cachedKey = "";

    public static BackendClient client() {
        String url = MaelityConfig.COMMON.backendUrl.get();
        String token = MaelityConfig.COMMON.backendToken.get();
        String key = url + "\n" + token;
        if (cached == null || !key.equals(cachedKey)) {
            cached = new BackendClient(url, token);
            cachedKey = key;
        }
        return cached;
    }

    private Backend() {}
}
