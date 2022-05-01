package md.miliano.secp2p.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import io.realm.Realm;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Message;

public class DeleteContact extends AsyncTask<Long, Void, Boolean> {

    private ProgressDialog progressDialog;
    private Context mContext;

    private static final String TAG = "DeleteContact";

    public DeleteContact(Context context) {
        mContext = context;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog = new ProgressDialog(mContext);
        progressDialog.setMessage("Deleting contact");
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Long... ids) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        Contact contact = realm.where(Contact.class).equalTo("_id", ids[0]).findFirst();
        realm.where(Message.class)
                .equalTo("sender", contact.getAddress())
                .or()
                .equalTo("receiver", contact.getAddress())
                .findAll().deleteAllFromRealm();

        Log.d(TAG, "Deleted all messages now deleting media files");

        deleteDir(new File(mContext.getFilesDir(), contact.getAddress()));

        Log.d(TAG, "Files have been deleted");
        contact.deleteFromRealm();
        Log.d(TAG, "Contact Deleted");
        realm.commitTransaction();
        realm.close();
        return null;
    }

    private void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        super.onPostExecute(aBoolean);
        progressDialog.dismiss();
        Toast.makeText(mContext, "Contact deleted", Toast.LENGTH_SHORT).show();
    }
}