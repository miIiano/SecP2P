package md.miliano.secp2p;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import md.miliano.secp2p.utils.Settings;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean use_dark_mode = Settings.getPrefs(this).getBoolean("use_dark_mode", false);

        if (use_dark_mode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        setContentView(R.layout.activity_start_actvity);

        boolean startSetupCompleted = Settings.getPrefs(getApplication()).getBoolean("start_setup_completed", false);
        if (startSetupCompleted) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    public void onStartNewClicked(View v) {
        startActivity(new Intent(this, CreateNewActivity.class));
    }

    public void onImportIdClicked(View v) {
        startActivity(new Intent(this, ImportIdActivity.class));
    }
}
