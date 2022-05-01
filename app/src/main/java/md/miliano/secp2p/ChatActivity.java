package md.miliano.secp2p;

import static androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.keenfin.audioview.AudioService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import md.miliano.secp2p.adapters.ChatAdapter;
import md.miliano.secp2p.adapters.ChatHolderText;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.service.SecP2PHostService;
import md.miliano.secp2p.tor.Client;
import md.miliano.secp2p.tor.FileServer;
import md.miliano.secp2p.tor.Notifier;
import md.miliano.secp2p.tor.Server;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.ui.DeleteMessages;
import md.miliano.secp2p.utils.Settings;
import md.miliano.secp2p.utils.Util;
import md.miliano.secp2p.view.ContentInfoEditText;
import md.miliano.secp2p.view.CustomSearchView;
import md.miliano.secp2p.view.TorStatusView;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = ChatActivity.class.getName();
    private static final int REQUEST_TAKE_PHOTO = 120;
    private static final int REQUEST_VIDEO_CAPTURE = 121;
    private ChatAdapter mChatAdapter;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView mRVChat;
    private Tor mTor;
    private String address;
    private Client mClient;
    private Contact mContact;

    public static final int REQUEST_PICK_FILE = 13;

    private RealmResults<Message> mMessages;

    private long rep = 0;
    private Timer timer;

    private boolean mSendMessage;

    private int mAttachFileType = Message.TYPE_IMAGE;

    private MenuItem mSelectedMenuItem;

    public static final int PR_WRITE_EXTERNAL_STORAGE = 10;
    public static final int PR_RECORD_AUDIO = 11;

    private String mCurrentPhotoPath;
    private File mMediaFolder;

    private CustomSearchView mSearchView;

    private ItemTouchHelper itemTouchHelper;


    private RelativeLayout rlReply;
    private ImageView imvwReply;
    private TextView txtReplyText;
    private Message mQuotedMessage;
    public Map<String, Integer> mIdPositionMap = new HashMap<>();
    private int mQuotedMessageRequested = -1;

    private TextView txtFabCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setLogo(R.mipmap.ic_launcher);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        mTor = Tor.getInstance(this);

        mClient = Client.getInstance(this);
        address = getIntent().getDataString();

        if (address.contains(":"))
            address = address.substring(address.indexOf(':') + 1);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Log.i("ADDRESS", address);

        Realm realm = Realm.getDefaultInstance();
        mContact = realm.where(Contact.class).equalTo("address", address).findFirst();
        if (mContact == null) {
            Toast.makeText(this, "Unable to find address: " + address, Toast.LENGTH_SHORT).show();
            finish();
        }

        mMediaFolder = new File(getFilesDir(), address);
        if (!mMediaFolder.exists()) {
            mMediaFolder.mkdir();
        }

        String name = mContact.getName();
        if (name.isEmpty()) {
            getSupportActionBar().setTitle(address);
        } else {
            getSupportActionBar().setTitle(name);
            getSupportActionBar().setSubtitle(address);
        }

        mRVChat = findViewById(R.id.rcvwChat);

        mLayoutManager = new LinearLayoutManager(this);
        mRVChat.setLayoutManager(mLayoutManager);

        RecyclerView.ItemAnimator animator = mRVChat.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        itemTouchHelper = new ItemTouchHelper(new SwipeController());
        itemTouchHelper.attachToRecyclerView(mRVChat);
        mMessages = Realm.getDefaultInstance().where(Message.class)
                .beginGroup().equalTo("sender", mTor.getID()).and().equalTo("receiver", address).endGroup()
                .or()
                .beginGroup().equalTo("sender", address).and().equalTo("receiver", mTor.getID()).endGroup()
                .sort("stableId", Sort.ASCENDING)
                .findAll();

        findViewById(R.id.noMessages).setVisibility(mMessages.size() > 0 ? View.GONE : View.VISIBLE);

        mMessages.addChangeListener((messages, changeSet) -> {
            log("Realm data set changed: ");
            updateIdPositionMap();
            findViewById(R.id.noMessages).setVisibility(mMessages.size() > 0 ? View.GONE : View.VISIBLE);
            if (shouldScroll()) {
                mRVChat.smoothScrollToPosition(Math.max(0, mMessages.size() - 1));
                txtFabCount.setText(null);
                txtFabCount.setVisibility(View.GONE);
                if (mContact.getPending() > 0) {
                    Realm realm1 = Realm.getDefaultInstance();
                    realm1.beginTransaction();
                    mContact.setPending(0);
                    realm1.commitTransaction();
                    realm1.close();
                }
            } else {
                if (mContact.getPending() > 0) {
                    txtFabCount.setText(mContact.getPending() + "");
                    txtFabCount.setVisibility(View.VISIBLE);
                }
            }
        });

        mChatAdapter = new ChatAdapter(mMessages, this, mContact);
        mRVChat.setAdapter(mChatAdapter);

        mRVChat.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if ((dy > 0 && findViewById(R.id.fab).getVisibility() == View.VISIBLE) && shouldScroll()) {
                    ((FloatingActionButton) findViewById(R.id.fab)).hide();
                    txtFabCount.setVisibility(View.GONE);
                    if (mContact.getPending() > 0) {
                        Realm realm = Realm.getDefaultInstance();
                        realm.beginTransaction();
                        mContact.setPending(0);
                        realm.commitTransaction();
                        realm.close();
                        txtFabCount.setText(null);
                    }
                } else if (dy < 0 && findViewById(R.id.fab).getVisibility() != View.VISIBLE && !shouldScroll()) {
                    ((FloatingActionButton) findViewById(R.id.fab)).show();
                    if (mContact.getPending() > 0) {
                        txtFabCount.setText(mContact.getPending() + "");
                        txtFabCount.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        mRVChat.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && findViewById(R.id.fab).getVisibility() != View.VISIBLE) {
                if (mChatAdapter.getItemCount() - 1 > 0) {
                    mRVChat.postDelayed(() -> mRVChat.smoothScrollToPosition(
                            mRVChat.getAdapter().getItemCount() - 1), 100);
                }
            }
        });

        updateIdPositionMap();

        final ImageButton send = findViewById(R.id.send);
        send.setOnClickListener(view -> {
            if (mSendMessage) {
                String sender = mTor.getID();
                if (sender == null || sender.trim().equals("")) {
                    sendPendingAndUpdate();
                    return;
                }
                String message = ((EditText) findViewById(R.id.txtMessage)).getText().toString();
                message = message.trim();
                if (message.equals("")) return;
                String quotedMessageId = null;
                String quotedMessageSender = null;
                String quotedMessageContent = null;
                if (mQuotedMessage != null) {
                    quotedMessageId = mQuotedMessage.getPrimaryKey();
                    quotedMessageSender = mQuotedMessage.getSender();
                    quotedMessageContent = mQuotedMessage.getContent();
                }
                log("Sending message with quoteId: " + quotedMessageId);
                Message.addPendingOutgoingMessage(sender, address, message, quotedMessageId, quotedMessageSender, quotedMessageContent);
                ((EditText) findViewById(R.id.txtMessage)).setText("");
                sendPendingAndUpdate();
                rep = 0;
                showHideReplyView(null);
                if (!mRVChat.canScrollVertically(1)) {
                    ((FloatingActionButton) findViewById(R.id.fab)).show();
                }
            } else {
                showAttachPopUpMenu();
            }
        });

        final ImageButton audio = findViewById(R.id.audio);
        audio.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(ChatActivity.this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ChatActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PR_RECORD_AUDIO);
                return;
            }
            TimerDialog dialog = new TimerDialog();
            dialog.show(getSupportFragmentManager(), "Timer");
        });

        txtFabCount = findViewById(R.id.txtFabCount);

        findViewById(R.id.fab).setOnClickListener(view -> {
            mRVChat.smoothScrollToPosition(Math.max(0, mMessages.size() - 1));
        });
        Intent i = new Intent(this, SecP2PHostService.class);
        i.setAction(Util.FGSAction.START.name());
        ContextCompat.startForegroundService(this, i);

        final ContentInfoEditText txtMessage = findViewById(R.id.txtMessage);
        send.setClickable(true);
        txtMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() == 0) {
                    mSendMessage = false;
                    send.setImageResource(R.drawable.ic_attach);
                } else {
                    send.setImageResource(R.drawable.ic_send);
                    mSendMessage = true;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        txtMessage.setKeyBoardInputCallbackListener((inputContentInfo, flags, opts) -> {
            Log.d(TAG, "Call from keyboard: " + inputContentInfo.getDescription().describeContents());
            InputStream is = null;
            FileOutputStream fos = null;
            File outputFile = null;
            Uri fileUri = inputContentInfo.getContentUri();
            try {
                is = getContentResolver().openInputStream(fileUri);
                File file = new File(fileUri.getPath());
                outputFile = new File(getFilesDir(), file.getName());
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
            if (outputFile != null) {
                String sender = mTor.getID();
                Bitmap bitmap = ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeFile(outputFile.getAbsolutePath()), Util.THUMBNAIL_SIZE, Util.THUMBNAIL_SIZE);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                byte[] thumbnail = baos.toByteArray();
                sendMessage(
                        sender,
                        mContact.getAddress(),
                        null,
                        outputFile.getAbsolutePath(),
                        inputContentInfo.getDescription().getMimeType(0),
                        Message.TYPE_IMAGE,
                        thumbnail);
            }
            inputContentInfo.releasePermission();
        });

        mRVChat.scrollToPosition(Math.max(0, mMessages.size() - 1));
        rlReply = findViewById(R.id.clReply);
        rlReply.setVisibility(View.GONE);
        rlReply.setOnClickListener(view -> {
            new Handler().postDelayed(() -> scrollToQuotedMessage(mQuotedMessage), 200);
        });
        txtReplyText = findViewById(R.id.txtReplyText);
        imvwReply = findViewById(R.id.imvwImage);
        findViewById(R.id.imvwClose).setOnClickListener(view -> showHideReplyView(null));
    }

    private boolean shouldScroll() {
        boolean result;
        int lastVisibleItem = mLayoutManager.findLastCompletelyVisibleItemPosition();
        result = lastVisibleItem >= mChatAdapter.getItemCount() - 5;
        return result;
    }

    private void updateIdPositionMap() {
        mIdPositionMap.clear();
        for (int i = 0; i < mChatAdapter.getItemCount(); i++) {
            mIdPositionMap.put(mChatAdapter.getItem(i).getPrimaryKey(), i);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_chat, menu);
        MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        if (searchMenuItem != null) {
            mSearchView = (CustomSearchView) searchMenuItem.getActionView();
            mSearchView.txtSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });
            mSearchView.txtSearch.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_SEARCH) {
                    mChatAdapter.setSearchText(textView.getText().toString());
                    mChatAdapter.notifyDataSetChanged();
                    return true;
                }
                return false;
            });
            mSearchView.imvwDown.setOnClickListener(view -> {
                // TODO implement next searched message
            });
            mSearchView.imvwUp.setOnClickListener(view -> {
                // TODO implement previous searched message
            });
        }
        return super.onCreateOptionsMenu(menu);
    }

    public void onAudioRecordComplete(String path) {
        Log.d(TAG, "onAudioRecordComplete: " + path);
        String sender = mTor.getID();
        if (sender == null || sender.trim().equals("")) {
            sendPendingAndUpdate();
            return;
        }
        File file = new File(path);
        String message = file.getName();
        message = message.trim();
        if (message.equals("")) return;
        Message.addPendingOutgoingMessage(sender, address, message, file.getName(),
                file.getAbsolutePath(),
                FileServer.getMimeType(file.getAbsolutePath()),
                Message.TYPE_AUDIO, null);
        ((EditText) findViewById(R.id.txtMessage)).setText("");
        sendPendingAndUpdate();
        rep = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent audioService = new Intent(this, AudioService.class);
        stopService(audioService);
        mMessages.removeAllChangeListeners();
    }

    private void showAttachPopUpMenu() {
        PopupMenu popup = new PopupMenu(this, findViewById(R.id.send));
        Menu menu = popup.getMenu();

        popup.getMenuInflater()
                .inflate(R.menu.menu_attachment, menu);

        popup.setOnMenuItemClickListener(item -> {
            mSelectedMenuItem = item;
            if (ContextCompat.checkSelfPermission(ChatActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(ChatActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PR_WRITE_EXTERNAL_STORAGE);
                return true;
            }
            showPickFileIntent(mSelectedMenuItem);
            return true;
        });
        try {
            Method method = popup.getMenu().getClass().getDeclaredMethod("setOptionalIconsVisible", boolean.class);
            method.setAccessible(true);
            method.invoke(popup.getMenu(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        popup.show();
    }

    private void showPickFileIntent(MenuItem item) {
        Intent intent = new Intent();
        String title = "Select File";
        switch (item.getItemId()) {
            case R.id.menu_take_picture:
                intent = null;
                dispatchTakePictureIntent();
                mAttachFileType = Message.TYPE_IMAGE;
                break;
            case R.id.menu_record_video:
                intent = null;
                dispatchTakeVideoIntent();
                mAttachFileType = Message.TYPE_VIDEO;
                break;
            case R.id.menu_attach_image:
                intent.setType("image/*");
                title = "Select Image";
                mAttachFileType = Message.TYPE_IMAGE;
                break;
            case R.id.menu_attach_video:
                intent.setType("video/*");
                title = "Select Video";
                mAttachFileType = Message.TYPE_VIDEO;
                break;
            case R.id.menu_attach_file:
                intent.setType("*/*");
                mAttachFileType = Message.TYPE_FILE;
                break;
        }
        if (intent != null) {
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, title), REQUEST_PICK_FILE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PR_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showPickFileIntent(mSelectedMenuItem);
                } else {
                    Toast.makeText(this, "External storage access denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case PR_RECORD_AUDIO:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    TimerDialog dialog = new TimerDialog();
                    dialog.show(getSupportFragmentManager(), "Timer");
                } else {
                    Toast.makeText(this, "Record audio access denied", Toast.LENGTH_SHORT).show();
                }
                return;
        }
    }

    private void showImage(Uri uri) throws URISyntaxException {
        String filePath = Util.getFilePath(this, uri);

        Intent sendMediaActivity = new Intent(this, SendMediaActivity.class);
        sendMediaActivity.putExtra(SendMediaActivity.EXTRA_ADDRESS, address);
        sendMediaActivity.putExtra(SendMediaActivity.EXTRA_FILE_TYPE, mAttachFileType);
        sendMediaActivity.putExtra(SendMediaActivity.EXTRA_FILE_PATH, filePath);
        startActivity(sendMediaActivity);

        rep = 0;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Unable to launch camera application", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    private File createImageFile() throws IOException {
        String imageFileName = "JPEG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_";
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                mMediaFolder
        );
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_PICK_FILE) {
            Log.d(TAG, "onActivityResult: " + data.getData().getPath());
            try {
                showImage(data.getData());
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Unable to load file", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_TAKE_PHOTO) {
            try {
                showImage(Uri.parse(mCurrentPhotoPath));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        if (requestCode == REQUEST_VIDEO_CAPTURE) {
            try {
                showImage(data.getData());
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void sendPendingAndUpdate() {
        mClient.startSendPendingMessages(address);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.action_clear_chat) {
            askAndDelete();
        }
        return super.onOptionsItemSelected(item);
    }

    private void askAndDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_message)
                .setMessage(getString(R.string.really_delete_all_message))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    new DeleteMessages(this).execute(mContact.getAddress());
                })
                .setNegativeButton(R.string.No, (dialog, which) -> {
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Server.getInstance(this).setFileDownloadListener(mFileDownloadListener);
        Tor.getInstance(this).addListener(mTorListener);

        mClient.setStatusListener(loading -> runOnUiThread(() -> {
            Log.i("LOADING", "" + loading);
            findViewById(R.id.progressbar).setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        }));

        String message = Settings.getPrefs(this).getString(mContact.getAddress() + "_message", null);
        if (message != null) {
            ((EditText) findViewById(R.id.txtMessage)).setText(message);
        }

        String quotedPrimaryKey = Settings.getPrefs(this).getString(mContact.getAddress() + "_quote", null);
        if (quotedPrimaryKey != null) {
            mQuotedMessage = Realm.getDefaultInstance().where(Message.class).equalTo("primaryKey", quotedPrimaryKey).findFirst();
            showHideReplyView(mQuotedMessage);
        }

        sendPendingAndUpdate();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                rep++;
                if (rep > 5 && rep % 5 != 0) {
                    log("wait");
                    return;
                }
                log("update");
                if (mClient.isBusy()) {
                    log("abort update, client busy");
                    return;
                } else {
                    log("do update");
                    mClient.startSendPendingMessages(address);
                }

            }
        }, 0, 1000 * 60);

        Notifier.getInstance(this).onResumeActivity();

        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        mContact.setPending(0);
        realm.commitTransaction();
        realm.close();

        ((TorStatusView) findViewById(R.id.torStatusView)).update();
        Intent i = new Intent(this, SecP2PHostService.class);
        i.setAction(Util.FGSAction.START.name());
        ContextCompat.startForegroundService(this, i);
    }

    private Tor.Listener mTorListener = () -> {
        runOnUiThread(() -> {
            if (!mClient.isBusy()) {
                sendPendingAndUpdate();
            }
        });
    };


    void log(String s) {
        Log.i("Chat", s);
    }


    private void showHideReplyView(Message message) {
        if (message == null) {
            txtReplyText.setText(null);
            imvwReply.setVisibility(View.GONE);
            rlReply.setVisibility(View.GONE);
            mQuotedMessage = null;
            return;
        }

        mQuotedMessage = message;

        rlReply.setVisibility(View.VISIBLE);
        txtReplyText.setText(mQuotedMessage.getContent());
        imvwReply.setVisibility(View.VISIBLE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) findViewById(R.id.bar).getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_TOP, R.id.imvwImage);
        params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.imvwImage);
        findViewById(R.id.bar).setLayoutParams(params);
        if (mQuotedMessage.getType() == Message.TYPE_IMAGE || mQuotedMessage.getType() == Message.TYPE_VIDEO) {
            String filePath = mChatAdapter.getFilePath(mQuotedMessage);
            if (mQuotedMessage.getFileShare().isDownloaded()) {
                Glide.with(this).load(Uri.fromFile(new File(filePath))).into(imvwReply);
            } else {
                byte[] data = mQuotedMessage.getFileShare().getThumbnail();
                imvwReply.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
            }
            imvwReply.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imvwReply.setColorFilter(null);
        } else if (mQuotedMessage.getType() == Message.TYPE_FILE || mQuotedMessage.getType() == Message.TYPE_AUDIO) {
            String content = mQuotedMessage.getContent();
            if (content != null && !content.isEmpty()) {
                txtReplyText.setText(mQuotedMessage.getContent());
            } else {
                txtReplyText.setText(mQuotedMessage.getFileShare().getFilename());
            }

            if (mQuotedMessage.getType() == Message.TYPE_FILE) {
                imvwReply.setImageResource(Util.getResourceForFileType(mChatAdapter.getFilePath(mQuotedMessage)));
                imvwReply.setColorFilter(null);
            } else {
                imvwReply.setImageResource(R.drawable.ic_play_circle_svg);
                imvwReply.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent), PorterDuff.Mode.SRC_ATOP);
            }

            imvwReply.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
            params.addRule(RelativeLayout.ALIGN_TOP, R.id.txtReplyText);
            params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.txtReplyText);
            findViewById(R.id.bar).setLayoutParams(params);
            imvwReply.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        mContact.setPending(0);
        realm.commitTransaction();
        realm.close();
        Notifier.getInstance(this).onPauseActivity();
        timer.cancel();
        timer.purge();
        Server.getInstance(this).setFileDownloadListener(null);
        mTor.addListener(mTorListener);
        mClient.setStatusListener(null);

        Settings.puString(this, mContact.getAddress() + "_quote", mQuotedMessage != null ? mQuotedMessage.getPrimaryKey() : null);
        String message = ((EditText) findViewById(R.id.txtMessage)).getText().toString();
        Settings.puString(this, mContact.getAddress() + "_message", (!message.isEmpty()) ? message : null);

        super.onPause();
    }

    private Server.FileDownloadListener mFileDownloadListener = new Server.FileDownloadListener() {
        @Override
        public void onDownloadProgressChange(String messageId) {
            if (mIdPositionMap.containsKey(messageId)) {
                Log.d(TAG, "notifyProgress: Notifying for the progress");
                mChatAdapter.notifyItemChanged(mIdPositionMap.get(messageId));
            }
        }
    };

    public void scrollToQuotedMessage(Message message) {
        if (message == null) {
            Toast.makeText(this, R.string.quoted_message_not_present, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIdPositionMap.containsKey(message.getPrimaryKey())) {
            log("Scrolling to quoted message");
            int position = mIdPositionMap.get(message.getPrimaryKey());
            mQuotedMessageRequested = position;
            RecyclerView.ViewHolder viewHolder = mRVChat.findViewHolderForAdapterPosition(mQuotedMessageRequested);
            if (viewHolder != null) {
                ChatHolderText chatHolderText = (ChatHolderText) viewHolder;
                forceRippleAnimation(chatHolderText.llMainView);
                log("Triggered touch event for view");
            } else {
                mChatAdapter.showRippleEffectForPosition = position;
            }
            mRVChat.scrollToPosition(position);
            mLayoutManager.findFirstVisibleItemPosition();
            if (position < mLayoutManager.findFirstVisibleItemPosition()) {
                ((FloatingActionButton) findViewById(R.id.fab)).show();
            }
        }
    }

    public void forceRippleAnimation(View view) {
        Drawable background = view.getBackground();
        if (background instanceof RippleDrawable) {
            final RippleDrawable rippleDrawable = (RippleDrawable) background;

            rippleDrawable.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});

            Handler handler = new Handler();

            handler.postDelayed(() -> rippleDrawable.setState(new int[]{}), 1000);
        }
    }

    private void sendMessage(String sender, String receiver, String message, String filePath, String mimeType, int type, byte[] thumbnail) {
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

    class SwipeController extends ItemTouchHelper.Callback {

        private boolean swipeBack = false;
        private static final float actionThreshold = 300;

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            Message message = ((ChatHolderText) viewHolder).message;

            int swipeFlags = ((mChatAdapter.isTx(message) && message.getPending() == 0) || !mChatAdapter.isTx(message)) ? RIGHT : 0;

            return makeMovementFlags(0, swipeFlags);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void onChildDraw(Canvas c,
                                RecyclerView recyclerView,
                                RecyclerView.ViewHolder viewHolder,
                                float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {

            if (actionState == ACTION_STATE_SWIPE) {
                setTouchListener(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        private void setTouchListener(Canvas c,
                                      RecyclerView recyclerView,
                                      RecyclerView.ViewHolder viewHolder,
                                      float dX, float dY,
                                      int actionState, boolean isCurrentlyActive) {

            recyclerView.setOnTouchListener((v, event) -> {
                swipeBack = event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP;

                if (dX > actionThreshold) {
                    if (viewHolder != null) {
                        ChatHolderText holder = (ChatHolderText) viewHolder;
                        showHideReplyView(holder.message);
                    } else {
                        showHideReplyView(null);
                    }
                }
                return false;
            });
        }

        @Override
        public int convertToAbsoluteDirection(int flags, int layoutDirection) {
            if (swipeBack) {
                swipeBack = false;
                return 0;
            }
            return super.convertToAbsoluteDirection(flags, layoutDirection);
        }
    }
}
