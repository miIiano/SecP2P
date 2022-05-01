package md.miliano.secp2p.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {

    private static Database instance;
    private Context context;

    public Database(Context context) {
        super(context, "cdb", null, 1);
        this.context = context;
    }

    public static Database getInstance(Context context) {
        if (instance == null)
            instance = new Database(context.getApplicationContext());
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE contacts " +
                "(" +
                "_id INTEGER PRIMARY KEY," +
                "address TEXT UNIQUE," +
                "name TEXT," +
                "outgoing INTEGER DEFAULT 0," +
                "friendReqAccepted INTEGER DEFAULT 0," +
                "incoming INTEGER DEFAULT 0," +
                "pending INTEGER DEFAULT 0" +
                ")");

        db.execSQL("CREATE INDEX contactindex ON contacts (" +
                "incoming," +
                "name," +
                "address" +
                ")");

        db.execSQL("CREATE TABLE messages " +
                "(" +
                "_id INTEGER PRIMARY KEY," +
                "sender TEXT," +
                "receiver TEXT," +
                "content TEXT," +
                "time INTEGER," +
                "pending INTEGER," +
                "type INTEGER," +
                "UNIQUE" +
                "(" +
                "sender," +
                "receiver," +
                "time" +
                ")" +
                ")");

        db.execSQL("CREATE TABLE fileshare " +
                "(" +
                "_id INTEGER PRIMARY KEY," +
                "message_id INTEGER," +
                "filename TEXT," +
                "filepath TEXT," +
                "should_serve INTEGER," +
                "UNIQUE(message_id)" +
                ")");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }


    synchronized private SharedPreferences prefs() {
        return context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    public synchronized String get(String key) {
        return prefs().getString(key, "");
    }

    public synchronized String get(String key, String defaultValue) {
        return prefs().getString(key, defaultValue);
    }

    public synchronized void put(String key, String value) {
        prefs().edit().putString(key, value).apply();
    }

    public synchronized String getName() {
        return get("name");
    }

    public synchronized void setName(String name) {
        put("name", name);
    }

    public synchronized int getNotifications() {
        return prefs().getInt("notifications", 0);
    }

    public synchronized void setNotifications(int n) {
        prefs().edit().putInt("notifications", n).apply();
    }

    public synchronized void addNotification() {
        setNotifications(getNotifications() + 1);
    }

    public synchronized void clearNotifications() {
        setNotifications(0);
    }

    public synchronized int getNewRequests() {
        return prefs().getInt("requests", 0);
    }

    public synchronized void setNewRequests(int n) {
        prefs().edit().putInt("requests", n).apply();
    }

    public synchronized void addNewRequest() {
        setNewRequests(getNewRequests() + 1);
    }

}
