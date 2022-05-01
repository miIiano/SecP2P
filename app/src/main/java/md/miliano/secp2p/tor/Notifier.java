package md.miliano.secp2p.tor;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import java.io.File;

import md.miliano.secp2p.MainActivity;
import md.miliano.secp2p.R;
import md.miliano.secp2p.RequestActivity;
import md.miliano.secp2p.db.Database;
import md.miliano.secp2p.utils.Settings;
import md.miliano.secp2p.utils.Util;

public class Notifier {

    private static Notifier instance;
    private final Context context;
    private int activities = 0;

    private static final String MESSAGE_CHANNEL_ID = "SecP2P_Notification_Channel";
    private static final String CHANNEL_REQ_NAME = "SecP2P Request";
    private static final String CHANNEL_MSG_NAME = "SecP2P Message";

    private Notifier(Context context) {
        context = context.getApplicationContext();
        this.context = context;
    }

    public static Notifier getInstance(Context context) {
        context = context.getApplicationContext();
        if (instance == null) {
            instance = new Notifier(context);
        }
        return instance;
    }

    private void log(String s) {
        Log.i("Notifier", s);
    }

    public synchronized void onMessage() {
        log("onMessage");
        if (activities <= 0) {
            Database.getInstance(context).addNotification();
            update();
        } else {
            if (Settings.getPrefs(context).getBoolean("sound", true)) {
                try {
                    File toneDir = new File(context.getFilesDir(), "tones");
                    if (!toneDir.exists()) toneDir.mkdir();
                    File toneFile = new File(toneDir, "tone.ogg");
                    Uri uri = Uri.fromFile(toneFile);
                    if (!toneFile.exists())
                        Util.copyAsset(context, "tones/tone.ogg", toneFile.getAbsolutePath());
                    Ringtone r = RingtoneManager.getRingtone(context, uri);
                    r.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized void onResumeActivity() {
        Database.getInstance(context).clearNotifications();
        activities++;
        update();
    }

    public synchronized void onPauseActivity() {
        activities--;
    }

    private void update() {
        log("update");
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int messageId = 5;
        int requestId = 6;
        int messages = Database.getInstance(context).getNotifications();
        if (messages <= 0 || !Settings.getPrefs(context).getBoolean("notify", true)) {
            log("cancel");
            notificationManager.cancel(messageId);
            notificationManager.cancel(requestId);
        } else {
            log("notify");
            showNotification(context,
                    context.getResources().getString(R.string.app_name),
                    context.getResources().getQuantityString(R.plurals.notification_new_messages, messages, messages),
                    new Intent(context, MainActivity.class));
        }
    }

    public void showNotification(Context context, String title, String body, Intent intent) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = 5;
        int importance = NotificationManager.IMPORTANCE_HIGH;

        String notificationTone = Settings.getPrefs(context).getString("ringtone", "DEFAULT_SOUND");

        log("Notification tone : " + notificationTone);

        NotificationChannel mChannel = new NotificationChannel(
                MESSAGE_CHANNEL_ID, CHANNEL_MSG_NAME, importance);

        if (Settings.getPrefs(context).getBoolean("sound", true)) {
            AudioAttributes att = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mChannel.setSound(Uri.parse(notificationTone), att);
        }
        notificationManager.createNotificationChannel(mChannel);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentTitle(title)
                .setContentText(body);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        mBuilder.setContentIntent(resultPendingIntent);

        notificationManager.notify(notificationId, mBuilder.build());
    }

    public void showRequestNotification(String sender, String description) {
        Intent intent = new Intent(context, RequestActivity.class);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = 6;

        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel mChannel = new NotificationChannel(MESSAGE_CHANNEL_ID, CHANNEL_REQ_NAME, importance);
        mChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
        notificationManager.createNotificationChannel(mChannel);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentTitle(sender + " sent you a friend request")

                .setContentText(description);

        if (Settings.getPrefs(context).getBoolean("sound", true)) {
            mBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND);
            mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        mBuilder.setContentIntent(resultPendingIntent);

        notificationManager.notify(notificationId, mBuilder.build());
    }
}
