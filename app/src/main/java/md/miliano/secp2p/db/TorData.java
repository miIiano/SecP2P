package md.miliano.secp2p.db;

/**
 * this is class which handles data which is being sent between two users
 * sender is the address of the sender
 * dataType is the type of the message i.e. Message, Request
 * pubKey is the public key of the sender. For requests, this pubKey will be used for verifying the
 * message, and for Message, the saved pubKey will be used for verification of the signature
 * secretKey is the key by which data is encrypted, and this key is encrypted by PrimaryKey of the sender
 * data is the data of the message, Message or Request
 */
public class TorData {

    public static final int TYPE_MESSAGE = 1;
    public static final int TYPE_REQUEST = 2;

    private String signature;
    private String sender;
    private String receiver;
    public int dataType;
    private String pubKeySpec;
    private String secretKey;
    public String data;

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public int getDataType() {
        return dataType;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
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

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPubKeySpec() {
        return pubKeySpec;
    }

    public void setPubKeySpec(String pubKeySpec) {
        this.pubKeySpec = pubKeySpec;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
