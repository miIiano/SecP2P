package md.miliano.secp2p.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import io.realm.Realm;
import io.realm.RealmResults;
import md.miliano.secp2p.crypto.AdvancedCrypto;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Database;
import md.miliano.secp2p.db.Message;

public class CreateZip extends AsyncTask<Void, Void, String> {

    private final File EXTERNAL_FOLDER;
    private final ProgressDialog pd;
    private final Context mContext;
    private final String mPassword;

    public CreateZip(Context context, String password) {
        mContext = context;
        EXTERNAL_FOLDER = new File(Environment.getExternalStoragePublicDirectory("SecP2P").getAbsolutePath());
        mPassword = password;
        pd = new ProgressDialog(context);
        pd.setMessage("Creating export zip");
        pd.setCancelable(false);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        pd.show();
    }

    @Override
    protected String doInBackground(Void... voids) {

        if (!EXTERNAL_FOLDER.exists()) EXTERNAL_FOLDER.mkdir();
        File tempDest = new File(mContext.getFilesDir(), "secP2P.zip");
        ZipManager zipMgr = new ZipManager();
        zipMgr.makeZipFile(tempDest.getAbsolutePath());
        String hiddenSvcPrivateKey = "tor/torSvc/hs_ed25519_secret_key";
        String hiddenSvcPublicKey = "tor/torSvc/hs_ed25519_public_key";
        String hostname = "tor/torSvc/hostname";
        String ecdsaPrivateKey = "tor/torSvc/ecdsaPrivateKey";
        String ecdsaPublicKey = "tor/torSvc/ecdsaPublicKey";

        String settings = "settings.prop";
        String messageFile = "messages.json";
        String contactFile = "contacts.json";

        Properties properties = new Properties();
        properties.setProperty("name", Database.getInstance(mContext).get("name"));
        properties.setProperty("use_dark_theme", Settings.getPrefs(mContext).getBoolean("use_dark_mode", true) + "");
        try {
            properties.store(new FileOutputStream(new File(mContext.getCacheDir(), settings)), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        zipMgr.addZipFile(new File(mContext.getFilesDir(), hiddenSvcPrivateKey).getAbsolutePath(), hiddenSvcPrivateKey);
        zipMgr.addZipFile(new File(mContext.getFilesDir(), hiddenSvcPublicKey).getAbsolutePath(), hiddenSvcPublicKey);
        zipMgr.addZipFile(new File(mContext.getFilesDir(), ecdsaPrivateKey).getAbsolutePath(), ecdsaPrivateKey);
        zipMgr.addZipFile(new File(mContext.getFilesDir(), ecdsaPublicKey).getAbsolutePath(), ecdsaPublicKey);
        zipMgr.addZipFile(new File(mContext.getFilesDir(), hostname).getAbsolutePath(), hostname);
        zipMgr.addZipFile(new File(mContext.getCacheDir(), settings).getAbsolutePath(), settings);
        try {
            zipMgr.addZipFile(createMessageFile().getAbsolutePath(), messageFile);
            zipMgr.addZipFile(createContactFile().getAbsolutePath(), contactFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        zipMgr.closeZip();
        File destination = new File(EXTERNAL_FOLDER, properties.get("name") + "-secP2P.zip");
        try {
            AdvancedCrypto advancedCrypto = new AdvancedCrypto(mPassword);
            advancedCrypto.encryptFile(tempDest.getAbsolutePath(), destination.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return destination.getAbsolutePath();
    }

    private File createMessageFile() throws IOException {
        File messageFile = new File(mContext.getCacheDir(), "messages.json");
        Gson gson = new Gson();
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Message> messages = realm.where(Message.class).findAll();
        JsonWriter jsonWriter = new JsonWriter(new FileWriter(messageFile));
        jsonWriter.beginArray();
        for (int i = 0; i < messages.size(); i++) {
            jsonWriter.jsonValue(gson.toJson(realm.copyFromRealm(messages.get(i))));
            jsonWriter.flush();
        }
        jsonWriter.endArray();
        jsonWriter.close();
        realm.close();
        return messageFile;
    }

    private File createContactFile() throws IOException {
        File contactFile = new File(mContext.getCacheDir(), "contacts.json");
        Gson gson = new Gson();
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Contact> contacts = realm.where(Contact.class).findAll();
        JsonWriter jsonWriter = new JsonWriter(new FileWriter(contactFile));
        jsonWriter.beginArray();
        for (int i = 0; i < contacts.size(); i++) {
            jsonWriter.jsonValue(gson.toJson(realm.copyFromRealm(contacts.get(i))));
            jsonWriter.flush();
        }
        jsonWriter.endArray();
        jsonWriter.close();
        realm.close();
        return contactFile;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        pd.dismiss();
        Toast.makeText(mContext, "Zip file created: " + s, Toast.LENGTH_SHORT).show();
    }
}