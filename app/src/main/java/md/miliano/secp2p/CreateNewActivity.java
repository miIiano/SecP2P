package md.miliano.secp2p;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;

import md.miliano.secp2p.db.Database;
import md.miliano.secp2p.utils.Settings;


public class CreateNewActivity extends AppCompatActivity {

    private static final String TAG = "CreateNewActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ((RadioGroup) findViewById(R.id.radioGroup)).setOnCheckedChangeListener((radioGroup, i) -> {

            switch (i) {
                case R.id.rdbtnLightTheme:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    Settings.putBoolean(getApplicationContext(), "use_dark_mode", false);
                    break;
                case R.id.rdbtnDarkTheme:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    Settings.putBoolean(getApplicationContext(), "use_dark_mode", true);
                    break;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public void onStartClicked(View v) {

        EditText txtName = findViewById(R.id.txtName);
        if (txtName.length() < 1) {
            txtName.setError("Please enter name");
            return;
        }
        Database.getInstance(this).setName(txtName.getText().toString().trim());
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        Settings.putBoolean(getApplicationContext(), "start_setup_completed", true);
    }

    public void onDpBoxClicked(View v) {
        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON)
                .setOutputUri(Uri.fromFile(new File(getFilesDir(), "dp.jpg")))
                .setFixAspectRatio(true)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri resultUri = result.getUri();
                Glide.with(this)
                        .load(resultUri)
                        .apply(RequestOptions.circleCropTransform())
                        .into(((ImageView) findViewById(R.id.imvwDp)));
                Database.getInstance(this).put("dp", resultUri.getPath());
                Log.d(TAG, "onActivityResult: " + resultUri.getPath());
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
            }
        }
    }
}
