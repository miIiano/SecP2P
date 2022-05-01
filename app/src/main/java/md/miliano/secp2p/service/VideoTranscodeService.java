package md.miliano.secp2p.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.daasuu.mp4compose.FillMode;
import com.daasuu.mp4compose.composer.Mp4Composer;

import java.io.File;
import java.io.IOException;

import md.miliano.secp2p.R;
import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.tor.Client;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.utils.Util;

public class VideoTranscodeService extends IntentService {

    public static final int VIDEO_WIDTH = 480;
    public static final int VIDEO_HEIGHT = 360;

    private static final String EXTRA_SRC_FILE = "md.miliano.secp2p.service.extra.SRC_FILE";
    private static final String EXTRA_DEST_FILE = "md.miliano.secp2p.service.extra.DEST_FILE";
    private static final String EXTRA_RECEIVER = "md.miliano.secp2p.service.extra.RECEIVER";
    private static final String EXTRA_MESSAGE = "md.miliano.secp2p.service.extra.MESSAGE";
    private static final String EXTRA_MIME_TYPE = "md.miliano.secp2p.service.extra.MIME_TYPE";
    private static final String EXTRA_ATTACH_FILE_TYPE = "md.miliano.secp2p.service.extra.ATTACH_FILE_TYPE";
    private static final String EXTRA_THUMB_FILE_PATH = "md.miliano.secp2p.service.extra.THUMB_FILE_PATH";
    private static final String CHANNEL_ID = "SecP2P_video_transcode";
    private static final int NOTIFICATION_ID = 9;

    public VideoTranscodeService() {
        super("VideoTranscodeService");
    }

    public static void startVideoTranscode(Context context, String srcFile, String desFile,
                                           String[] receiver,
                                           String message,
                                           String mimeType,
                                           int fileType,
                                           String thumbFilePath) {
        Intent intent = new Intent(context, VideoTranscodeService.class);
        intent.putExtra(EXTRA_SRC_FILE, srcFile);
        intent.putExtra(EXTRA_DEST_FILE, desFile);
        intent.putExtra(EXTRA_RECEIVER, receiver);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_MIME_TYPE, mimeType);
        intent.putExtra(EXTRA_ATTACH_FILE_TYPE, fileType);
        intent.putExtra(EXTRA_THUMB_FILE_PATH, thumbFilePath);
        context.startService(intent);
    }

    private int getInt(String key, Intent intent) {
        Bundle extra = intent.getExtras();
        if (extra != null && extra.containsKey(key)) {
            return extra.getInt(key);
        }
        return -1;
    }

    private String getString(String key, Intent intent) {
        Bundle extra = intent.getExtras();
        if (extra != null && extra.containsKey(key)) {
            return extra.getString(key);
        }
        return null;
    }

    private String[] getStringArray(String key, Intent intent) {
        Bundle extra = intent.getExtras();
        if (extra != null && extra.containsKey(key)) {
            return extra.getStringArray(key);
        }
        return null;
    }

    public boolean isTranscodeNecessary(String file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(file);
        int bitrate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));

        log("Bitrate of the file: " + bitrate);

        return bitrate > (VIDEO_WIDTH * VIDEO_HEIGHT * 30 * 0.15);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            String[] receivers = getStringArray(EXTRA_RECEIVER, intent);
            String message = getString(EXTRA_MESSAGE, intent);
            String mimeType = getString(EXTRA_MIME_TYPE, intent);
            int attachFileType = getInt(EXTRA_ATTACH_FILE_TYPE, intent);
            String thumbnailFilePath = getString(EXTRA_THUMB_FILE_PATH, intent);

            final String srcMp4Path = intent.getStringExtra(EXTRA_SRC_FILE);
            final String destMp4Path = intent.getStringExtra(EXTRA_DEST_FILE);

            if (isTranscodeNecessary(srcMp4Path)) {
                new Mp4Composer(srcMp4Path, destMp4Path)
                        .size(VIDEO_WIDTH, VIDEO_HEIGHT)
                        .fillMode(FillMode.PRESERVE_ASPECT_FIT)
                        .listener(new Mp4Composer.Listener() {
                            @Override
                            public void onProgress(double progress) {
                                log("onProgress = " + progress);
                                showNotification("Video Compression", (int) (progress * 100));
                            }

                            @Override
                            public void onCompleted() {
                                log("onCompleted()");
                                cancelNotification();

                                try {
                                    byte[] thumbnail = Util.readSmallFile(getApplicationContext(), thumbnailFilePath);
                                    for (String r : receivers) {
                                        sendMessage(r, message, destMp4Path, mimeType, attachFileType, thumbnail);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onCanceled() {
                                log("onCanceled");
                                cancelNotification();
                            }

                            @Override
                            public void onFailed(Exception exception) {
                                exception.printStackTrace();
                                log("onFailed()" + exception.getMessage());
                                cancelNotification();
                            }
                        })
                        .start();
            } else {
                try {
                    byte[] thumbnail = Util.readSmallFile(getApplicationContext(), thumbnailFilePath);
                    for (String r : receivers) {
                        sendMessage(r, message, srcMp4Path, mimeType, attachFileType, thumbnail);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void showNotification(String title, int progress) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = createNotificationChannel(notificationManager);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);

        Notification notification = notificationBuilder
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_video)
                .setContentText(title)
                .setContentTitle("SecP2P Vide Encoding")
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, progress <= 0)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }


    private String createNotificationChannel(NotificationManager notificationManager) {
        String channelName = "Transcode Video";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return CHANNEL_ID;
    }

    private void sendMessage(String receiver, String message, String filePath, String mimeType, int type, byte[] thumbnail) {
        String sender = Tor.getInstance(this).getID();
        Message.addPendingOutgoingMessage(
                sender,
                receiver,
                message,
                new File(filePath).getName(),
                filePath,
                mimeType,
                type,
                thumbnail);
        Client.getInstance(this).startSendPendingMessages(receiver);
    }

    private void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void log(String s) {
        Log.d("VideoTranscodeService", s);
    }
}
