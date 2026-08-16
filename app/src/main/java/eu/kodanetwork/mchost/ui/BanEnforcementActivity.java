package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.util.ThemeHelper;

public class BanEnforcementActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Fullscreen and Keep Screen On
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setPadding(64, 64, 64, 64);
        
        TextView header = new TextView(this);
        header.setText("=== P.R.A.E.T.O.R ENFORCEMENT ACTION ===");
        header.setTextColor(0xFFFF3333); // Scary Red
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setTextSize(20);
        header.setGravity(android.view.Gravity.CENTER);
        header.setLetterSpacing(0.2f);
        root.addView(header);
        
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, 40));
        root.addView(spacer);
        
        String reason = getIntent().getStringExtra("reason");
        if (reason == null) reason = "UNAUTHORIZED CLIENT MODIFICATION";
        
        addInfoLine(root, "STATUS", "DENIED", 0xFFFF3333);
        addInfoLine(root, "OPERATOR", "SYSTEM_CORE", 0xFF888888);
        addInfoLine(root, "PROTOCOL", "B-144_ENFORCE", 0xFF888888);
        addInfoLine(root, "VIOLATION", reason.toUpperCase(), 0xFFFF6A00);
        
        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(1, 60));
        root.addView(spacer2);
        
        TextView msg = new TextView(this);
        msg.setText("Your access to KodaSMP has been terminated.\nAll assets have been seized.\n\n[ DISCONNECTING... ]");
        msg.setTextColor(0xFFCCCCCC);
        msg.setTypeface(Typeface.MONOSPACE);
        msg.setTextSize(14);
        msg.setGravity(android.view.Gravity.CENTER);
        root.addView(msg);
        
        MaterialButton btn = new MaterialButton(this);
        btn.setText("ACKNOWLEDGE");
        btn.setOnClickListener(v -> finish());
        btn.setCornerRadius(0);
        btn.setBackgroundColor(0xFF1A1A1A);
        btn.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFFF3333));
        btn.setStrokeWidth(2);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 60;
        btn.setLayoutParams(lp);
        root.addView(btn);
        
        setContentView(root);
        
        // Apply Grid Background
        root.setBackgroundResource(R.drawable.bg_cyber_grid);
    }
    
    private void addInfoLine(LinearLayout root, String key, String val, int valCol) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER);
        
        TextView k = new TextView(this);
        k.setText(key + ": ");
        k.setTextColor(0xFF555555);
        k.setTypeface(Typeface.MONOSPACE);
        k.setTextSize(12);
        l.addView(k);
        
        TextView v = new TextView(this);
        v.setText(val);
        v.setTextColor(valCol);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        v.setTextSize(12);
        l.addView(v);
        
        root.addView(l);
    }
}
