package md.miliano.secp2p;

import android.util.Log;

import androidx.multidex.MultiDexApplication;

import com.liulishuo.filedownloader.FileDownloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import cn.dreamtobe.filedownloader.OkHttp3Connection;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmSchema;
import md.miliano.secp2p.tor.Tor;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

public class MainApp extends MultiDexApplication {

    private static final String TAG = "MainApp";

    @Override
    public void onCreate() {
        super.onCreate();

        Realm.init(this);
        FileDownloader.setup(this);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name("myrealm.realm")
                .schemaVersion(2)
                .migration((realm, oldVersion, newVersion) -> {

                    Log.d(TAG, "onCreate: Old Version: " + oldVersion + " New Version: " + newVersion);

                    if (oldVersion == 0 && newVersion == 1) {
                        RealmSchema schema = realm.getSchema();
                        schema.get("Contact")
                                .addField("pubKey", byte[].class);
                    }

                    if (oldVersion == 1 && newVersion == 2) {
                        RealmSchema schema = realm.getSchema();
                        Log.d(TAG, "onCreate: changing contact schema and lastOnelineTime");
                        schema.get("Contact")
                                .addField("lastOnlineTime", Long.class);
                    }
                })
                .build();

        Realm.setDefaultConfiguration(config);

        InetSocketAddress proxyAddr = new InetSocketAddress("127.0.0.1", Tor.getHttpPort());
        Proxy proxyTor = new Proxy(Proxy.Type.HTTP, proxyAddr);

        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3)
                .cipherSuites(
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
                )
                .build();

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .proxy(proxyTor)
                .proxySelector(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        return Collections.singletonList(proxyTor);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
                        e.printStackTrace();
                    }
                })
                .connectTimeout(300, TimeUnit.SECONDS)
                .dns(s -> Collections.singletonList(InetAddress.getByAddress(new byte[]{127, 0, 0, 1})))
                .connectionSpecs(Collections.singletonList(spec))
                .readTimeout(300, TimeUnit.SECONDS);
        try {
            TrustManager[] trustManagers = getTrustManagerFactory().getTrustManagers();
            builder.sslSocketFactory(getSSLSocketFactory(), (X509TrustManager) trustManagers[0]);
            builder.hostnameVerifier((s, sslSession) -> true);
        } catch (CertificateException | KeyStoreException | IOException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        FileDownloader.setupOnApplicationOnCreate(this)
                .connectionCreator(new OkHttp3Connection.Creator(builder));
    }

    private TrustManager[] getWrappedTrustManagers(TrustManager[] trustManagers) {
        final X509TrustManager originalTrustManager = (X509TrustManager) trustManagers[0];
        return new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return originalTrustManager.getAcceptedIssuers();
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        try {
                            originalTrustManager.checkClientTrusted(certs, authType);
                        } catch (CertificateException ignored) {
                        }
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        try {
                            originalTrustManager.checkServerTrusted(certs, authType);
                        } catch (CertificateException ignored) {
                        }
                    }
                }
        };
    }

    private TrustManagerFactory getTrustManagerFactory() throws CertificateException, KeyStoreException, IOException,
            NoSuchAlgorithmException {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream keystoreStream = MainActivity.class.getResourceAsStream("/secP2PCert.jks");
        keystore.load(keystoreStream, "secP2PCertPass".toCharArray());
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keystore);
        return tmf;
    }

    private SSLSocketFactory getSSLSocketFactory() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        TrustManager[] wrappedTrustManagers = getWrappedTrustManagers(getTrustManagerFactory().getTrustManagers());
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, wrappedTrustManagers, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

}
