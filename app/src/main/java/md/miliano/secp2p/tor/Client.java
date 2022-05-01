package md.miliano.secp2p.tor;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.realm.Realm;
import io.realm.RealmResults;
import md.miliano.secp2p.BuildConfig;
import md.miliano.secp2p.crypto.AdvancedCrypto;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Database;
import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.db.TorData;
import md.miliano.secp2p.db.TorRequest;
import md.miliano.secp2p.utils.Util;

public class Client {

    private static final String TAG = "Client";
    private static Client instance;
    private final Tor tor;
    private final Database mDatabase;

    private final Context mContext;
    private final AtomicInteger counter = new AtomicInteger();
    private StatusListener statusListener;

    private final HashSet<String> mMessageSending = new HashSet<>();
    private final AtomicInteger mTries = new AtomicInteger(0);

    public Client(Context c) {
        mContext = c;
        tor = Tor.getInstance(mContext);

        mDatabase = Database.getInstance(mContext);
    }

    public static Client getInstance(Context context) {
        if (instance == null)
            instance = new Client(context.getApplicationContext());
        return instance;
    }

    private void log(String s) {
        if (!BuildConfig.DEBUG) return;
        Log.i(TAG, s);
    }

    private Sock connect(String address) {
        log("connect to " + address);
        return new Sock(address + ".onion", Tor.getHiddenServicePort());
    }

    private String sendAddNotif(String receiver, String description) {

        String sender = tor.getID();

        String n = mDatabase.getName();
        if (n == null || n.trim().isEmpty()) n = " ";
        Gson gson = new Gson();

        TorData td = new TorData();
        td.setReceiver(receiver);
        td.setSender(sender);
        td.setDataType(TorData.TYPE_REQUEST);

        TorRequest tr = new TorRequest();
        tr.setSender(sender);
        tr.setReceiver(receiver);
        tr.setSenderName(n);
        tr.setDescription(description);

        td.setData(gson.toJson(tr));
        td.setPubKeySpec(Util.base64encode(tor.getPubKeySpec()));
        td.setSignature(Util.base64encode(tor.sign(("add " + sender + " " + td.getData()).getBytes(StandardCharsets.UTF_8))));

        String content = gson.toJson(td);
        content = Util.base64encode(content.getBytes(StandardCharsets.UTF_8));

        return connect(receiver).queryAndCloseString(
                "add",
                content
        );
    }

    private String sendPubKey(String receiver) {
        String sender = tor.getID();

        String n = mDatabase.getName();
        if (n == null || n.trim().isEmpty()) n = " ";
        Gson gson = new Gson();

        TorData td = new TorData();
        td.setReceiver(receiver);
        td.setSender(sender);
        td.setDataType(TorData.TYPE_REQUEST);

        TorRequest tr = new TorRequest();
        tr.setSender(sender);
        tr.setReceiver(receiver);
        tr.setSenderName(n);
        tr.setDescription(mDatabase.get("description"));

        td.setData(gson.toJson(tr));
        td.setPubKeySpec(Util.base64encode(tor.getPubKeySpec()));
        td.setSignature(Util.base64encode(tor.sign(("pubKey " + sender + " " + td.getData()).getBytes(StandardCharsets.UTF_8))));

        String content = gson.toJson(td);
        content = Util.base64encode(content.getBytes(StandardCharsets.UTF_8));

        return connect(receiver).queryAndCloseString(
                "pubKey",
                content
        );
    }

    public boolean isTxMessage(Message message) {
        String sender = message.getSender();
        return sender.equals(Tor.getInstance(mContext).getID());
    }

    private Message getMessage(String id) {
        Realm realm = Realm.getDefaultInstance();
        Message message = realm.where(Message.class).equalTo("primaryKey", id).findFirst();
        realm.close();
        return message;
    }

    private Message resolveQuoteMessageId(Message message) {
        if (message.getQuotedMessageId() != null) {
            Message quotedMessage = getMessage(message.getQuotedMessageId());
            if (isTxMessage(quotedMessage)) {
                message.setQuotedMessageId(quotedMessage.getPrimaryKey());
            } else {
                message.setQuotedMessageId(quotedMessage.getRemoteMessageId());
            }
        }
        return message;
    }

    private boolean sendMsg(Sock sock, Message message, Contact contact) throws Exception {
        if (sock.isClosed()) {
            return false;
        }
        resolveQuoteMessageId(message);
        Gson gson = new Gson();
        TorData td = new TorData();
        td.setSender(message.getSender());
        td.setReceiver(message.getReceiver());
        td.setDataType(TorData.TYPE_MESSAGE);
        if (message.getFileShare() != null) {
            message.getFileShare().setFilePath("");
            message.getFileShare().setDownloaded(false);
        }
        String key = UUID.randomUUID().toString();
        AdvancedCrypto advancedCrypto = new AdvancedCrypto(key);
        String content = advancedCrypto.encrypt(gson.toJson(message));
        td.setData(content);
        String encryptedKey = tor.encryptByPublicKey(key, contact.getPubKey());
        log("Encrypted key: " + encryptedKey);
        td.setSecretKey(encryptedKey);
        td.setSignature(Util.base64encode(tor.sign(("msg " + content).getBytes(StandardCharsets.UTF_8))));

        String jsonContent = gson.toJson(td);
        jsonContent = Util.base64encode(jsonContent.getBytes(StandardCharsets.UTF_8));
        String sender = tor.getID();
        if (message.getReceiver().equals(sender)) return false;

        return sock.queryBool(
                "msg",
                jsonContent
        );
    }

    public void startSendPendingFriends() {
        log("start send pending friends");
        start(this::doSendPendingFriends);
    }

    public void doSendPendingFriends() {
        log("do send pending friends");
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Contact> contacts = realm.where(Contact.class).equalTo("friendReqAccepted", 1).findAll();
        realm.beginTransaction();
        for (Contact c : contacts) {
            log("try to send friend request: " + c.getAddress());
            sendAddNotif(c.getAddress(), c.getDescription());
        }
        realm.commitTransaction();
        realm.close();
    }

    public void doSendAllPendingMessages() {
        log("do send all pending messages");
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Contact> contacts = realm.where(Contact.class).equalTo("outgoing", 1).findAll();
        for (Contact c : contacts) {
            log("try to send friend request");
            try {
                doSendPendingMessages(c);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        realm.close();
    }

    private void doSendPendingMessages(Contact contact) throws Exception {
        log("do send pending messages");
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Message> messages = realm.where(Message.class).equalTo("pending", 1).equalTo("receiver", contact.getAddress()).findAll();
        Sock sock = connect(contact.getAddress());
        for (Message m : messages) {
            log("try to send message: " + m.getPrimaryKey());
            if (sendMsg(sock, realm.copyFromRealm(m), contact)) {
                if (m.isValid()) {
                    realm.beginTransaction();
                    m.setPending(0);
                    contact.setLastMessageTime(m.getTime());
                    realm.commitTransaction();
                }
                log("message sent");
            }
        }
        realm.close();
        sock.close();
    }

    public void startSendPendingMessages(final String address) {
        if (mMessageSending.contains(address)) {
            mTries.incrementAndGet();
            return;
        }
        mMessageSending.add(address);
        log("start send pending messages");
        start(() -> {
            Realm realm = Realm.getDefaultInstance();
            Contact contact = realm.where(Contact.class).equalTo("address", address).findFirst();
            if (contact != null) {
                int tries;
                do {
                    try {
                        doSendPendingMessages(contact);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    tries = mTries.decrementAndGet();
                    log("Tries are : " + tries);
                } while (tries > 0);
                if (mTries.get() < 0) mTries.set(0);
            }
            realm.close();
            mMessageSending.remove(address);
        });
    }

    public boolean isBusy() {
        return counter.get() > 0;
    }

    private void start(final Runnable runnable) {
        new Thread() {
            @Override
            public void run() {
                {
                    int n = counter.incrementAndGet();
                    StatusListener l = statusListener;
                    if (l != null) l.onStatusChange(n > 0);
                }
                try {
                    runnable.run();
                } finally {
                    int n = counter.decrementAndGet();
                    StatusListener l = statusListener;
                    if (l != null) l.onStatusChange(n > 0);
                }
            }
        }.start();
    }

    public void setStatusListener(StatusListener statusListener) {
        this.statusListener = statusListener;
        if (statusListener != null) {
            statusListener.onStatusChange(counter.get() > 0);
        }
    }

    public void sendPublicKey(final String address) {
        if (mMessageSending.contains(address)) {
            mTries.incrementAndGet();
            return;
        }
        mMessageSending.add(address);
        log("start send public Key ");
        start(() -> {
            Realm realm = Realm.getDefaultInstance();
            Contact contact = realm.where(Contact.class).equalTo("address", address).findFirst();
            if (contact != null) {
                int tries;
                do {
                    try {
                        sendPubKey(contact.getAddress());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    tries = mTries.decrementAndGet();
                    log("Tries are : " + tries);
                } while (tries > 0);
                if (mTries.get() < 0) mTries.set(0);
            }
            realm.close();
            mMessageSending.remove(address);
        });
    }

    public interface StatusListener {
        void onStatusChange(boolean loading);
    }


    public boolean testIfServerIsUp() {
        Sock sock = connect(tor.getID());
        log("Socket opened: " + !sock.isClosed());
        boolean ret = !sock.isClosed();
        sock.close();
        return ret;
    }

    public void doAskForNewMessages(String receiver) {
        String sender = tor.getID();
        log("ask for new msg");
        String cmd = "newmsg " + receiver + " " + sender + " " + System.currentTimeMillis() / 60000 * 60000;
        connect(receiver).queryAndClose(
                cmd,
                Util.base64encode(tor.getPubKeySpec()),
                Util.base64encode(tor.sign(cmd.getBytes(StandardCharsets.UTF_8)))
        );
    }

    public void startAskForNewMessages(final String receiver) {
        start(() -> doAskForNewMessages(receiver));
    }

    public void askForNewMessages() {
        Realm realm = Realm.getDefaultInstance();
        final RealmResults<Contact> contacts = realm.where(Contact.class).equalTo("incoming", 0).findAll();
        for (Contact c : contacts) {
            String receiver = c.getAddress();
            doAskForNewMessages(receiver);
        }
        realm.close();
    }
}
