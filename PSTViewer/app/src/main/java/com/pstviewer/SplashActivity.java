package com.pstviewer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long FADE_IN_DURATION  = 700L;
    private static final long HOLD_DURATION     = 1600L;
    private static final long FADE_OUT_DURATION = 500L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View content = findViewById(R.id.splashContent);
        if (content != null) {
            // Fade content in
            AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(FADE_IN_DURATION);
            fadeIn.setFillAfter(true);
            content.startAnimation(fadeIn);
        }

        // After hold period, fade out and navigate to MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (content != null) {
                AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
                fadeOut.setDuration(FADE_OUT_DURATION);
                fadeOut.setFillAfter(true);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationRepeat(Animation animation) {}
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        launchMain();
                    }
                });
                content.startAnimation(fadeOut);
            } else {
                launchMain();
            }
        }, FADE_IN_DURATION + HOLD_DURATION);
    }

    private void launchMain() {
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        // Swallow back press during splash
    }
}
