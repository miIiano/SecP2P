package md.miliano.secp2p;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceFragment;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.util.Objects;

import md.miliano.secp2p.db.Database;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.utils.CreateZip;
import md.miliano.secp2p.utils.PasswordValidator;
import md.miliano.secp2p.utils.Settings;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Settings.getPrefs(this);
        setContentView(R.layout.settings_activity);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        ImageView imageView = findViewById(R.id.imvwDp);
        imageView.setOnClickListener(view -> CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON)
                .setOutputUri(Uri.fromFile(new File(getFilesDir(), "dp.jpg")))
                .setFixAspectRatio(true)
                .start(this));
        TextView txtName = findViewById(R.id.txtName);
        txtName.setOnClickListener(view -> changeName());
        TextView txtAddress = findViewById(R.id.txtAddress);

        Database db = Database.getInstance(this);
        Glide.with(this).load(Uri.fromFile(new File(db.get("dp"))))
                .placeholder(R.drawable.ic_launcher_background)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView);
        txtName.setText(db.getName().trim().isEmpty() ? "Anonymous" : db.getName());
        txtAddress.setText(Tor.getInstance(this).getID());

        getFragmentManager().beginTransaction().add(R.id.content, new SettingsFragment()).commit();

        ScrollView scrollView = findViewById(R.id.scrollView);

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));

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

    private void changeName() {
        final FrameLayout view = new FrameLayout(this);
        final EditText editText = new EditText(this);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setSingleLine();
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        view.addView(editText);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());

        final Database db = Database.getInstance(this);

        view.setPadding(padding, padding, padding, padding);
        editText.setText(db.getName());
        new AlertDialog.Builder(this)
                .setTitle(R.string.change_alias)
                .setView(view)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    db.setName(editText.getText().toString().trim());
                    ((TextView) findViewById(R.id.txtName)).setText(db.getName().trim().isEmpty() ? "Anonymous" : db.getName());
                    Snackbar.make(findViewById(R.id.txtName), R.string.alias_changed, Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                }).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.prefs_menu, menu);
        return true;
    }

    void doReset() {
        Settings.getPrefs(this).edit().clear().apply();
        Intent intent = getIntent();
        finish();
        startActivity(intent);
        overridePendingTransition(0, 0);
        Snackbar.make(findViewById(R.id.content), "All settings reset", Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        }
        if (id == R.id.action_reset) {
            doReset();
        }
        return super.onOptionsItemSelected(item);
    }


    public static class SettingsFragment extends PreferenceFragment {

        public static final int PR_WRITE_EXTERNAL_STORAGE = 10;
        public static final String[] permission = {READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE};


        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);

            findPreference("use_dark_mode").setOnPreferenceChangeListener((preference, o) -> {
                boolean useDarkTheme = (boolean) o;
                if (useDarkTheme) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
                return true;
            });

            findPreference("export_id").setOnPreferenceClickListener(preference -> {
                if (!checkPermission(getActivity())) {
                    requestPermission(getActivity(), permission);
                    return true;
                } else {
                    showPasswordDialog();
                }
                return true;
            });

            findPreference("about").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(getActivity(), AboutActivity.class));
                return false;
            });


        }

        public static void requestPermission(Activity activity, String[] permission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", activity.getApplicationContext().getPackageName())));
                    activity.startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(activity, "ERR " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            ActivityCompat.requestPermissions(activity, permission, PR_WRITE_EXTERNAL_STORAGE);

        }

        public static boolean checkPermission(Activity activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager();
            } else {
                int rd = ContextCompat.checkSelfPermission(activity.getApplicationContext(), READ_EXTERNAL_STORAGE);
                int wr = ContextCompat.checkSelfPermission(activity.getApplicationContext(), WRITE_EXTERNAL_STORAGE);
                return rd == PackageManager.PERMISSION_GRANTED && wr == PackageManager.PERMISSION_GRANTED;
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            switch (requestCode) {
                case PR_WRITE_EXTERNAL_STORAGE:
                    if (grantResults.length > 0) {
                        boolean rd = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                        boolean wr = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                        if (rd && wr) {
                            Toast.makeText(getActivity(), "Permission Granted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), "Permission Denied", Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        Toast.makeText(getActivity(), "You Denied Permission", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }

        private void showPasswordDialog() {
            final View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_two_password, null);
            final TextInputEditText txtPassword = view.findViewById(R.id.txtPassword);
            final TextInputEditText txtConfPassword = view.findViewById(R.id.txtConfPassword);

            AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.password_for_export_backup)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    }).create();

            alertDialog.setOnShowListener(dialogInterface -> {

                Button button = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(view1 -> {
                    String password = txtPassword.getText().toString().trim();
                    String confPassword = txtConfPassword.getText().toString().trim();
                    if (password.length() < 8) {
                        txtPassword.setError("Password is less than 8 characters");
                        return;
                    }
                    if (!password.equals(confPassword)) {
                        txtConfPassword.setError("Password does not match");
                        return;
                    }

                    PasswordValidator pv = PasswordValidator.getInstance();
                    if (!pv.validate(password)) {
                        txtPassword.setError("Password must have 1 numeric, 1 upper letter and 1 special character");
                        return;
                    }

                    new CreateZip(getActivity(), password).execute();
                    alertDialog.dismiss();
                });
            });

            alertDialog.show();
        }

    }


}