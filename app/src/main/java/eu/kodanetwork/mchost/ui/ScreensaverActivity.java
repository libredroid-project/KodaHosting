package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import eu.kodanetwork.mchost.App;

public class ScreensaverActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String style = prefs.getString("afk_style", "squares");

        if ("squares".equals(style)) {
            setContentView(new FloatingSquaresView(this));
        } else {
            View blackView = new View(this);
            blackView.setBackgroundColor(Color.BLACK);
            setContentView(blackView);
        }

        // Hide navigation and status bars
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        App.resetAfkTimer();
        finish();
        overridePendingTransition(0, 0); // No animation
        return true;
    }

}
