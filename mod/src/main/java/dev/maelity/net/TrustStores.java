package dev.maelity.net;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Confianza TLS = certificados de Java + certificados de Windows (Windows-ROOT).
 * Arregla "PKIX path building failed" cuando un antivirus, VPN o proxy reemplaza el certificado
 * y Windows ya confia en el, pero el Java incluido en Minecraft no.
 */
public final class TrustStores {
    public static SSLContext javaPlusWindows() {
        try {
            List<X509TrustManager> tms = new ArrayList<>();
            X509TrustManager def = managerFor(null);
            if (def != null) tms.add(def);
            try {
                KeyStore win = KeyStore.getInstance("Windows-ROOT");
                win.load(null, null);
                X509TrustManager w = managerFor(win);
                if (w != null) tms.add(w);
            } catch (Exception ignored) {
                // no es Windows: solo los certificados de Java
            }
            if (tms.isEmpty()) return null;
            X509TrustManager composite = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    tms.get(0).checkClientTrusted(chain, authType);
                }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    CertificateException first = null;
                    for (X509TrustManager tm : tms) {
                        try { tm.checkServerTrusted(chain, authType); return; }
                        catch (CertificateException e) { if (first == null) first = e; }
                    }
                    throw first != null ? first : new CertificateException("no trust managers");
                }
                @Override public X509Certificate[] getAcceptedIssuers() {
                    List<X509Certificate> all = new ArrayList<>();
                    for (X509TrustManager tm : tms) all.addAll(List.of(tm.getAcceptedIssuers()));
                    return all.toArray(new X509Certificate[0]);
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{composite}, null);
            return ctx;
        } catch (Exception e) {
            return null;
        }
    }

    private static X509TrustManager managerFor(KeyStore ks) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (TrustManager tm : f.getTrustManagers()) if (tm instanceof X509TrustManager x) return x;
        return null;
    }

    private TrustStores() {}
}
