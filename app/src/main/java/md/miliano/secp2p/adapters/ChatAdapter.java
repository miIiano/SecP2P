package md.miliano.secp2p.adapters;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.github.abdularis.buttonprogress.DownloadButtonProgress;
import com.keenfin.audioview.AudioView2;
import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloader;
import com.stfalcon.imageviewer.StfalconImageViewer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;

import io.realm.OrderedRealmCollection;
import io.realm.Realm;
import io.realm.RealmRecyclerViewAdapter;
import io.realm.RealmResults;
import md.miliano.secp2p.ChatActivity;
import md.miliano.secp2p.R;
import md.miliano.secp2p.VideoViewActivity;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.tor.FileServer;
import md.miliano.secp2p.tor.Server;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.utils.TimeAgo;
import md.miliano.secp2p.utils.Util;

public class ChatAdapter extends RealmRecyclerViewAdapter<Message, ChatHolderText> {

    private static final String TAG = ChatAdapter.class.getName();
    private ChatActivity mChatActivity;
    private Server mServer;
    private File mMediaFolder;
    private String mSearchText;

    public int showRippleEffectForPosition = -1;

    public ChatAdapter(@Nullable OrderedRealmCollection<Message> data, ChatActivity context, Contact contact) {
        super(data, true);

        mChatActivity = context;
        mServer = Server.getInstance(mChatActivity);
        mMediaFolder = new File(context.getFilesDir(), contact.getAddress());
        setHasStableIds(true);
    }


    @Override
    public long getItemId(int position) {
        return getData().get(position).getStableId();
    }

    public void setSearchText(String text) {
        mSearchText = text;
    }


    @Override
    public ChatHolderText onCreateViewHolder(ViewGroup parent, int viewType) {
        ChatHolderText viewHolder;
        if (viewType == Message.TYPE_TEXT) {
            viewHolder = new ChatHolderText(LayoutInflater.from(mChatActivity)
                    .inflate(R.layout.item_message, parent, false));
        } else if (viewType == Message.TYPE_IMAGE || viewType == Message.TYPE_VIDEO) {
            viewHolder = new ChatHolderMedia(LayoutInflater.from(mChatActivity)
                    .inflate(R.layout.item_message_media, parent, false));
        } else if (viewType == Message.TYPE_FILE) {
            viewHolder = new ChatHolderFile(LayoutInflater.from(mChatActivity)
                    .inflate(R.layout.item_message_file, parent, false));
        } else {
            viewHolder = new ChatHolderAudio(LayoutInflater.from(mChatActivity)
                    .inflate(R.layout.item_message_audio, parent, false));
        }

        if (viewHolder instanceof ChatHolderMedia) {
            ChatHolderMedia viewHolderMedia = (ChatHolderMedia) viewHolder;
            viewHolderMedia.imvwImage.setOnClickListener(view -> viewFile(viewHolderMedia));

            if (viewType == Message.TYPE_VIDEO) {
                viewHolderMedia.imvwPlayButton.setOnClickListener(view -> viewFile(viewHolderMedia));
            }

            viewHolderMedia.progressBar.addOnClickListener(new DownloadButtonProgress.OnClickListener() {
                @Override
                public void onIdleButtonClick(View view) {
                    mServer.downloadFile(viewHolder.message);
                    viewHolderMedia.progressBar.setIndeterminate();
                }

                @Override
                public void onCancelButtonClick(View view) {
                    if (mServer.mDownloadTasks.containsKey(viewHolder.message.getPrimaryKey())) {
                        BaseDownloadTask bdt = mServer.mDownloadTasks.get(viewHolder.message.getPrimaryKey());
                        FileDownloader.getImpl().pause(bdt.getId());
                    }
                    viewHolderMedia.progressBar.setIdle();
                }

                @Override
                public void onFinishButtonClick(View view) {
                    viewHolderMedia.progressBar.setVisibility(View.GONE);
                }
            });
        }

        if (viewHolder instanceof ChatHolderAudio) {
            ChatHolderAudio viewHolderAudio = (ChatHolderAudio) viewHolder;
            viewHolderAudio.progressBar.addOnClickListener(new DownloadButtonProgress.OnClickListener() {
                @Override
                public void onIdleButtonClick(View view) {
                    mServer.downloadFile(viewHolder.message);
                    viewHolderAudio.progressBar.setIndeterminate();
                }

                @Override
                public void onCancelButtonClick(View view) {
                    if (mServer.mDownloadTasks.containsKey(viewHolder.message.getPrimaryKey())) {
                        BaseDownloadTask bdt = mServer.mDownloadTasks.get(viewHolder.message.getPrimaryKey());
                        FileDownloader.getImpl().pause(bdt.getId());
                    }
                    viewHolderAudio.progressBar.setIdle();
                }

                @Override
                public void onFinishButtonClick(View view) {
                    viewHolderAudio.progressBar.setVisibility(View.GONE);
                }
            });
        }

        if (viewType == Message.TYPE_TEXT) {
            viewHolder.rlReply.setOnClickListener(view -> moveToQuotedMessage(viewHolder));
        }

        viewHolder.card.setOnLongClickListener(view -> {
            performLongClick(viewHolder, viewType);
            return true;
        });

        viewHolder.content.setOnLongClickListener(view -> {
            performLongClick(viewHolder, viewType);
            return true;
        });

        if (viewHolder instanceof ChatHolderMedia) {
            ((ChatHolderMedia) viewHolder).imvwImage.setOnLongClickListener(view -> {
                performLongClick(viewHolder, viewType);
                return true;
            });
        }
        return viewHolder;
    }

    private void moveToQuotedMessage(ChatHolderText chatHolderText) {
        mChatActivity.scrollToQuotedMessage(chatHolderText.quotedMessage);
    }

    private void viewImage(Message message) {
        RealmResults<Message> all = getData().where().
                equalTo("type", Message.TYPE_IMAGE)
                .or()
                .equalTo("type", Message.TYPE_VIDEO)
                .findAll();
        int index = all.indexOf(message);
        View view = LayoutInflater.from(mChatActivity).inflate(R.layout.imageview_overlay, null);

        ImageView imvwPlay = view.findViewById(R.id.imvwPlay);
        imvwPlay.setOnClickListener(v -> viewVideoFile(message));
        imvwPlay.setVisibility(message.getType() == Message.TYPE_VIDEO ? View.VISIBLE : View.GONE);
        ImageView imvwShare = view.findViewById(R.id.imvwShare);
        imvwShare.setOnClickListener(v -> doShare(message));

        StfalconImageViewer<Message> images = new StfalconImageViewer.Builder<>(mChatActivity, all, (imageView, m) -> loadIntoImageView(m, imageView))
                .withOverlayView(view)
                .withStartPosition(index)
                .allowZooming(true)
                .withImageChangeListener(position -> {
                    Message m = all.get(position);
                    view.findViewById(R.id.imvwPlay).setOnClickListener(v -> viewVideoFile(m));
                    view.findViewById(R.id.imvwPlay).setVisibility(m.getType() == Message.TYPE_VIDEO ? View.VISIBLE : View.GONE);
                    view.findViewById(R.id.imvwShare).setOnClickListener(v -> doShare(m));
                })
                .build();
        images.show(true);
    }

    private void viewVideoFile(Message message) {
        Intent intent = new Intent(mChatActivity, VideoViewActivity.class);
        intent.putExtra(VideoViewActivity.EXTRA_VIDEO_PATH, getFilePath(message));
        mChatActivity.startActivity(intent);
    }

    private void doShare(Message message) {
        File file = new File(getFilePath(message));
        Uri path = FileProvider.getUriForFile(mChatActivity,
                mChatActivity.getApplicationContext().getPackageName() + ".provider",
                file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, path);
        shareIntent.setType(message.getFileShare().getMimeType());
        mChatActivity.startActivity(Intent.createChooser(shareIntent,
                mChatActivity.getString(R.string.send_to)));
    }

    private void viewFile(ChatHolderMedia viewHolder) {
        Log.d(TAG, "Viewing file " + viewHolder.message.getFileShare().getFilePath());
        File file = new File(getFilePath(viewHolder.message));
        if (file.exists()) {
            if (viewHolder.message.getType() == Message.TYPE_IMAGE) {
                viewImage(viewHolder.message);
            } else if (viewHolder.message.getType() == Message.TYPE_VIDEO) {
                viewVideoFile(viewHolder.message);
            } else {
                String mimeType = FileServer.getMimeType(viewHolder.message.getFileShare().getFilePath());
                if (mimeType == null)
                    mimeType = viewHolder.message.getFileShare().getMimeType();
                Log.d(TAG, "Viewing filetype " + mimeType);
                Uri path = FileProvider.getUriForFile(mChatActivity,
                        mChatActivity.getApplicationContext().getPackageName() + ".provider",
                        file);
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                openIntent.setDataAndType(path, mimeType);
                try {
                    mChatActivity.startActivity(openIntent);
                } catch (ActivityNotFoundException e) {
                }
            }
        } else {
            Toast.makeText(mChatActivity, "File not present", Toast.LENGTH_SHORT).show();
        }
    }

    private void performLongClick(ChatHolderText viewHolder, int viewType) {
        PopupMenu popupMenu = new PopupMenu(mChatActivity, viewHolder.card);
        popupMenu.getMenuInflater().inflate(R.menu.item_chat_menu, popupMenu.getMenu());
        popupMenu.getMenu().findItem(R.id.action_save_to).setVisible(
                viewType != Message.TYPE_TEXT && !isTx(viewHolder.message));
        popupMenu.getMenu().findItem(R.id.action_copy_text).setVisible(viewType == Message.TYPE_TEXT);
        popupMenu.getMenu().findItem(R.id.action_forward).setVisible(false);
        popupMenu.getMenu().findItem(R.id.action_share_with).setVisible(viewType != Message.TYPE_TEXT);
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_copy_text:
                    Util.setClipboard(mChatActivity, viewHolder.message.getContent());
                    Toast.makeText(mChatActivity, "Message text copied", Toast.LENGTH_SHORT).show();
                    break;
                case R.id.action_forward:
                    break;
                case R.id.action_delete:
                    askAndDelete(viewHolder.message, viewHolder.position);
                    break;
                case R.id.action_save_to:
                    if (ContextCompat.checkSelfPermission(mChatActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(mChatActivity,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                1);
                        return false;
                    }
                    if (!Util.EXTERNAL_FOLDER.exists()) {
                        Util.EXTERNAL_FOLDER.mkdir();
                    }
                    File src = new File(mMediaFolder, viewHolder.message.getFileShare().getFilename());
                    String mimeType = viewHolder.message.getFileShare().getMimeType();
                    File dest = new File(Util.getFolder(mimeType), src.getName());
                    new CopyFile().execute(src.getAbsolutePath(), dest.getAbsolutePath());
                    break;
                case R.id.action_share_with:
                    File file = new File(getFilePath(viewHolder.message));
                    Uri path = FileProvider.getUriForFile(mChatActivity,
                            mChatActivity.getApplicationContext().getPackageName() + ".provider",
                            file);
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, path);
                    shareIntent.setType(viewHolder.message.getFileShare().getMimeType());
                    mChatActivity.startActivity(Intent.createChooser(shareIntent,
                            mChatActivity.getString(R.string.send_to)));
                    break;
            }
            return false;
        });
        try {
            Method method = popupMenu.getMenu().getClass().getDeclaredMethod("setOptionalIconsVisible", boolean.class);
            method.setAccessible(true);
            method.invoke(popupMenu.getMenu(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        popupMenu.show();
    }

    private void askAndDelete(Message message, int position) {
        if (mServer.mDownloadTasks.containsKey(position)) {
            BaseDownloadTask bdt = mServer.mDownloadTasks.get(position);
            FileDownloader.getImpl().pause(bdt.getId());
        }
        new AlertDialog.Builder(mChatActivity)
                .setTitle(R.string.delete_message)
                .setMessage(mChatActivity.getString(R.string.really_delete_message))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    Realm realm = Realm.getDefaultInstance();
                    realm.beginTransaction();
                    message.deleteFromRealm();
                    realm.commitTransaction();
                    realm.close();
                })
                .setNegativeButton(R.string.No, (dialog, which) -> {
                })
                .show();
    }

    @Override
    public int getItemViewType(int position) {
        Message m = getItem(position);

        return m.getType();
    }

    public boolean isTx(Message message) {
        String sender = message.getSender();
        return sender.equals(Tor.getInstance(mChatActivity).getID());
    }

    public String getFilePath(Message message) {
        if (isTx(message)) {
            return message.getFileShare().getFilePath();
        } else {
            return new File(mMediaFolder, message.getFileShare().getFilename()).getAbsolutePath();
        }
    }

    @Override
    public void onBindViewHolder(ChatHolderText holder, @SuppressLint("RecyclerView") int position) {
        final Message message = getItem(position);
        holder.message = message;
        holder.position = position;

        String content = message.getContent();
        String time = date(message.getTime());
        boolean pending = message.getPending() == 1;
        boolean tx = isTx(message);

        if (tx) {
            holder.llMainView.setGravity(Gravity.RIGHT);
            holder.left.setVisibility(View.VISIBLE);
            holder.right.setVisibility(View.GONE);
            holder.card.setCardBackgroundColor(ContextCompat.getColor(
                    mChatActivity, R.color.message_sent));
        } else {
            holder.llMainView.setGravity(Gravity.LEFT);
            holder.left.setVisibility(View.GONE);
            holder.right.setVisibility(View.VISIBLE);
            holder.card.setCardBackgroundColor(ContextCompat.getColor(
                    mChatActivity, R.color.message_received));
        }

        if (message.getType() == Message.TYPE_IMAGE
                || message.getType() == Message.TYPE_VIDEO
                || message.getType() == Message.TYPE_FILE) {
            ChatHolderMedia holderMedia = (ChatHolderMedia) holder;
            holderMedia.description.setText(Util.humanReadableByteCountBin(message.getFileShare().getFileSize()));
            if (tx) {
                holderMedia.progressBar.setVisibility(View.GONE);
                if (checkFileDownloaded(message)) {
                    if (message.getType() != Message.TYPE_FILE) {
                        loadIntoImageView(message, holderMedia.imvwImage);
                        holderMedia.imvwPlayButton.setVisibility(message.getType() == Message.TYPE_VIDEO ? View.VISIBLE : View.GONE);
                    }
                } else {
                    if (message.getFileShare().getThumbnail() != null) {
                        byte[] data = message.getFileShare().getThumbnail();
                        holderMedia.imvwImage.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
                    }
                }
            } else {
                if (checkFileDownloaded(message)) {
                    holderMedia.progressBar.setVisibility(View.GONE);
                    if (message.getType() != Message.TYPE_FILE) {
                        loadIntoImageView(message, holderMedia.imvwImage);
                        holderMedia.imvwPlayButton.setVisibility(message.getType() == Message.TYPE_VIDEO ? View.VISIBLE : View.GONE);
                    }
                } else {
                    String url = Message.getDownloadUrl(message);
                    holderMedia.progressBar.setVisibility(View.VISIBLE);
                    if (message.getType() != Message.TYPE_FILE) {
                        holderMedia.imvwPlayButton.setVisibility(View.GONE);
                    }
                    if (message.getFileShare().getThumbnail() != null) {
                        byte[] data = message.getFileShare().getThumbnail();
                        holderMedia.imvwImage.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
                    }
                    Log.d(TAG, "onBindViewHolder: Trying to load " + url);
                    if (mServer.mDownloadTasks.containsKey(message.getPrimaryKey())) {
                        holderMedia.progressBar.setIndeterminate();
                    } else {
                        holderMedia.progressBar.setIdle();
                    }
                    if (mServer.mDownloadProgress.containsKey(message.getPrimaryKey())) {
                        int progress = mServer.mDownloadProgress.get(message.getPrimaryKey());
                        if (progress > -1) {
                            holderMedia.progressBar.setDeterminate();
                            holderMedia.progressBar.setCurrentProgress(mServer.mDownloadProgress.get(message.getPrimaryKey()));
                        } else {
                            holderMedia.progressBar.setIndeterminate();
                        }
                    }
                }
            }
        } else if (message.getType() == Message.TYPE_AUDIO) {
            ChatHolderAudio holderAudio = (ChatHolderAudio) holder;
            holderAudio.description.setText(Util.humanReadableByteCountBin(message.getFileShare().getFileSize()));
            if (tx) {
                holderAudio.progressBar.setVisibility(View.GONE);
                holderAudio.audioView2.setVisibility(View.VISIBLE);
                Log.d(TAG, "onBindViewHolder: Trying to load " + getFilePath(message));
                holderAudio.audioView2.setTag(position);
                if (!holderAudio.audioView2.attached())
                    holderAudio.audioView2.setUpControls();
                try {
                    holderAudio.audioView2.setDataSource(message.getFileShare().getFilePath());
                } catch (IOException ignored) {
                }
            } else {
                File file = new File(getFilePath(message));
                if (checkFileDownloaded(message)) {
                    Log.d(TAG, "onBindViewHolder: Trying to load " + file.getAbsolutePath());
                    holderAudio.progressBar.setVisibility(View.GONE);
                    holderAudio.audioView2.setVisibility(View.VISIBLE);
                    holderAudio.audioView2.setTag(position);
                    if (!holderAudio.audioView2.attached())
                        holderAudio.audioView2.setUpControls();
                    try {
                        holderAudio.audioView2.setDataSource(file.getAbsolutePath());
                    } catch (IOException ignored) {
                    }
                } else {
                    holderAudio.progressBar.setVisibility(View.VISIBLE);
                    holderAudio.audioView2.setVisibility(View.GONE);
                    String url = Message.getDownloadUrl(message);
                    Log.d(TAG, "onBindViewHolder: Trying to load " + url);
                    if (!mServer.mDownloadTasks.containsKey(message.getPrimaryKey())) {
                        holderAudio.progressBar.setIdle();
                    } else {
                        holderAudio.progressBar.setIndeterminate();
                    }
                    if (mServer.mDownloadProgress.containsKey(message.getPrimaryKey())) {
                        int progress = mServer.mDownloadProgress.get(message.getPrimaryKey());
                        if (progress > -1) {
                            holderAudio.progressBar.setDeterminate();
                            holderAudio.progressBar.setCurrentProgress(mServer.mDownloadProgress.get(message.getPrimaryKey()));
                        } else {
                            holderAudio.progressBar.setIndeterminate();
                        }
                    }
                }
            }
        } else {
            if (message.getQuotedMessageId() != null) {
                String idField;
                if (isTx(message)) {
                    idField = "primaryKey";
                } else {
                    idField = message.getQuotedMessageSender().equals(Tor.getInstance(mChatActivity).getID())
                            ? "primaryKey"
                            : "remoteMessageId";
                }
                Message d = getData().where()
                        .equalTo(idField, message.getQuotedMessageId())
                        .equalTo("sender", message.getQuotedMessageSender())
                        .findFirst();

                holder.quotedMessage = d;
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.imvwBar.getLayoutParams();
                params.addRule(RelativeLayout.ALIGN_TOP, R.id.imvwImage);
                params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.imvwImage);
                params.height = (int) mChatActivity.getResources().getDimension(R.dimen.media_reply_max_width);
                holder.imvwBar.setLayoutParams(params);
                holder.rlReply.setVisibility(View.VISIBLE);
                if (d != null) {
                    holder.txtReplyText.setText(d.getContent());
                    if (d.getType() == Message.TYPE_IMAGE || d.getType() == Message.TYPE_VIDEO) {
                        holder.imvwReply.setVisibility(View.VISIBLE);
                        String filePath = getFilePath(d);
                        File file = new File(filePath);
                        if (file.exists()) {
                            loadIntoImageView(d, holder.imvwReply);
                        } else {
                            byte[] thumbnail = d.getFileShare().getThumbnail();
                            holder.imvwReply.setImageBitmap(BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length));
                        }
                        holder.imvwReply.setColorFilter(null);
                    } else if (d.getType() == Message.TYPE_FILE || d.getType() == Message.TYPE_AUDIO) {
                        String c = d.getContent();
                        if (c != null && !c.isEmpty()) {
                            holder.txtReplyText.setText(c);
                        } else {
                            holder.txtReplyText.setText(d.getFileShare().getFilename());
                        }
                        holder.imvwReply.setVisibility(View.VISIBLE);
                        if (d.getType() == Message.TYPE_FILE) {
                            holder.imvwReply.setImageResource(Util.getResourceForFileType(getFilePath(d)));
                            holder.imvwReply.setColorFilter(null);
                        } else {
                            holder.imvwReply.setImageResource(R.drawable.ic_play_circle);
                            holder.imvwReply.setColorFilter(
                                    ContextCompat.getColor(mChatActivity, R.color.colorAccent), PorterDuff.Mode.SRC_ATOP);
                        }
                    } else {
                        params.addRule(RelativeLayout.ALIGN_TOP, R.id.txtReplyText);
                        params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.txtReplyText);
                        holder.imvwBar.setLayoutParams(params);
                        holder.imvwReply.setVisibility(View.GONE);
                    }
                } else {
                    params.addRule(RelativeLayout.ALIGN_TOP, R.id.txtReplyText);
                    params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.txtReplyText);
                    holder.imvwBar.setLayoutParams(params);
                    holder.imvwReply.setVisibility(View.GONE);
                    holder.txtReplyText.setText(message.getQuotedMessageContent());
                }
            } else {
                holder.quotedMessage = null;
                holder.rlReply.setVisibility(View.GONE);
            }
        }

        if (pending)
            holder.card.setCardElevation(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, mChatActivity.getResources().getDisplayMetrics()));
        else
            holder.card.setCardElevation(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, mChatActivity.getResources().getDisplayMetrics()));

        if (!isTx(message)) {
            holder.status.setVisibility(View.GONE);
        } else {
            holder.status.setVisibility(View.VISIBLE);
            if (pending) {
                holder.status.setImageDrawable(ContextCompat.getDrawable(mChatActivity, R.drawable.ic_pending));
            } else {
                holder.status.setImageDrawable(ContextCompat.getDrawable(mChatActivity, R.drawable.ic_sent));
            }
        }

        if (message.getType() == Message.TYPE_TEXT) {
            holder.description.setVisibility(View.GONE);
        } else {
            holder.description.setVisibility(View.VISIBLE);
        }

        holder.content.setMovementMethod(LinkMovementMethod.getInstance());
        if (content != null && !content.isEmpty()) {
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setText(Util.linkify(mChatActivity, content));
        } else {
            holder.content.setVisibility(View.GONE);
        }
        if (mSearchText != null) {
            setHighLightedText(holder.content, mSearchText.toLowerCase(Locale.ROOT));
        }

        if (message.getType() == Message.TYPE_FILE) {
            holder.content.setVisibility(View.VISIBLE);
            Log.d(TAG, "onBindViewHolder: MimeType: " + message.getFileShare().getMimeType());
            holder.content.setText(message.getFileShare().getFilename());
            ((ChatHolderFile) holder).txtDescription.setText(message.getFileShare().getMimeType());
            ((ChatHolderFile) holder).imvwImage.setImageResource(Util.getResourceForFileType(getFilePath(message)));
        }

        if (message.getType() == Message.TYPE_AUDIO) {
            holder.content.setVisibility(View.GONE);
        }

        holder.time.setText(time);
        if (pending) {
            holder.abort.setVisibility(View.VISIBLE);
            holder.abort.setClickable(true);
            holder.abort.setOnClickListener(v -> {
                boolean ok = Message.abortOutgoingMessage(message.getPrimaryKey());
                Toast.makeText(mChatActivity, ok ? "Pending message aborted." : "Error: Message already sent.", Toast.LENGTH_SHORT).show();
            });
        } else {
            holder.abort.setVisibility(View.INVISIBLE);
            holder.abort.setClickable(false);
            holder.abort.setOnClickListener(null);
        }

        if (showRippleEffectForPosition > 0) {
            showRippleEffectForPosition = -1;
            new Handler().postDelayed(() -> mChatActivity.forceRippleAnimation(holder.llMainView), 200);
        }
    }

    private void loadIntoImageView(Message message, ImageView imageView) {
        String filePath = getFilePath(message);
        Log.d(TAG, "onBindViewHolder: Trying to load local file " + filePath);
        Glide.with(mChatActivity).load(Uri.fromFile(new File(filePath)))
                .placeholder(R.drawable.ic_photo)
                .into(imageView);
    }

    private boolean setHighLightedText(TextView tv, String textToHighlight) {
        boolean result = false;
        String tvt = tv.getText().toString().toLowerCase(Locale.ROOT);
        int ofe = tvt.indexOf(textToHighlight);
        Spannable wordToSpan = new SpannableString(tv.getText());
        for (int ofs = 0; ofs < tvt.length() && ofe != -1; ofs = ofe + 1) {
            ofe = tvt.indexOf(textToHighlight, ofs);
            if (ofe == -1)
                break;
            else {
                result = true;
                wordToSpan.setSpan(new BackgroundColorSpan(
                                ContextCompat.getColor(mChatActivity, R.color.colorAccent)),
                        ofe, ofe + textToHighlight.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(wordToSpan, TextView.BufferType.SPANNABLE);
            }
        }
        return result;
    }

    private boolean checkFileDownloaded(Message m) {
        String filePath = getFilePath(m);
        File file = new File(filePath);
        if (m.getFileShare().isDownloaded()) return true;
        return file.exists();
    }

    private String date(long time) {
        if (DateUtils.isToday(time)) {
            return TimeAgo.getTimeAgo(time, mChatActivity);
        } else {
            return DateUtils.formatDateTime(mChatActivity, time, DateUtils.FORMAT_ABBREV_ALL);
        }
    }

    private class CopyFile extends AsyncTask<String, Integer, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Toast.makeText(mChatActivity, "Copying file to external storage", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(String... strings) {
            String srcFile = strings[0];
            String destFile = strings[1];
            try {
                Util.copyFile(new File(srcFile), new File(destFile));
                return destFile;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String file) {
            super.onPostExecute(file);
            String message = "Unable to copy file";
            if (file != null) {
                message = "File copied successfully\n" + file;
            }
            Toast.makeText(mChatActivity, message, Toast.LENGTH_LONG).show();
        }
    }
}

class ChatHolderMedia extends ChatHolderText {
    public ImageView imvwImage;
    public DownloadButtonProgress progressBar;
    public ImageView imvwPlayButton;

    public ChatHolderMedia(View v) {
        super(v);
        imvwImage = v.findViewById(R.id.imvwImage);
        imvwPlayButton = v.findViewById(R.id.imvePlayButton);
        progressBar = v.findViewById(R.id.progressbar);
    }
}


class ChatHolderFile extends ChatHolderMedia {
    public TextView txtDescription;

    public ChatHolderFile(View v) {
        super(v);
        txtDescription = v.findViewById(R.id.txtDescription);
    }
}

class ChatHolderAudio extends ChatHolderText {
    public AudioView2 audioView2;
    public DownloadButtonProgress progressBar;

    public ChatHolderAudio(View v) {
        super(v);
        audioView2 = v.findViewById(R.id.audioview);
        progressBar = v.findViewById(R.id.progressbar);
    }
}

