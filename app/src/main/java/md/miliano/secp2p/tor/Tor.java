package md.miliano.secp2p.tor;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import md.miliano.secp2p.crypto.AdvancedCrypto;
import md.miliano.secp2p.utils.Util;

public class Tor {

    private static String torname = "ctor";
    private static String tordirname = "tordata";
    private static String torSvcDir = "torSvc";
    private static String torCfg = "torcfg";
    private static int HIDDEN_SERVICE_VERSION = 3;
    private static Tor instance = null;
    private Context mContext;
    private static int mSocksPort = 9151;
    private static int mHttpPort = 8191;
    private String mDomain = "";
    private ArrayList<Listener> mListeners;
    private ArrayList<LogListener> mLogListeners;
    private String status = "";
    private boolean mReady = false;

    private File mTorDir;

    private Process mProcessTor;

    private AtomicBoolean mRunning = new AtomicBoolean(false);

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

                log("Command: " + sb.toString());

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
                        } catch (Exception e) {
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
                    } catch (Exception e) {

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
            for (File s : f.listFiles()) {
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
        Security.addProvider(new BouncyCastleProvider());
        try {
            return KeyFactory.getInstance("RSA");
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }


    public RSAPrivateKey getPrivateKey() {
        try {
            String sk = Util.filestr(new File(getServiceDir(), "rsaPrivateKey"));
            sk = sk.replace("-----BEGIN RSA PRIVATE KEY-----\n", "");
            sk = sk.replace("-----END RSA PRIVATE KEY-----", "");
            sk = sk.replaceAll("\\s", "");
            return (RSAPrivateKey) getKeyFactory().generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(sk, Base64.DEFAULT)));
        } catch (InvalidKeySpecException ex) {
            throw new Error(ex);
        }
    }

    private RSAPrivateKeySpec getPrivateKeySpec() {
        try {
            return getKeyFactory().getKeySpec(getPrivateKey(), RSAPrivateKeySpec.class);
        } catch (InvalidKeySpecException ex) {
            throw new Error(ex);
        }
    }

    private RSAPublicKeySpec getPublicKeySpec() {
        return new RSAPublicKeySpec(getPrivateKeySpec().getModulus(), BigInteger.valueOf(65537));
    }

    public byte[] getPubKeySpec() {
        return getPrivateKeySpec().getModulus().toByteArray();
    }

    public byte[] sign(byte[] msg) {
        try {
            Signature signature;
            signature = Signature.getInstance("SHA1withRSA");
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

    public String encryptByPublicKey(String data, byte[] pubKeySpecBytes) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, InvalidKeySpecException, NoSuchProviderException {
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(new BigInteger(pubKeySpecBytes), BigInteger.valueOf(65537));
        PublicKey publicKey = getKeyFactory().generatePublic(publicKeySpec);
        Cipher encrypt;
        encrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encrypt.init(Cipher.ENCRYPT_MODE, publicKey);
        String encrypted = AdvancedCrypto.toHex(encrypt.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        log("CIPHER - encryptByPublicKey : " + encrypted);
        return encrypted;
    }

    public String decryptByPrivateKey(String data) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, NoSuchProviderException {
        Cipher decrypt;
        decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decrypt.init(Cipher.DECRYPT_MODE, getPrivateKey());
        log("CIPHER - decryptByPrivateKey : " + data);
        return new String(decrypt.doFinal(AdvancedCrypto.toByte(data)), StandardCharsets.UTF_8);
    }

    boolean checkSig(byte[] pubKey, byte[] sig, byte[] msg) {
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(new BigInteger(pubKey), BigInteger.valueOf(65537));

        PublicKey publicKey;
        try {
            publicKey = getKeyFactory().generatePublic(publicKeySpec);
        } catch (InvalidKeySpecException ex) {
            ex.printStackTrace();
            return false;
        }

        try {
            Signature signature;
            signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(publicKey);
            signature.update(msg);
            return signature.verify(sig);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
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
