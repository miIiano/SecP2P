package md.miliano.secp2p.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import io.realm.Realm;
import md.miliano.secp2p.db.Message;

public class DeleteMessages extends AsyncTask<String, Void, Boolean> {

    private ProgressDialog progressDialog;
    private Context mContext;

    private static final String TAG = "DeleteMessages";

    public DeleteMessages(Context context) {
        mContext = context;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog = new ProgressDialog(mContext);
        progressDialog.setMessage("Deleting all messages");
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(String... strings) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        realm.where(Message.class)
                .equalTo("sender", strings[0])
                .or()
                .equalTo("receiver", strings[0])
                .findAll().deleteAllFromRealm();

        Log.d(TAG, "Deleted all messages now deleting media files");

        deleteDir(new File(mContext.getFilesDir(), strings[0]));

        Log.d(TAG, "Files have been deleted");
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
        Toast.makeText(mContext, "Messages deleted", Toast.LENGTH_SHORT).show();
    }
}