package md.miliano.secp2p.tor;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import com.google.gson.Gson;
import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import md.miliano.secp2p.BuildConfig;
import md.miliano.secp2p.crypto.AdvancedCrypto;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.db.TorData;
import md.miliano.secp2p.db.TorRequest;
import md.miliano.secp2p.utils.Util;

public class Server {

    public static final int C_UNKNOWN = 0;
    public static final int C_DATA_RECEIVED = 1;
    public static final int C_CONTACT_NOT_FOUND = 2;
    public static final int C_WRONG_ADDRESS = 3;
    public static final int C_INVALID_SIGNATURE = 4;
    public static final int C_WRONG_TIMESTAMP = 5;

    private static Server instance;
    private String socketName;
    private Context mContext;
    private String TAG = "Server";
    private ArrayList<Listener> mListeners = new ArrayList<>();
    private ServiceRegisterListener mServiceRegisterListener;
    private AtomicBoolean mServiceRegistered = new AtomicBoolean(false);
    private LocalServerSocket serverSocket;
    private LocalSocket ls;

    private volatile AtomicBoolean mCheckServiceRegisteredRunning = new AtomicBoolean(false);
    private Thread mServiceRegistration;

    public interface FileDownloadListener {
        void onDownloadProgressChange(String messageId);
    }

    public FileDownloadListener mFileDownloadListener;
    public Map<String, Integer> mDownloadProgress = new HashMap<>();
    public Map<String, BaseDownloadTask> mDownloadTasks = new HashMap<>();

    public Server(Context c) {
        mContext = c;

        log("start listening");
        try {
            socketName = new File(mContext.getFilesDir(), "socket").getAbsolutePath();
            ls = new LocalSocket();
            ls.bind(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.FILESYSTEM));
            serverSocket = new LocalServerSocket(ls.getFileDescriptor());
            socketName = "unix:" + socketName;
            log(socketName);

        } catch (Exception ex) {
            throw new Error(ex);
        }
        log("started listening");
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    LocalServerSocket ss = serverSocket;
                    if (ss == null) break;
                    log("waiting for connection");
                    final LocalSocket ls;
                    try {
                        ls = ss.accept();
                        log("accept");
                    } catch (IOException ex) {
                        throw new Error(ex);
                    }
                    if (ls == null) {
                        log("no socket");
                        continue;
                    }
                    log("new connection");
                    new Thread() {
                        @Override
                        public void run() {
                            handle(ls);
                            try {
                                ls.close();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }.start();
                }
            }
        }.start();
    }

    public void setServiceRegistered(boolean value) {
        mServiceRegistered.set(value);
    }

    public boolean isServiceRegistered() {
        return mServiceRegistered.get();
    }

    public void setCheckServiceRegisteredRunning(boolean value) {
        mCheckServiceRegisteredRunning.set(value);
    }

    public boolean isCheckServiceRegisteredRunning() {
        return mCheckServiceRegisteredRunning.get();
    }

    public void checkServiceRegistered() {
        if (mCheckServiceRegisteredRunning.get()) return;

        setCheckServiceRegisteredRunning(true);
        mServiceRegistration = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    return;
                }
                setServiceRegistered(false);

                Tor tor = Tor.getInstance(mContext);
                for (int i = 0; i < 20 && !tor.isReady(); i++) {
                    log("Tor not ready");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
                log("Tor ready");
                final Client client = Client.getInstance(mContext);
                for (int i = 0; i < 20 && !client.testIfServerIsUp(); i++) {
                    log("Hidden server descriptors not yet propagated");
                    if (mServiceRegisterListener != null) mServiceRegisterListener.onChange(false);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
                log("Hidden service registered");
                setServiceRegistered(true);

                if (mServiceRegisterListener != null) mServiceRegisterListener.onChange(true);

                try {
                    new Scanner(new File(tor.getServiceDir(), "hs_ed25519_secret_key"));
                    new Scanner(new File(tor.getServiceDir(), "rsaPrivateKey"));
                } catch (FileNotFoundException fnfe) {
                    genRSAKeyPairAndSaveToFile(1024, Tor.getInstance(mContext).getServiceDir().getPath());
                }
                client.askForNewMessages();

                try {
                    FileServer.getInstance(mContext, Tor.getFileServerPort(), false).startServer();
                    log("FileServer has been started");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                setCheckServiceRegisteredRunning(false);
            }
        };
        mServiceRegistration.start();
    }

    public static Server getInstance(Context context) {
        if (instance == null) {
            instance = new Server(context.getApplicationContext());
        }
        return instance;
    }

    public void setFileDownloadListener(FileDownloadListener fileDownloadListener) {
        mFileDownloadListener = fileDownloadListener;
    }

    private void log(String s) {
        if (!BuildConfig.DEBUG) return;
        Log.i(TAG, s);
    }

    public void addListener(Listener l) {
        if (!mListeners.contains(l)) {
            mListeners.add(l);
            if (l != null)
                l.onChange();
        }
    }

    public void removeListener(Listener l) {
        mListeners.remove(l);
    }

    public void setServiceRegisterListener(ServiceRegisterListener srl) {
        mServiceRegisterListener = srl;
    }

    public void stopFileServer() {
        FileServer.getInstance(mContext, Tor.getFileServerPort(), false).stop();
    }

    String handle(String request) throws Exception {
        if (BuildConfig.DEBUG) log("accept");

        FileServer.getInstance(mContext, Tor.getFileServerPort(), false).startServer();

        Tor tor = Tor.getInstance(mContext);
        Notifier notifier = Notifier.getInstance(mContext);

        log("request " + request);

        String[] tokens = request.split(" ");
        if (tokens.length == 0)
            return "";

        log("toks " + tokens.length);
        if ("add".equals(tokens[0]) && tokens.length == 2) {
            String op = tokens[0];
            String content = new String(Util.base64decode(tokens[1]));
            Gson gson = new Gson();
            TorData td = gson.fromJson(content, TorData.class);

            if (!td.getReceiver().equals(tor.getID())) {
                log("message wrong address");
                return "" + C_WRONG_ADDRESS;
            }

            String sender = td.getSender();
            String pubKeySpec = td.getPubKeySpec();
            String signature = td.getSignature();
            log("add target ok");
            if (!tor.checkSig(
                    Util.base64decode(pubKeySpec),
                    Util.base64decode(signature),
                    (op + " " + sender + " " + td.getData()).getBytes(StandardCharsets.UTF_8))) {
                log("add invalid signature");
                return "" + C_INVALID_SIGNATURE;
            }
            log("add signature ok");
            if (td.getDataType() == TorData.TYPE_REQUEST) {
                String data = td.getData();
                TorRequest tr = gson.fromJson(data, TorRequest.class);
                Contact.addContact(mContext, sender, tr.getSenderName(), tr.getDescription(), Util.base64decode(pubKeySpec), false, true,1);
                for (Listener l : mListeners) {
                    if (l != null) l.onChange();
                }
                Notifier.getInstance(mContext).showRequestNotification(tr.getSenderName(), tr.getDescription());
                log("add ok");
            } else {
                log("Not a request message");
            }
            return "" + C_DATA_RECEIVED; // send pub key only after friend request is approved
        }
        if ("msg".equals(tokens[0]) && tokens.length == 2) {
            Gson gson = new Gson();
            String content = new String(Util.base64decode(tokens[1]), StandardCharsets.UTF_8);
            log("Content: " + content);
            TorData td = gson.fromJson(content, TorData.class);
            String sender = td.getSender();
            Realm realm = Realm.getDefaultInstance();
            Contact contact = realm.where(Contact.class).equalTo("address", sender).findFirst();
            if (contact == null) {
                log("Contact not found with " + sender);
                return "" + C_CONTACT_NOT_FOUND;
            }
            byte[] pubKey = contact.getPubKey();
            String signature = td.getSignature();

            if (!td.getReceiver().equals(tor.getID())) {
                log("message wrong address");
                return "" + C_WRONG_ADDRESS;
            }

            log("message address ok");
            if (!tor.checkSig(
                    pubKey,
                    Util.base64decode(signature),
                    ("msg " + td.getData()).getBytes(StandardCharsets.UTF_8))) {
                log("message invalid signature");
                return "" + C_INVALID_SIGNATURE;
            }
            log("message signature ok");

            for (Listener l : mListeners) {
                if (l != null) l.onChange();
            }
            log("Trying to decrypt Secret key: " + td.getSecretKey());
            String key = tor.decryptByPrivateKey(td.getSecretKey());
            AdvancedCrypto advancedCrypto = new AdvancedCrypto(key);
            String data = advancedCrypto.decrypt(td.getData());
            log("Decrypted Data: " + data);
            Message message = gson.fromJson(data, Message.class);

            Message.addUnreadIncomingMessage(message);
            if (message.getType() != Message.TYPE_TEXT) {
                downloadFile(message);
            }
            notifier.onMessage();
            log("add ok");
            return "" + C_DATA_RECEIVED;
        }
        if ("newmsg".equals(tokens[0]) && tokens.length == 6) {
            String op = tokens[0];
            String receiver = tokens[1];
            String sender = tokens[2];
            String timestr = tokens[3];
            String pubkey = tokens[4];
            String signature = tokens[5];
            if (!receiver.equals(tor.getID())) {
                log("message wrong address");
                return "" + C_WRONG_ADDRESS;
            }
            log("message address ok");
            if (Long.parseLong(timestr) > System.currentTimeMillis()) {
                log("wrong timestamp, future");
                return "" + C_WRONG_TIMESTAMP;
            }
            if (Long.parseLong(timestr) + 150000 < System.currentTimeMillis()) {
                log("wrong timestamp, timed out");
                return "" + C_WRONG_TIMESTAMP;
            }
            if (!tor.checkSig(
                    Util.base64decode(pubkey),
                    Util.base64decode(signature),
                    (op + " " + receiver + " " + sender + " " + timestr).getBytes(StandardCharsets.UTF_8))) {
                log("message invalid signature");
                return "" + C_INVALID_SIGNATURE;
            }
            Client.getInstance(mContext).startSendPendingMessages(sender);
            return "" + C_DATA_RECEIVED;
        }

        if ("pubKey".equals(tokens[0]) && tokens.length == 2) {
            String content = new String(Util.base64decode(tokens[1]));
            Gson gson = new Gson();
            TorData td = gson.fromJson(content, TorData.class);

            if (!td.getReceiver().equals(tor.getID())) {
                log("message wrong address");
                return "" + C_WRONG_ADDRESS;
            }

            String sender = td.getSender();
            String pubKeySpec = td.getPubKeySpec();

            if (td.getDataType() == TorData.TYPE_REQUEST) {
                String data = td.getData();
                TorRequest tr = gson.fromJson(data, TorRequest.class);
                Contact contact = Realm.getDefaultInstance().where(Contact.class).equalTo("address", sender).findFirst();
                if (contact == null) {
                    log("Contact not found with " + sender);
                    return "" + C_CONTACT_NOT_FOUND;
                }
                Contact.addContact(mContext, sender, tr.getSenderName(), tr.getDescription(), Util.base64decode(pubKeySpec), false, false, 0);



                for (Listener l : mListeners) {
                    if (l != null) l.onChange();
                }
                log("add ok");
            } else {
                log("Not a request message");
            }

            return "" + C_DATA_RECEIVED;
        }

        return "" + C_UNKNOWN;
    }

    public void downloadFile(Message message) {
        File mediaFileDir = new File(mContext.getFilesDir(), message.getSender());
        File file = new File(mediaFileDir, message.getFileShare().getFilename());
        String url = Message.getDownloadUrl(message);
        if (!file.exists()) {
            if (!mDownloadTasks.containsKey(message.getPrimaryKey())) {
                BaseDownloadTask dt = FileDownloader.getImpl()
                        .create(url)
                        .setPath(mediaFileDir.toString(), true)
                        .addHeader("password", message.getFileShare().getPassword())
                        .setCallbackProgressTimes(300)
                        .setAutoRetryTimes(3)
                        .setMinIntervalUpdateSpeed(2000)
                        .setTag(message.getPrimaryKey())
                        .setListener(fileDownloadListener);

                mDownloadTasks.put(message.getPrimaryKey(), dt);
                mDownloadProgress.put(message.getPrimaryKey(), -1);
                int dtid = dt.start();
                Log.d(TAG, "onBindViewHolder: Download started for ID: " + dtid);
            }
        }
    }

    public com.liulishuo.filedownloader.FileDownloadListener fileDownloadListener = new com.liulishuo.filedownloader.FileDownloadListener() {
        @Override
        protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            Log.d(TAG, "pending: " + task.getId());
            String messageId = (String) task.getTag();
            if (mFileDownloadListener != null) {
                mFileDownloadListener.onDownloadProgressChange(messageId);
            }
        }

        @Override
        protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            int progress = (int) (((double) soFarBytes / (double) totalBytes) * 100.f);
            Log.d(TAG, "progress: " + progress);
            String messageId = (String) task.getTag();
            int oldProgress = -1;
            if (mDownloadProgress.containsKey(messageId)) {
                oldProgress = mDownloadProgress.get(messageId);
            }
            mDownloadProgress.put(messageId, progress);
            if (mFileDownloadListener != null && oldProgress != progress) {
                mFileDownloadListener.onDownloadProgressChange(messageId);
            }
        }

        @Override
        protected void completed(BaseDownloadTask task) {
            Log.d(TAG, "completed: " + task.getId());
            String messageId = (String) task.getTag();
            mDownloadTasks.remove(messageId);
            mDownloadProgress.remove(messageId);
            Message.updateDownloadStatus(messageId, true);
            if (mFileDownloadListener != null) {
                mFileDownloadListener.onDownloadProgressChange(messageId);
            }
        }

        @Override
        protected void paused(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            Log.d(TAG, "paused: " + task.getId());
            String messageId = (String) task.getTag();
            mDownloadTasks.remove(messageId);
            mDownloadProgress.remove(messageId);
            if (mFileDownloadListener != null) {
                mFileDownloadListener.onDownloadProgressChange(messageId);
            }
        }

        @Override
        protected void error(BaseDownloadTask task, Throwable e) {
            Log.d(TAG, "error: " + task.getId() + " " + e.getLocalizedMessage());
            e.printStackTrace();
            String messageId = (String) task.getTag();
            mDownloadTasks.remove(messageId);
            mDownloadProgress.remove(messageId);
            if (mFileDownloadListener != null) {
                mFileDownloadListener.onDownloadProgressChange(messageId);
            }
        }

        @Override
        protected void warn(BaseDownloadTask task) {
            Log.d(TAG, "warn: " + task.getId());
        }
    };

    private void handle(InputStream is, OutputStream os) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os));
        while (true) {
            String request = r.readLine();
            if (request == null) break;
            request = request.trim();
            if (request.equals("")) break;
            String response = "";
            try {
                response = handle(request);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            w.write(response + "\r\n");
            w.flush();
        }
        r.close();
        w.close();
    }

    private void handle(LocalSocket s) {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = s.getInputStream();
        } catch (IOException ex) {
        }
        try {
            os = s.getOutputStream();
        } catch (IOException ex) {
        }
        if (is != null && os != null) {
            try {
                handle(is, os);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
        if (is != null) {
            try {
                is.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public String getSocketName() {
        return socketName;
    }

    public interface Listener {
        void onChange();
    }

    public interface ServiceRegisterListener {
        void onChange(boolean registered);
    }

    public static void genRSAKeyPairAndSaveToFile(int keyLength, String dir) {
        KeyPair keyPair = genRSAKeyPair(keyLength);

        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        Base64.Encoder encoder = Base64.getEncoder();

        try {
            Writer out = new FileWriter(dir + "/rsaPublicKey");
            out.write("-----BEGIN RSA PUBLIC KEY-----\n");
            out.write(encoder.encodeToString(publicKey.getEncoded()));
            out.write("\n-----END RSA PUBLIC KEY-----\n");
            out.close();
            out = new FileWriter(dir + "/rsaPrivateKey");
            out.write("-----BEGIN RSA PRIVATE KEY-----\n");
            out.write(encoder.encodeToString(privateKey.getEncoded()));
            out.write("\n-----END RSA PRIVATE KEY-----\n");
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair genRSAKeyPair(int keyLength) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(keyLength);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
