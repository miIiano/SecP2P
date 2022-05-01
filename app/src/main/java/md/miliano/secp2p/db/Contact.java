package md.miliano.secp2p.db;


import android.content.Context;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

public class Contact extends RealmObject {

    @PrimaryKey
    private long _id;
    @Index
    private String address;
    @Index
    private String name;
    private int outgoing;
    @Index
    private int incoming;
    private long lastOnlineTime;
    private int pending;
    private long lastMessageTime;
    private String description;
    private byte[] pubKey;
    private int friendReqAccepted;

    public long get_id() {
        return _id;
    }

    public void set_id(long _id) {
        this._id = _id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOutgoing() {
        return outgoing;
    }

    public void setOutgoing(int outgoing) {
        this.outgoing = outgoing;
    }

    public int getIncoming() {
        return incoming;
    }

    public void setIncoming(int incoming) {
        this.incoming = incoming;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getLastOnlineTime() {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(long lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public void setPubKey(byte[] pubKey) {
        this.pubKey = pubKey;
    }

    public int isFriendReqAccepted() {
        return friendReqAccepted;
    }

    public void setFriendReqAccepted(int friendReqAccepted) {
        this.friendReqAccepted = friendReqAccepted;
    }

    public static boolean rmContact(String id) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        RealmResults<Contact> contact = realm.where(Contact.class)
                .equalTo("address", id)
                .findAll();
        return contact.deleteAllFromRealm();
    }

    public static boolean addContact(Context context, String id, String name, String description, byte[] pubKey, boolean outgoing, boolean incoming, int isFriendReqPending) {
        name = name.trim();
        id = id.trim().toLowerCase();
        Realm realm = Realm.getDefaultInstance();
        Contact savedContact = realm.where(Contact.class).equalTo("address", id).findFirst();
        if (savedContact == null) {
            realm.beginTransaction();
            Contact contact = realm.createObject(Contact.class, getNextId());
            contact.setName(name);
            contact.setDescription(description);
            contact.setAddress(id);
            contact.setPubKey(pubKey);
            contact.setFriendReqAccepted(isFriendReqPending);
            contact.setOutgoing(outgoing ? 1 : 0);
            contact.setIncoming(incoming ? 1 : 0);
            realm.commitTransaction();
            Database.getInstance(context).addNewRequest();
        } else if (savedContact.getPubKey() == null) {
            realm.beginTransaction();
            savedContact.setPubKey(pubKey);
            savedContact.setFriendReqAccepted(isFriendReqPending);
            realm.commitTransaction();
        }
        realm.close();
        return savedContact == null;
    }

    public static boolean hasContact(Context context, String id) {
        Realm realm = Realm.getDefaultInstance();
        Contact contact = realm.where(Contact.class).equalTo("address", id).equalTo("incoming", 0).findFirst();
        realm.close();
        return contact != null;
    }

    public static long getNextId() {
        Realm realm = Realm.getDefaultInstance();
        Number maxId = realm.where(Contact.class).max("_id");
        realm.close();
        return (maxId == null) ? 1 : maxId.longValue() + 1;
    }

    public static void acceptContact(Context context, String id) {
        Realm realm = Realm.getDefaultInstance();
        Contact contact = realm.where(Contact.class).equalTo("address", id).findFirst();
        if (contact == null) {
            realm.beginTransaction();
            contact.setIncoming(0);
            contact.setOutgoing(0);
            realm.commitTransaction();
        }
        realm.close();
    }
}
