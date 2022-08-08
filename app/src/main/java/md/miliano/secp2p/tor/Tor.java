package md.miliano.secp2p.tor;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import md.miliano.secp2p.crypto.AdvancedCrypto;
import md.miliano.secp2p.utils.Util;

public class Tor {

    private static final String torname = "ctor";
    private static final String tordirname = "tordata";
    private static final String torSvcDir = "torSvc";
    private static final String torCfg = "torcfg";
    private static final int HIDDEN_SERVICE_VERSION = 3;
    private static Tor instance = null;
    private final Context mContext;
    private static final int mSocksPort = 9151;
    private static final int mHttpPort = 8191;
    private String mDomain = "";
    private final ArrayList<Listener> mListeners;
    private final ArrayList<LogListener> mLogListeners;
    private String status = "";
    private boolean mReady = false;

    private final File mTorDir;

    private Process mProcessTor;

    private final AtomicBoolean mRunning = new AtomicBoolean(false);

    static {
        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    private Tor(Context c) {

        this.mContext = c;

        mListeners = new ArrayList<>();
        mLogListeners = new ArrayList<>();

        mTorDir = new File(c.getFilesDir(), "tor");
        if (!mTorDir.exists()) {
            mTorDir.mkdir();
        }

        mDomain = Util.filestr(new File(getServiceDir(), "hostname")).trim();
        log(mDomain);
    }

    /**
     * Starting tor thread
     */
    public void start() {
        if (mRunning.get()) return;

        Server.getInstance(mContext).setServiceRegistered(false);
        mReady = false;
        new Thread(() -> {
            try {
                log("make dir");
                File tordir = new File(mTorDir, tordirname);
                tordir.mkdirs();

                log("make service");
                File torsrv = new File(mTorDir, torSvcDir);
                torsrv.mkdirs();

                log("configure");
                PrintWriter torcfg = new PrintWriter(mContext.openFileOutput(torCfg, Context.MODE_PRIVATE));
                torcfg.println("DataDirectory " + tordir.getAbsolutePath());
                torcfg.println("SOCKSPort " + mSocksPort);
                torcfg.println("HTTPTunnelPort " + mHttpPort);
                torcfg.println("HiddenServiceDir " + torsrv.getAbsolutePath());
                torcfg.println("HiddenServiceVersion " + HIDDEN_SERVICE_VERSION);
                torcfg.println("HiddenServicePort " + getHiddenServicePort() + " " + Server.getInstance(mContext).getSocketName());
                torcfg.println("HiddenServicePort " + getFileServerPort() + " 127.0.0.1:" + getFileServerPort());
                torcfg.println();
                torcfg.close();
                log(Util.filestr(new File(mContext.getFilesDir(), torCfg)));

                log("start: " + new File(torname).getAbsolutePath());

                String torFile = new File(mContext.getApplicationInfo().nativeLibraryDir, "libTor.so").getAbsolutePath();

                String[] command = new String[]{
                        torFile,
                        "-f", mContext.getFileStreamPath(torCfg).getAbsolutePath()
                };

                StringBuilder sb = new StringBuilder();
                for (String s : command) {
                    sb.append(s);
                    sb.append(" ");
                }

                log("Command: " + sb);

                mRunning.set(true);

                mProcessTor = Runtime.getRuntime().exec(command);
                BufferedReader torReader = new BufferedReader(new InputStreamReader(mProcessTor.getInputStream()));
                while (true) {
                    final String line = torReader.readLine();
                    if (line == null) break;
                    log("### | " + line);
                    status = line;

                    boolean ready2 = mReady;

                    if (line.contains("100%")) {
                        ls(mTorDir);
                        mDomain = Util.filestr(new File(torsrv, "hostname")).trim();
                        log(mDomain);
                        try {
                            for (Listener l : mListeners) {
                                if (l != null) l.onChange();
                            }
                        } catch (Exception ignored) {
                        }
                        ready2 = true;

                        Server.getInstance(mContext).checkServiceRegistered();
                    }
                    mReady = ready2;
                    try {
                        for (LogListener ll : mLogListeners) {
                            if (ll != null) {
                                ll.onLog();
                            }
                        }
                    } catch (Exception ignored) {

                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            mRunning.set(false);
        }).start();
    }

    public static Tor getInstance(Context context) {
        if (instance == null) {
            instance = new Tor(context.getApplicationContext());
        }
        return instance;
    }

    public static int getHiddenServicePort() {
        return 31512;
    }

    public static int getFileServerPort() {
        return 8088;
    }

    private void log(String s) {
        Log.d("Tor", "Data: " + s);
    }

    void ls(File f) {
        log(f.toString());
        if (f.isDirectory()) {
            for (File s : Objects.requireNonNull(f.listFiles())) {
                ls(s);
            }
        }
    }

    public static int getSocksPort() {
        return mSocksPort;
    }

    public static int getHttpPort() {
        return mHttpPort;
    }

    public String getID() {
        return mDomain.replace(".onion", "").trim();
    }

    public void addListener(Listener l) {
        if (l != null && !mListeners.contains(l)) {
            mListeners.add(l);
            l.onChange();
        }
    }

    public void removeListener(Listener l) {
        mListeners.remove(l);
    }

    public File getServiceDir() {
        return new File(mTorDir, torSvcDir);
    }

    private KeyFactory getKeyFactory() {
        try {
            return KeyFactory.getInstance("ECDSA");
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    public byte[] getPublicKeyBytes() {
        try {
            return Base64.decode(Util.filestr(new File(getServiceDir(), "ecdsaPublicKey")).replaceAll("\\s", ""), Base64.DEFAULT);
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    public ECPrivateKey getPrivateKey() {
        try {
            return (ECPrivateKey) getKeyFactory().generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(Util.filestr(new File(getServiceDir(), "ecdsaPrivateKey")).replaceAll("\\s", ""), Base64.DEFAULT)));
        } catch (InvalidKeySpecException ex) {
            throw new Error(ex);
        }
    }

    public byte[] sign(byte[] msg) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA", "SC");
            signature.initSign(getPrivateKey());
            signature.update(msg);
            return signature.sign();
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    public void stop() {
        if (mProcessTor != null) mProcessTor.destroy();
    }

    public String encryptByPublicKey(String data, byte[] pubKeyBytes) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, InvalidKeySpecException, NoSuchProviderException {
        PublicKey publicKey = getKeyFactory().generatePublic(new X509EncodedKeySpec(pubKeyBytes));
        Cipher cipher = Cipher.getInstance("ECIES", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        String encrypted = AdvancedCrypto.toHex(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        log("CIPHER - encryptByPublicKey : " + encrypted);
        return encrypted;
    }

    public String decryptByPrivateKey(String data) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, NoSuchProviderException {
        Cipher cipher = Cipher.getInstance("ECIES", "SC");
        cipher.init(Cipher.DECRYPT_MODE, getPrivateKey());
        log("CIPHER - decryptByPrivateKey : " + data);
        return new String(cipher.doFinal(AdvancedCrypto.toByte(data)), StandardCharsets.UTF_8);
    }

    boolean checkSig(byte[] pubKey, byte[] sig, byte[] msg) throws InvalidKeySpecException {
        PublicKey publicKey = getKeyFactory().generatePublic(new X509EncodedKeySpec(pubKey));
        boolean ret;
        try {
            Signature ECDSA = Signature.getInstance("SHA256withECDSA", new BouncyCastleProvider());
            ECDSA.initVerify(publicKey);
            ECDSA.update(msg);
            ret = ECDSA.verify(sig);
        } catch (Exception ex) {
            ex.printStackTrace();
            ret = false;
        }
        return ret;
    }

    public void addLogListener(LogListener l) {
        if (!mLogListeners.contains(l)) {
            mLogListeners.add(l);
        }
    }

    public String getStatus() {
        return status;
    }

    public boolean isReady() {
        return mReady;
    }

    public void removeLogListener(LogListener ll) {
        mLogListeners.remove(ll);
    }

    public interface Listener {
        void onChange();
    }

    public interface LogListener {
        void onLog();
    }
}
