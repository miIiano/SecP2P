package md.miliano.secp2p.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.novoda.merlin.Merlin;
import com.novoda.merlin.MerlinsBeard;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import md.miliano.secp2p.BuildConfig;
import md.miliano.secp2p.MainActivity;
import md.miliano.secp2p.R;
import md.miliano.secp2p.tor.Client;
import md.miliano.secp2p.tor.Server;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.utils.Util;

public class SecP2PHostService extends Service {

    private final String TAG = "SecP2PHostService";
    private Timer mTimer;
    private Client mClient;
    private Server mServer;
    private Tor mTor;
    private PowerManager.WakeLock wakeLock;
    private static final String CHANNEL_ID = "SecP2P_SERVICE";
    public static final int ID_SERVICE = 101;

    private Merlin mMerlin;

    public SecP2PHostService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mMerlin = new Merlin.Builder().withConnectableCallbacks().withAllCallbacks().build(this);
        mMerlin.bind();

        mMerlin.registerConnectable(() -> {
            mTor.start();
            update();
        });

        mMerlin.registerDisconnectable(() -> {
            mTor.stop();
            update();
        });

        mServer = Server.getInstance(this);
        mServer.setServiceRegisterListener(mSrl);
        mTor = Tor.getInstance(this);
        mTor.start();
        mClient = Client.getInstance(this);
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                Log.d(TAG, "update");
                mClient.doSendPendingFriends();
                mClient.doSendAllPendingMessages();
            }
        }, 0, 1000 * 60 * 10);

        PowerManager pMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecP2P:WakeLock");
        wakeLock.acquire(10*60*1000L);
        startForeground(getString(R.string.starting_tor_), -1);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent!=null && intent.getAction()!=null)?intent.getAction():Util.FGSAction.START.name();
        if(BuildConfig.DEBUG){
            Toast.makeText(this, "SecP2PHostService -> action : " + action.toUpperCase(Locale.ROOT), Toast.LENGTH_SHORT).show();
        }
        if (action.equals(Util.FGSAction.STOP.name())) {
            stopForeground(true);
            stopSelf();
        } else if (action.equals(Util.FGSAction.START.name())) {
            Server.getInstance(this);
            Tor.getInstance(this);
            Client.getInstance(this);
            Tor tor = Tor.getInstance(this);
            tor.addLogListener(mTorLogListener);
        }
        return START_STICKY;
    }

    private final Tor.LogListener mTorLogListener = () -> update();

    private final Server.ServiceRegisterListener mSrl = registered -> {
        if (registered) {
            startForeground(getString(R.string.id_registered), 100);
        } else {
            startForeground(getString(R.string.registering_secp2p_id), -1);
        }
    };

    public void update() {
        Tor tor = Tor.getInstance(this);

        String status = tor.getStatus();
        int i = status.indexOf(']');
        if (i >= 0) status = status.substring(i + 1);
        status = status.trim();

        String prefix = "Bootstrapped";
        if (status.contains("%") && status.length() > prefix.length() && status.startsWith(prefix)) {
            status = status.substring(prefix.length());
            status = status.trim();
        } else {
            status = getString(R.string.starting_);
        }

        MerlinsBeard mb = new MerlinsBeard.Builder().build(this);
        if (mb.isConnected()) {
            if (tor.isReady()) {
                if (mServer.isServiceRegistered()) {
                    status = getString(R.string.id_registered);
                } else {
                    if (mServer.isCheckServiceRegisteredRunning()) {
                        status = getString(R.string.registering_secp2p_id);
                    } else {
                        status = getString(R.string.tor_connected);
                    }
                }
            }
        } else {
            status = getString(R.string.internet_not_available);
        }

        String percentage = status.replace("\\b(?<!\\.)(?!0+(?:\\.0+)?%)(?:\\d|[1-9]\\d|100)(?:(?<!100)\\.\\d+)?%.*$", "");
        if (percentage.contains("%")) {
            Integer progress = Integer.parseInt(percentage.split("%")[0]);
            Log.d(TAG, "update: " + progress);
            startForeground(status, progress);
        } else {
            startForeground(status, -1);
        }
    }

    private void startForeground(String text, int progress) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = createNotificationChannel(notificationManager);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);

        Intent notificationIntent = new Intent(this, MainActivity.class);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent intent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        if (progress > -1 && progress < 99) {
            notificationBuilder.setProgress(100, progress, false);
        } else if (progress < 0 && text.contains("Registering")) {
            notificationBuilder.setProgress(100, progress, true);
        }
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText(text)
                .setSound(null)
                .setContentTitle(getString(R.string.connection_status))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(intent)
                .build();

        startForeground(ID_SERVICE, notification);
    }


    private String createNotificationChannel(NotificationManager notificationManager) {

        String channelName = "SecP2P Service";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setImportance(NotificationManager.IMPORTANCE_NONE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return CHANNEL_ID;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        mTimer.cancel();
        mTimer.purge();

        if (mMerlin != null) mMerlin.unbind();

        mServer.setServiceRegisterListener(null);
        mTor.removeLogListener(mTorLogListener);

        if (mServer != null) mServer.stopFileServer();

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        super.onDestroy();
    }
}
