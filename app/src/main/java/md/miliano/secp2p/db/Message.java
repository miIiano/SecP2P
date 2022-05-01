package md.miliano.secp2p.db;


import android.util.Log;

import java.io.File;
import java.security.SecureRandom;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;
import md.miliano.secp2p.crypto.AdvancedCrypto;
import md.miliano.secp2p.tor.Tor;

/**
 * message table
 * primaryKey: primary key
 * sender: 16 char onion address
 * receiver: 16 char onion address
 * content: message contents
 * time: message timestamp
 * pending: 1 if it is an outgoing message that still has to be sent, 0 if the message has already been sent, or if it has been received from someone else
 * type: it 1 = text, 2 = video, 3 = image, 4 = audio,  or 5 =file
 */
public class Message extends RealmObject {

    public static final int TYPE_TEXT = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_IMAGE = 3;
    public static final int TYPE_FILE = 4;
    public static final int TYPE_AUDIO = 5;
    private static final String TAG = Message.class.getName();


    @PrimaryKey
    @Required
    private String primaryKey;
    private long stableId;
    @Index
    private String sender;
    @Index
    private String receiver;
    private String content;
    private long time;
    private int pending;
    private int type;
    @Index
    private String remoteMessageId;
    @Index
    private String quotedMessageId;
    private String quotedMessageSender;
    private String quotedMessageContent;
    private FileShare fileShare;

    public String getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }

    public long getStableId() {
        return stableId;
    }

    public void setStableId(long stableId) {
        this.stableId = stableId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getRemoteMessageId() {
        return remoteMessageId;
    }

    public void setRemoteMessageId(String remoteMessageId) {
        this.remoteMessageId = remoteMessageId;
    }

    public FileShare getFileShare() {
        return fileShare;
    }

    public void setFileShare(FileShare fileShare) {
        this.fileShare = fileShare;
    }

    public String getQuotedMessageId() {
        return quotedMessageId;
    }

    public void setQuotedMessageId(String quotedMessageId) {
        this.quotedMessageId = quotedMessageId;
    }

    public String getQuotedMessageSender() {
        return quotedMessageSender;
    }

    public void setQuotedMessageSender(String quotedMessageSender) {
        this.quotedMessageSender = quotedMessageSender;
    }

    public String getQuotedMessageContent() {
        return quotedMessageContent;
    }

    public void setQuotedMessageContent(String quotedMessageContent) {
        this.quotedMessageContent = quotedMessageContent;
    }

    public synchronized static boolean addUnreadIncomingMessage(Message message) {
        Realm realm = Realm.getDefaultInstance();
        Log.d(TAG, "addUnreadIncomingMessage: " + message.getSender() + " " + message.getRemoteMessageId());
        Message checkMessage = realm.where(Message.class).equalTo("sender", message.getSender()).equalTo("remoteMessageId", message.getRemoteMessageId()).findFirst();
        if (checkMessage != null) {
            Log.d(TAG, "addUnreadIncomingMessage: Message already present");
            realm.close();
            return false;
        }

        realm.beginTransaction();
        message.setRemoteMessageId(message.getPrimaryKey());
        message.setPrimaryKey(UUID.randomUUID().toString());
        message.setStableId(getNextStableId());
        if (message.getFileShare() != null) {
            message.getFileShare().set_id(getNextFileId());
        }
        realm.copyToRealm(message);

        Contact contact = realm.where(Contact.class).equalTo("address", message.getSender()).findFirst();
        contact.setPending(contact.getPending() + 1);
        contact.setLastMessageTime(System.currentTimeMillis());
        realm.commitTransaction();
        realm.close();
        return true;
    }

    public static synchronized boolean abortOutgoingMessage(String id) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        Message m = realm.where(Message.class).equalTo("primaryKey", id).findFirst();
        m.deleteFromRealm();
        realm.commitTransaction();
        realm.close();
        return m != null;
    }

    public static long getNextStableId() {
        Realm realm = Realm.getDefaultInstance();
        Number maxId = realm.where(Message.class).max("stableId");
        realm.close();
        return (maxId == null) ? 1 : maxId.longValue() + 1;
    }

    public static long getNextFileId() {
        Realm realm = Realm.getDefaultInstance();
        Number maxId = realm.where(FileShare.class).max("_id");
        realm.close();
        return (maxId == null) ? 1 : maxId.longValue() + 1;
    }

    public static synchronized String addPendingOutgoingMessage(String sender, String receiver,
                                                                String content,
                                                                String quotedMessageId,
                                                                String quotedMessageSender,
                                                                String quotedMessageContent) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        Message message = realm.createObject(Message.class, UUID.randomUUID().toString());
        message.setStableId(getNextStableId());
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);
        message.setTime(System.currentTimeMillis());
        message.setQuotedMessageId(quotedMessageId);
        message.setQuotedMessageSender(quotedMessageSender);
        message.setQuotedMessageContent(quotedMessageContent);
        Log.d(TAG, "addPendingOutgoingMessage: " + message.getPrimaryKey() + " " + message.getTime());
        message.setPending(1);
        message.setType(TYPE_TEXT);
        Contact contact = realm.where(Contact.class).equalTo("address", receiver).findFirst();
        if (contact != null) {
            contact.setLastMessageTime(message.getTime());
        }
        realm.commitTransaction();
        realm.close();
        return message.getPrimaryKey();
    }

    public static synchronized String addPendingOutgoingMessage(
            String sender,
            String receiver,
            String content,
            String filename,
            String filePath,
            String mimeType,
            int type,
            byte[] thumbnail) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        Message message = realm.createObject(Message.class, UUID.randomUUID().toString());
        message.setStableId(getNextStableId());
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);
        message.setTime(System.currentTimeMillis());
        message.setPending(1);
        message.setType(type);

        FileShare fs = realm.createObject(FileShare.class, getNextFileId());
        fs.setFilename(filename);
        fs.setFilePath(filePath);
        fs.setMimeType(mimeType);
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        fs.setPassword(AdvancedCrypto.toHex(bytes));
        fs.setFileSize(new File(filePath).length());
        fs.setDownloaded(true);
        fs.setServed(false);
        fs.setThumbnail(thumbnail);
        message.setFileShare(fs);

        Contact contact = realm.where(Contact.class).equalTo("address", receiver).findFirst();
        if (contact != null) {
            contact.setLastMessageTime(message.getTime());
        }

        realm.commitTransaction();
        String primaryKey = message.getPrimaryKey();
        realm.close();
        return primaryKey;
    }

    public static Message updateDownloadStatus(String primaryKey, boolean isDownloaded) {
        Realm realm = Realm.getDefaultInstance();
        Message message = realm.where(Message.class).equalTo("primaryKey", primaryKey).findFirst();
        realm.beginTransaction();
        message.getFileShare().setDownloaded(isDownloaded);
        realm.commitTransaction();
        realm.close();
        return message;
    }

    public static String getDownloadUrl(Message message) {
        return "https://" + message.getSender() + ".onion:" + Tor.getFileServerPort() + "/" + message.getFileShare().getFilename();
    }
}
