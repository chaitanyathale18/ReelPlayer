package com.reelplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText urlInput;
    private Button playBtn;
    private Button permissionBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlInput = findViewById(R.id.url_input);
        playBtn = findViewById(R.id.play_btn);
        permissionBtn = findViewById(R.id.permission_btn);

        // Handle YouTube URL shared/opened into app
        handleIncomingIntent(getIntent());

        permissionBtn.setOnClickListener(v -> requestOverlayPermission());

        playBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (url.isEmpty()) {
                url = "https://www.youtube.com";
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_LONG).show();
                requestOverlayPermission();
                return;
            }
            startFloatingService(url);
            // Minimize this activity
            moveTaskToBack(true);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                urlInput.setText(data.toString());
            }
        }
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startFloatingService(String url) {
        Intent serviceIntent = new Intent(this, FloatingService.class);
        serviceIntent.putExtra("url", url);
        startForegroundService(serviceIntent);
        Toast.makeText(this, "Started! Tap the floating button to dim screen", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionButton();
    }

    private void updatePermissionButton() {
        boolean hasPermission = Settings.canDrawOverlays(this);
        permissionBtn.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
        playBtn.setAlpha(hasPermission ? 1.0f : 0.5f);
    }
}
