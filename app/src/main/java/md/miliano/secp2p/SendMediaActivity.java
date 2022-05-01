package md.miliano.secp2p;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.service.VideoTranscodeService;
import md.miliano.secp2p.tor.Client;
import md.miliano.secp2p.tor.FileServer;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.utils.Util;

public class SendMediaActivity extends AppCompatActivity {

    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_TYPE = "file_type";

    private ImageView imvwImage;
    private EditText txtDescription;

    private String mAddress;
    private String mFilePath;
    private int mAttachFileType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_media);

        imvwImage = findViewById(R.id.imageView);
        txtDescription = findViewById(R.id.txtDescription);

        Bundle extra = getIntent().getExtras();
        if (extra != null) {
            mAddress = extra.getString(EXTRA_ADDRESS);
            mFilePath = extra.getString(EXTRA_FILE_PATH);
            mAttachFileType = extra.getInt(EXTRA_FILE_TYPE);
        }

        if (!new File(mFilePath).exists()) {
            Toast.makeText(this, "File not found : " + mFilePath, Toast.LENGTH_SHORT).show();
            finish();
        }


        String mimeType = FileServer.getMimeType(mFilePath);
        mAttachFileType = mimeType != null ? FileServer.getMessageType(mimeType) : mAttachFileType;

        if (mAttachFileType == Message.TYPE_VIDEO || mAttachFileType == Message.TYPE_IMAGE) {
            Glide.with(this).load(Uri.fromFile(new File(mFilePath))).into(imvwImage);
        } else {
            imvwImage.setImageResource(Util.getResourceForFileType(mFilePath));
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view1 -> {
            String sender = Tor.getInstance(this).getID();
            if (sender == null || sender.trim().equals("")) {
                Toast.makeText(this, "Unable to get sender", Toast.LENGTH_SHORT).show();
                return;
            }

            String message = txtDescription.getText().toString();
            message = message.trim();

            if (mAttachFileType == Message.TYPE_IMAGE) {
                new ResizeImage(sender, message, mAttachFileType).execute(mFilePath);
            } else if (mAttachFileType == Message.TYPE_VIDEO) {
                Bitmap bitmap = ((BitmapDrawable) imvwImage.getDrawable()).getBitmap();
                Bitmap imageBitmap = ThumbnailUtils.extractThumbnail(bitmap, 64, 64);
                String thumbFileName = UUID.randomUUID().toString();
                String thumbnailFilePath = Util.writeBitmapCache(this, imageBitmap, thumbFileName);
                imageBitmap.recycle();

                VideoTranscodeService.startVideoTranscode(this,
                        mFilePath, new File(getFilesDir(), UUID.randomUUID().toString() + ".mp4").getAbsolutePath(), new String[]{mAddress}, message, mimeType, mAttachFileType, thumbnailFilePath);
                finish();
            } else {
                sendMessage(sender, message, mFilePath, mimeType, mAttachFileType, null);
                finish();
            }
        });
    }

    private void sendMessage(String sender, String message, String filePath, String mimeType, int type, byte[] thumbnail) {
        Message.addPendingOutgoingMessage(
                sender,
                mAddress,
                message,
                new File(filePath).getName(),
                filePath,
                mimeType,
                type != -1 ? type : mAttachFileType,
                thumbnail);
        Client.getInstance(this).startSendPendingMessages(mAddress);
    }

    void log(String s) {
        Log.d("SendMediaActivity", s);
    }

    class ResizeImage extends AsyncTask<String, Void, String> {

        private static final String TAG = "ResizeImage";
        private String mSender;
        private String mMessage;
        private int mType;
        private byte[] thumbnail;

        public ResizeImage(String sender, String message, int type) {
            mSender = sender;
            mMessage = message;
            mType = type;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Toast.makeText(SendMediaActivity.this, "Resizing image", Toast.LENGTH_SHORT).show();
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

                Bitmap imageBitmap = ThumbnailUtils.extractThumbnail(out, Util.THUMBNAIL_SIZE, Util.THUMBNAIL_SIZE);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
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
                Log.d(TAG, "onPostExecute: Resize file path: " + s);
                Toast.makeText(SendMediaActivity.this, "Image resized, sending...", Toast.LENGTH_SHORT).show();
                sendMessage(mSender, mMessage, s, "image/jpeg", mType, thumbnail);
            }
            finish();
        }
    }
}
