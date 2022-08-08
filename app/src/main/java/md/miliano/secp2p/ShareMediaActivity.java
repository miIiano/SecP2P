package md.miliano.secp2p;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import md.miliano.secp2p.adapters.ContactsAdapter;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.service.VideoTranscodeService;
import md.miliano.secp2p.tor.Client;
import md.miliano.secp2p.tor.FileServer;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.utils.MimeTypes;
import md.miliano.secp2p.utils.Util;

public class ShareMediaActivity extends AppCompatActivity {

    private static final String TAG = "ShareMediaActivity";
    private RecyclerView mRVContacts;
    private ImageView imvwImage;
    private TextView txtFilename;
    private TextView txtFileMime;
    private TextView txtFileSize;
    private EditText txtMessage;
    public ContactsAdapter mContactsAdapter;
    private RealmResults<Contact> mContacts;

    public static final int PR_WRITE_EXTERNAL_STORAGE = 10;

    private int mAttachFileType;
    private String mFilePath;

    private Uri mFileUri;
    private String mMimeType;

    private boolean mCanSendMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_media);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRVContacts = findViewById(R.id.rcvwContacts);

        mContacts = Realm.getDefaultInstance().where(Contact.class)
                .equalTo("incoming", 0)
                .findAll()
                .sort("lastMessageTime", Sort.DESCENDING);

        mRVContacts.setLayoutManager(new LinearLayoutManager(this));
        mContactsAdapter = new ContactsAdapter(mContacts, this, true);
        mRVContacts.setAdapter(mContactsAdapter);
        mRVContacts.setHasFixedSize(true);
        mRVContacts.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        imvwImage = findViewById(R.id.imvwImage);
        txtFilename = findViewById(R.id.txtFileName);
        txtFileMime = findViewById(R.id.txtFileMime);
        txtFileSize = findViewById(R.id.txtFileSize);
        txtMessage = findViewById(R.id.txtDescription);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {

            if (!mCanSendMessage) {
                Toast.makeText(this, "Unable find media for sending", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            if (mContactsAdapter.mSelectedPositions.size() <= 0) {
                Toast.makeText(this, "Please select some contact", Toast.LENGTH_SHORT).show();
                return;
            }
            String sender = Tor.getInstance(this).getID();
            String message = txtMessage.getText().toString();
            message = message.trim();
            if (mAttachFileType == Message.TYPE_IMAGE) {
                new ResizeImage(sender, message, mAttachFileType).execute(mFilePath);
            } else if (mAttachFileType == Message.TYPE_VIDEO) {
                Bitmap bitmap = ((BitmapDrawable) imvwImage.getDrawable()).getBitmap();
                Bitmap imageBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, false);
                String thumbFileName = UUID.randomUUID().toString();
                String thumbnailFilePath = Util.writeBitmapCache(this, imageBitmap, thumbFileName);
                imageBitmap.recycle();

                Object[] positions = mContactsAdapter.mSelectedPositions.toArray();
                String[] addresses = new String[positions.length];
                for (int i = 0; i < positions.length; i++) {
                    addresses[i] = mContactsAdapter.getItem((Integer) positions[i]).getAddress();
                }
                if (checkSizeIsLarge(mFilePath)) {
                    VideoTranscodeService.startVideoTranscode(this,
                            mFilePath, new File(getFilesDir(), UUID.randomUUID().toString() + ".mp4").getAbsolutePath(), addresses, message, mMimeType, mAttachFileType, thumbnailFilePath);
                } else {
                    for (String a : addresses) {
                        try {
                            sendMessage(sender, a, message, mFilePath, mMimeType, mAttachFileType, Util.readSmallFile(getApplicationContext(), thumbnailFilePath));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                finish();
            } else {
                Object[] positions = mContactsAdapter.mSelectedPositions.toArray();
                for (Object o : positions) {
                    Contact contact = mContactsAdapter.getItem((Integer) o);
                    sendMessage(sender, contact.getAddress(), message, mFilePath, mMimeType, mAttachFileType, null);
                }
                finish();
            }
        });

        Intent intent = getIntent();
        String action = intent.getAction();
        mMimeType = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && mMimeType != null) {
            mFileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if ("text/plain".equals(mMimeType)) {
                Toast.makeText(this, "Text cannot be shared", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        log("Got share intent " + mMimeType + " " + mFileUri.getPath());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            handIntent(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PR_WRITE_EXTERNAL_STORAGE);
        }
    }

    private boolean checkSizeIsLarge(String filepath) {
        File file = new File(filepath);
        long fileSizeInBytes = file.length();
        long fileSizeInKB = fileSizeInBytes / 1024;
        long fileSizeInMB = fileSizeInKB / 1024;
        return fileSizeInMB > 5;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_share_media, menu);

        MenuItem mi = menu.findItem(R.id.action_search);
        if (mi != null) {
            SearchView searchView = (SearchView) mi.getActionView();
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    mContactsAdapter.filter(query);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    mContactsAdapter.filter(newText);
                    return false;
                }
            });
            searchView.setOnCloseListener(() -> {
                mContactsAdapter.filter(null);
                Log.d(TAG, "onCreateOptionsMenu: Closing Search View");
                return false;
            });

        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PR_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    handIntent(true);
                } else {
                    Toast.makeText(this, "External storage access denied", Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }
        }
    }

    private void handIntent(boolean callCopyFromUrlAgain) {
        if (mFileUri == null) {
            Toast.makeText(this, "Shared file is not present", Toast.LENGTH_SHORT).show();
            return;
        }
        log("Trying to process " + mFileUri + " with mimeType: " + mMimeType);
        String filePath;
        try {
            filePath = Util.getFilePath(this, mFileUri);
            log("Got file path: " + filePath);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            filePath = mFileUri.getPath();
            log("Unable to get the file path, using URI path: " + filePath);
        }
        mFilePath = filePath;
        if (mMimeType == null) {
            mMimeType = FileServer.getMimeType(filePath);
            if (mMimeType == null) {
                if (callCopyFromUrlAgain)
                    new CopyFromUri(this).execute(mFileUri);
            }
        }
        mAttachFileType = FileServer.getMessageType(mMimeType);
        File file = new File(filePath);
        if (file.exists()) {
            mCanSendMessage = true;
            txtFilename.setText(file.getName());
            txtFileMime.setText(mMimeType);
            txtFileSize.setText(Util.humanReadableByteCountBin(file.length()));
            if (mMimeType.startsWith("image") || mMimeType.startsWith("video")) {
                Glide.with(this).load(Uri.fromFile(file)).into(imvwImage);
            } else if (mMimeType.startsWith("audio")) {
                imvwImage.setImageResource(R.drawable.ic_play_circle_svg);
                imvwImage.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent), PorterDuff.Mode.SRC_ATOP);

            } else {
                imvwImage.setImageResource(Util.getResourceForFileType(filePath));
            }
        } else {
            if (callCopyFromUrlAgain)
                new CopyFromUri(this).execute(mFileUri);
        }
    }

    private void sendMessage(String sender, String receiver, String message,
                             String filePath,
                             String mimeType,
                             int type, byte[] thumbnail) {
        Message.addPendingOutgoingMessage(
                sender,
                receiver,
                message,
                new File(filePath).getName(),
                filePath,
                mimeType,
                type != -1 ? type : mAttachFileType,
                thumbnail);
        Client.getInstance(this).startSendPendingMessages(receiver);
    }


    class ResizeImage extends AsyncTask<String, Void, String> {

        private final String mSender;
        private final String mMessage;
        private final int mType;
        private byte[] thumbnail;

        public ResizeImage(String sender, String message, int type) {
            mSender = sender;
            mMessage = message;
            mType = type;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Toast.makeText(ShareMediaActivity.this, "Resizing image", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(String... strings) {
            File inputFile = new File(strings[0]);
            File file = new File(getFilesDir(), inputFile.getName());
            Bitmap out = Util.lessResolution(inputFile.getAbsolutePath(), 1280, 720);
            log("Image resized: " + out.getWidth() + "x" + out.getHeight());
            FileOutputStream fOut;
            try {
                fOut = new FileOutputStream(file);
                out.compress(Bitmap.CompressFormat.JPEG, 75, fOut);
                fOut.flush();
                fOut.close();
                int THUMBNAIL_SIZE = 64;
                Bitmap imageBitmap = Bitmap.createScaledBitmap(out, THUMBNAIL_SIZE, THUMBNAIL_SIZE, false);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                thumbnail = baos.toByteArray();

                out.recycle();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return file.getAbsolutePath();
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if (s != null) {
                Toast.makeText(ShareMediaActivity.this, "Image resized, sending...", Toast.LENGTH_SHORT).show();
                Object[] positions = mContactsAdapter.mSelectedPositions.toArray();
                for (Object o : positions) {
                    Contact contact = mContactsAdapter.getItem((Integer) o);
                    sendMessage(mSender, contact.getAddress(), mMessage, s, mMimeType, mType, thumbnail);
                }
            }
            finish();
        }
    }

    private void log(String s) {
        Log.d("ShareMediaActivity", s);
    }

    private class CopyFromUri extends AsyncTask<Uri, Void, String> {

        private ProgressDialog progressDialog;
        private final Context mContext;

        public CopyFromUri(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(mContext);
            progressDialog.setMessage("Copying file...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Uri... uris) {
            Uri fileUri = uris[0];

            InputStream is = null;
            FileOutputStream fos = null;
            File outputFile = null;
            try {
                is = mContext.getContentResolver().openInputStream(fileUri);
                File file = new File(fileUri.getPath());
                outputFile = new File(mContext.getFilesDir(), file.getName() + "." + MimeTypes.getDefaultExt(mMimeType));
                fos = new FileOutputStream(outputFile);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }


            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                    if (fos != null) {
                        fos.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return outputFile.getAbsolutePath();
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            progressDialog.dismiss();
            mFilePath = s;
            if (mFilePath == null) {
                mCanSendMessage = false;
            } else {
                mFileUri = Uri.fromFile(new File(mFilePath));
                handIntent(false);
            }
        }
    }
}
