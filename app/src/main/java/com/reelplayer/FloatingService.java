package com.reelplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private static final String CHANNEL_ID = "ReelPlayerChannel";
    private static final int NOTIF_ID = 1;
    private static final String ACTION_STOP = "ACTION_STOP";

    private WindowManager windowManager;
    private View webViewContainer;
    private WebView webView;
    private View dimOverlayView;
    private WindowManager.LayoutParams dimOverlayParams;
    private View floatingBtnView;
    private WindowManager.LayoutParams floatingBtnParams;

    private boolean isDimmed = false;
    private int tapCount = 0;
    private final Handler tapHandler = new Handler(Looper.getMainLooper());
    private static final long TRIPLE_TAP_TIMEOUT = 600L;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    private String videoUrl = "https://www.youtube.com";

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildNotification());

        if (intent != null && intent.hasExtra("url")) {
            videoUrl = intent.getStringExtra("url");
        }

        removeAllViews();
        setupWebView();
        setupDimOverlay();
        setupFloatingButton();

        return START_NOT_STICKY;
    }

    private void setupWebView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        webViewContainer = inflater.inflate(R.layout.overlay_webview, null);
        webView = webViewContainer.findViewById(R.id.overlay_webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
        );

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.loadUrl(videoUrl);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(webViewContainer, params);
    }

    private void setupDimOverlay() {
        dimOverlayView = new View(this);
        dimOverlayView.setBackgroundColor(Color.BLACK);
        dimOverlayView.setAlpha(0f);
        dimOverlayView.setVisibility(View.GONE);

        dimOverlayParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        windowManager.addView(dimOverlayView, dimOverlayParams);
    }

    private void setupFloatingButton() {
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingBtnView = inflater.inflate(R.layout.floating_button, null);

        floatingBtnParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        floatingBtnParams.gravity = Gravity.TOP | Gravity.START;
        floatingBtnParams.x = 40;
        floatingBtnParams.y = 300;

        floatingBtnView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = floatingBtnParams.x;
                    initialY = floatingBtnParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(event.getRawX() - initialTouchX);
                    int dy = (int)(event.getRawY() - initialTouchY);
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                    if (isDragging) {
                        floatingBtnParams.x = initialX + dx;
                        floatingBtnParams.y = initialY + dy;
                        windowManager.updateViewLayout(floatingBtnView, floatingBtnParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) registerTap();
                    return true;
            }
            return false;
        });

        windowManager.addView(floatingBtnView, floatingBtnParams);
    }

    private void registerTap() {
        tapCount++;
        tapHandler.removeCallbacksAndMessages(null);

        if (tapCount >= 3) {
            tapCount = 0;
            Toast.makeText(this, "ReelPlayer stopped", Toast.LENGTH_SHORT).show();
            tapHandler.postDelayed(this::stopSelf, 300);
            return;
        }

        tapHandler.postDelayed(() -> {
            if (tapCount == 1) toggleDim();
            tapCount = 0;
        }, TRIPLE_TAP_TIMEOUT);
    }

    private void toggleDim() {
        isDimmed = !isDimmed;
        if (isDimmed) {
            webViewContainer.setVisibility(View.INVISIBLE);
            dimOverlayView.setVisibility(View.VISIBLE);
            dimOverlayView.animate().alpha(1f).setDuration(350).start();
            floatingBtnView.setAlpha(0.55f);
            Toast.makeText(this, "Screen dimmed  •  tap to restore  •  triple-tap to exit", Toast.LENGTH_LONG).show();
        } else {
            dimOverlayView.animate().alpha(0f).setDuration(350)
                .withEndAction(() -> dimOverlayView.setVisibility(View.GONE)).start();
            webViewContainer.setVisibility(View.VISIBLE);
            floatingBtnView.setAlpha(1f);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, FloatingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("▶ ReelPlayer Active")
            .setContentText("YouTube running • tap ▶ to dim screen • triple-tap to quit")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "ReelPlayer", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("YouTube background playback");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void removeAllViews() {
        for (View v : new View[]{webViewContainer, dimOverlayView, floatingBtnView}) {
            try { if (v != null) windowManager.removeView(v); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tapHandler.removeCallbacksAndMessages(null);
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        removeAllViews();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
