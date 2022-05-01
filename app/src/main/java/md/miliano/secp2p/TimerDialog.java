package md.miliano.secp2p;

import android.app.Dialog;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class TimerDialog extends DialogFragment {

    private static final String TIME = "sec";

    private TextView mTime;
    private TimerTask mTimerTask;
    private long mSeconds = 0;

    private String path;
    private MediaRecorder mAudioRecord;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setTitle("Recording audio");
        alert.setPositiveButton("Share", (dialogInterface, i) -> {
            stopRecord();
            ((ChatActivity) getActivity()).onAudioRecordComplete(path);
        });
        alert.setNegativeButton("Cancel", null);


        View view = View.inflate(getActivity(), R.layout.timer_dialog, null);
        mTime = view.findViewById(R.id.time);

        alert.setView(view);
        try {
            startRecord();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Unable to record", Toast.LENGTH_SHORT).show();
        }
        return alert.create();
    }


    private void startRecord() throws IllegalStateException, IOException {
        mAudioRecord = new MediaRecorder();
        mAudioRecord.setAudioSource(MediaRecorder.AudioSource.MIC);
        mAudioRecord.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mAudioRecord.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        File file = new File(getActivity().getFilesDir(), System.currentTimeMillis() + ".amr");
        path = file.getAbsolutePath();
        mAudioRecord.setOutputFile(path);
        mAudioRecord.prepare();
        mAudioRecord.start();
    }

    private void stopRecord() {
        mAudioRecord.stop();
        mAudioRecord.release();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mSeconds = savedInstanceState.getLong(TIME);
            mTime.setText(String.valueOf(mSeconds));
        }
        startTimer();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimer();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(TIME, mSeconds);
    }

    private void startTimer() {
        Timer t = new Timer();

        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                mSeconds++;
                getActivity().runOnUiThread(() -> mTime.setText(DateUtils.formatElapsedTime(mSeconds)));
            }
        };

        t.scheduleAtFixedRate(mTimerTask, 1000, 1000);
    }

    private void stopTimer() {
        mTimerTask.cancel();
    }
}