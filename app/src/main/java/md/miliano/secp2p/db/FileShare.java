package md.miliano.secp2p.db;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * fileshare table, it contains all the information of file which will be shared
 * _id primary key
 * message_id this is id of the message which was used for file sharing
 * filename name of the file being shared
 * filepath path of the file being shared
 * should_serve file will be shared only once, after that it won't be accessible
 */

public class FileShare extends RealmObject {

    @PrimaryKey
    private long _id;
    private String filename;
    private String filePath;
    private boolean isServed;
    private boolean isDownloadTried;
    private String mimeType;
    private String password;
    private long fileSize;
    private boolean isDownloaded;
    private byte[] thumbnail;

    public long get_id() {
        return _id;
    }

    public void set_id(long _id) {
        this._id = _id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isServed() {
        return isServed;
    }

    public void setServed(boolean served) {
        isServed = served;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isDownloadTried() {
        return isDownloadTried;
    }

    public void setDownloadTried(boolean downloadTried) {
        isDownloadTried = downloadTried;
    }

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }
}
