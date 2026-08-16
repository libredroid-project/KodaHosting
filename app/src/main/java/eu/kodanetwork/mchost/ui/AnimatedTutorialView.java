package eu.kodanetwork.mchost.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import eu.kodanetwork.mchost.R;

/**
 * A custom view that draws a hardware-accelerated, completely animated step-by-step guide
 * for DNS configuration, custom-tailored to the detected provider (Cloudflare, IONOS, GoDaddy, or Default).
 * Switches dynamically between a Desktop Browser mockup and a Mobile App mockup based on whether the provider app is installed.
 */
public class AnimatedTutorialView extends View {
    private Paint paint;
    private Paint textPaint;
    private float animProgress = 0f;
    private ValueAnimator animator;
    private int currentStep = 1; // 1, 2, or 3
    private String provider = "Default";
    private int serverPort = 25565;
    private boolean isAppInstalled = false;

    public AnimatedTutorialView(Context context) {
        super(context);
        init();
    }

    public AnimatedTutorialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimatedTutorialView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(getResources().getDisplayMetrics().density * 11);
        android.graphics.Typeface koda = androidx.core.content.res.ResourcesCompat.getFont(getContext(), R.font.font_koda);
        textPaint.setTypeface(koda != null ? koda : android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        // Looping animator for step timeline
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(3500); // 3.5 seconds per animation step loop
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    public void setStep(int step) {
        this.currentStep = step;
        if (animator != null) {
            animator.cancel();
            animator.start();
        }
    }

    public void setProvider(String provider) {
        this.provider = provider;
        invalidate();
    }

    private String recordService = "_minecraft";
    private String recordProtocol = "TCP";
    private String recordTarget = "play.server.com";

    public void setRecordDetails(String service, String protocol, String target, int port) {
        this.recordService = service;
        this.recordProtocol = protocol;
        this.recordTarget = target;
        this.serverPort = port;
        invalidate();
    }

    public void setAppInstalled(boolean installed) {
        this.isAppInstalled = installed;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        boolean animsEnabled = eu.kodanetwork.mchost.App.getPrefs(getContext()).getBoolean("animations_enabled", true);
        if (animsEnabled && animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();

        if (isAppInstalled) {
            drawMockMobilePhone(canvas, w, h, density);
        } else {
            drawMockBrowser(canvas, w, h, density);
        }
    }

    // ═══ DESKTOP BROWSER MOCKUP ═══
    private void drawMockBrowser(Canvas canvas, float w, float h, float density) {
        // Background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1E1E1E);
        canvas.drawRoundRect(0, 0, w, h, 8 * density, 8 * density, paint);

        // Header bar
        paint.setColor(0xFF2D2D2D);
        canvas.drawRoundRect(0, 0, w, 32 * density, 8 * density, 8 * density, paint);
        canvas.drawRect(0, 20 * density, w, 32 * density, paint);

        // Window controls (dots)
        paint.setColor(0xFFFF5F56);
        canvas.drawCircle(12 * density, 16 * density, 4 * density, paint);
        paint.setColor(0xFFFFBD2E);
        canvas.drawCircle(24 * density, 16 * density, 4 * density, paint);
        paint.setColor(0xFF27C93F);
        canvas.drawCircle(36 * density, 16 * density, 4 * density, paint);

        // Address bar
        paint.setColor(0xFF161616);
        RectF urlRect = new RectF(50 * density, 6 * density, w - 16 * density, 26 * density);
        canvas.drawRoundRect(urlRect, 4 * density, 4 * density, paint);

        // URL text
        textPaint.setColor(0xFF8A8A8A);
        textPaint.setTextSize(density * 9);
        canvas.drawText(getProviderUrl(), 58 * density, 19 * density, textPaint);

        // Draw browser mockup steps
        switch (currentStep) {
            case 1:
                drawBrowserStep1(canvas, w, h, density);
                break;
            case 2:
                drawBrowserStep2(canvas, w, h, density);
                break;
            case 3:
                drawBrowserStep3(canvas, w, h, density);
                break;
        }
    }

    private void drawBrowserStep1(Canvas canvas, float w, float h, float density) {
        // Sidebar
        paint.setColor(0xFF2A2A2A);
        canvas.drawRect(0, 32 * density, 56 * density, h, paint);

        // DNS Button
        float dnsY = 56 * density;
        paint.setColor(getProviderColor());
        paint.setAlpha(30);
        canvas.drawRect(6 * density, dnsY, 50 * density, dnsY + 28 * density, paint);
        paint.setAlpha(255);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(density * 8);
        canvas.drawText("DNS", 18 * density, dnsY + 18 * density, textPaint);

        // Canvas Area
        paint.setColor(0xFF252525);
        canvas.drawRect(62 * density, 38 * density, w - 6 * density, h - 6 * density, paint);

        // Mock contents
        paint.setColor(0xFF333333);
        canvas.drawRoundRect(68 * density, 46 * density, w - 12 * density, 68 * density, 4 * density, 4 * density, paint);
        canvas.drawRoundRect(68 * density, 74 * density, w - 12 * density, 96 * density, 4 * density, 4 * density, paint);

        // Animate cursor moving to DNS button
        float startX = w * 0.8f;
        float startY = h * 0.8f;
        float targetX = 28 * density;
        float targetY = dnsY + 14 * density;

        float curX = lerp(startX, targetX, Math.min(1f, animProgress / 0.6f));
        float curY = lerp(startY, targetY, Math.min(1f, animProgress / 0.6f));

        if (animProgress >= 0.6f && animProgress <= 0.8f) {
            float t = (animProgress - 0.6f) / 0.2f;
            drawClickRipple(canvas, targetX, targetY, t, density);
        }

        drawCursor(canvas, curX, curY, density);
    }

    private void drawBrowserStep2(Canvas canvas, float w, float h, float density) {
        textPaint.setColor(0xFF8A8A8A);
        textPaint.setTextSize(density * 9);
        canvas.drawText("DNS Management > " + provider, 12 * density, 48 * density, textPaint);

        // Add Record Button
        float btnX = w - 90 * density;
        float btnY = 56 * density;
        RectF btnRect = new RectF(btnX, btnY, w - 12 * density, btnY + 24 * density);
        paint.setColor(getProviderColor());
        canvas.drawRoundRect(btnRect, 4 * density, 4 * density, paint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(density * 8);
        canvas.drawText("+ Add Record", btnX + 8 * density, btnY + 15 * density, textPaint);

        // Table Header
        paint.setColor(0xFF2A2A2A);
        canvas.drawRect(12 * density, 90 * density, w - 12 * density, 110 * density, paint);
        textPaint.setColor(0xFF666666);
        canvas.drawText("Type", 18 * density, 103 * density, textPaint);
        canvas.drawText("Name", 60 * density, 103 * density, textPaint);
        canvas.drawText("Content", 140 * density, 103 * density, textPaint);

        // Animate cursor
        float startX = w * 0.2f;
        float startY = h * 0.8f;
        float targetX = btnX + 38 * density;
        float targetY = btnY + 12 * density;

        float curX = lerp(startX, targetX, Math.min(1f, animProgress / 0.6f));
        float curY = lerp(startY, targetY, Math.min(1f, animProgress / 0.6f));

        if (animProgress >= 0.6f && animProgress <= 0.8f) {
            float t = (animProgress - 0.6f) / 0.2f;
            drawClickRipple(canvas, targetX, targetY, t, density);
        }

        // Show mock dropdown menu
        if (animProgress >= 0.65f) {
            paint.setColor(0xFF2D2D2D);
            RectF dropRect = new RectF(btnX - 20 * density, btnY + 26 * density, w - 12 * density, btnY + 90 * density);
            canvas.drawRoundRect(dropRect, 4 * density, 4 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * density);
            paint.setColor(0xFF444444);
            canvas.drawRoundRect(dropRect, 4 * density, 4 * density, paint);
            paint.setStyle(Paint.Style.FILL);

            textPaint.setColor(0xFFCCCCCC);
            canvas.drawText("A - Address", btnX - 12 * density, btnY + 42 * density, textPaint);
            canvas.drawText("CNAME - Alias", btnX - 12 * density, btnY + 60 * density, textPaint);
            paint.setColor(getProviderColor());
            paint.setAlpha(40);
            canvas.drawRect(btnX - 18 * density, btnY + 68 * density, w - 14 * density, btnY + 86 * density, paint);
            paint.setAlpha(255);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("SRV - Service locator", btnX - 12 * density, btnY + 80 * density, textPaint);
        }

        drawCursor(canvas, curX, curY, density);
    }

    private void drawBrowserStep3(Canvas canvas, float w, float h, float density) {
        textPaint.setColor(0xFF8A8A8A);
        textPaint.setTextSize(density * 9);
        canvas.drawText("Add SRV Record Form", 12 * density, 44 * density, textPaint);

        float row1Y = 52 * density;
        float row2Y = 82 * density;
        float row3Y = 112 * density;

        // Draw input borders
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1 * density);
        paint.setColor(0xFF444444);

        canvas.drawRoundRect(12 * density, row1Y, 72 * density, row1Y + 22 * density, 3 * density, 3 * density, paint);
        canvas.drawRoundRect(78 * density, row1Y, 138 * density, row1Y + 22 * density, 3 * density, 3 * density, paint);
        canvas.drawRoundRect(144 * density, row1Y, w - 12 * density, row1Y + 22 * density, 3 * density, 3 * density, paint);
        canvas.drawRoundRect(12 * density, row2Y, w - 88 * density, row2Y + 22 * density, 3 * density, 3 * density, paint);
        canvas.drawRoundRect(w - 82 * density, row2Y, w - 12 * density, row2Y + 22 * density, 3 * density, 3 * density, paint);

        paint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFF555555);
        textPaint.setTextSize(density * 7);
        canvas.drawText(getContext().getString(R.string.cdns_lbl_service), 14 * density, row1Y - 2 * density, textPaint);
        canvas.drawText(getContext().getString(R.string.cdns_lbl_protocol), 80 * density, row1Y - 2 * density, textPaint);
        canvas.drawText(getContext().getString(R.string.cdns_lbl_hostname), 146 * density, row1Y - 2 * density, textPaint);
        canvas.drawText(getContext().getString(R.string.cdns_lbl_target), 14 * density, row2Y - 2 * density, textPaint);
        canvas.drawText(getContext().getString(R.string.cdns_lbl_port), w - 80 * density, row2Y - 2 * density, textPaint);

        // Save Button
        RectF saveRect = new RectF(w - 72 * density, row3Y + 4 * density, w - 12 * density, row3Y + 26 * density);
        paint.setColor(getProviderColor());
        canvas.drawRoundRect(saveRect, 3 * density, 3 * density, paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(density * 8);
        canvas.drawText(getContext().getString(R.string.cdns_lbl_save), w - 50 * density, row3Y + 19 * density, textPaint);

        // Typing inputs
        textPaint.setColor(0xFFF0F0F0);
        textPaint.setTextSize(density * 8);

        if (animProgress > 0.1f) {
            String val = recordService;
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.1f) / 0.15f * val.length()))), 16 * density, row1Y + 14 * density, textPaint);
        }
        if (animProgress > 0.25f) {
            String val = "_" + recordProtocol.toLowerCase();
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.25f) / 0.1f * val.length()))), 82 * density, row1Y + 14 * density, textPaint);
        }
        if (animProgress > 0.35f) {
            String val = "@";
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.35f) / 0.05f * val.length()))), 148 * density, row1Y + 14 * density, textPaint);
        }
        if (animProgress >= 0.7f) {
            String val = recordTarget;
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.7f) / 0.2f * val.length()))), 18 * density, row2Y + 16 * density, textPaint);
        }
        if (animProgress > 0.7f) {
            String val = String.valueOf(serverPort);
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.7f) / 0.1f * val.length()))), w - 78 * density, row2Y + 14 * density, textPaint);
        }

        if (animProgress > 0.8f) {
            float startX = w * 0.3f;
            float startY = row2Y + 30 * density;
            float targetX = w - 42 * density;
            float targetY = row3Y + 15 * density;
            float t = (animProgress - 0.8f) / 0.2f;
            drawCursor(canvas, lerp(startX, targetX, t), lerp(startY, targetY, t), density);
        }
    }


    // ═══ MOBILE PHONE MOCKUP (WHEN APP IS INSTALLED) ═══
    private void drawMockMobilePhone(Canvas canvas, float w, float h, float density) {
        // Base viewport background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF161616);
        canvas.drawRoundRect(0, 0, w, h, 8 * density, 8 * density, paint);

        // Center a vertical mobile device frame
        float phoneW = 100 * density;
        float phoneH = 164 * density;
        float phoneX = (w - phoneW) / 2f;
        float phoneY = (h - phoneH) / 2f;

        // Outer case
        paint.setColor(0xFF333333);
        canvas.drawRoundRect(phoneX, phoneY, phoneX + phoneW, phoneY + phoneH, 14 * density, 14 * density, paint);

        // Screen area
        float scrX = phoneX + 4 * density;
        float scrY = phoneY + 6 * density;
        float scrW = phoneW - 8 * density;
        float scrH = phoneH - 12 * density;
        paint.setColor(0xFF0F0F0F);
        canvas.drawRect(scrX, scrY, scrX + scrW, scrY + scrH, paint);

        // Notch at top
        paint.setColor(0xFF333333);
        RectF notchRect = new RectF(phoneX + phoneW/2f - 14 * density, phoneY + 4 * density, phoneX + phoneW/2f + 14 * density, phoneY + 10 * density);
        canvas.drawRoundRect(notchRect, 3 * density, 3 * density, paint);

        // Clip drawing region to phone screen to avoid overflows
        canvas.save();
        canvas.clipRect(scrX, scrY + 6 * density, scrX + scrW, scrY + scrH);

        // App Status Bar
        paint.setColor(0xFF1E1E1E);
        canvas.drawRect(scrX, scrY, scrX + scrW, scrY + 12 * density, paint);
        // Small battery and signal icons
        paint.setColor(0xFF555555);
        canvas.drawRect(scrX + scrW - 14 * density, scrY + 4 * density, scrX + scrW - 4 * density, scrY + 8 * density, paint);
        canvas.drawCircle(scrX + 10 * density, scrY + 6 * density, 2 * density, paint);

        // App Title Bar (Brand themed)
        paint.setColor(getProviderColor());
        canvas.drawRect(scrX, scrY + 12 * density, scrX + scrW, scrY + 28 * density, paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(density * 7.5f);
        canvas.drawText(provider + " App", scrX + 6 * density, scrY + 23 * density, textPaint);

        // Draw Mobile app mockup steps
        switch (currentStep) {
            case 1:
                drawMobileStep1(canvas, scrX, scrY, scrW, scrH, density);
                break;
            case 2:
                drawMobileStep2(canvas, scrX, scrY, scrW, scrH, density);
                break;
            case 3:
                drawMobileStep3(canvas, scrX, scrY, scrW, scrH, density);
                break;
        }

        canvas.restore();
    }

    private void drawMobileStep1(Canvas canvas, float x, float y, float w, float h, float density) {
        // Inside app dashboard list
        float itemY = y + 36 * density;
        paint.setColor(0xFF222222);

        // List items
        canvas.drawRoundRect(x + 4 * density, itemY, x + w - 4 * density, itemY + 18 * density, 2 * density, 2 * density, paint);
        canvas.drawRoundRect(x + 4 * density, itemY + 22 * density, x + w - 4 * density, itemY + 40 * density, 2 * density, 2 * density, paint);
        canvas.drawRoundRect(x + 4 * density, itemY + 44 * density, x + w - 4 * density, itemY + 62 * density, 2 * density, 2 * density, paint);

        textPaint.setColor(0xFF777777);
        textPaint.setTextSize(density * 6);
        canvas.drawText("Web Hosting", x + 10 * density, itemY + 11 * density, textPaint);
        canvas.drawText("SSL Certificates", x + 10 * density, itemY + 55 * density, textPaint);

        // Highlight "Domains" row
        paint.setColor(getProviderColor());
        paint.setAlpha(40);
        canvas.drawRect(x + 4 * density, itemY + 22 * density, x + w - 4 * density, itemY + 40 * density, paint);
        paint.setAlpha(255);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Domains & DNS", x + 10 * density, itemY + 33 * density, textPaint);

        // Animate Touch Point
        float startX = x + w * 0.8f;
        float startY = y + h * 0.8f;
        float targetX = x + w * 0.5f;
        float targetY = itemY + 31 * density;

        float curX = lerp(startX, targetX, Math.min(1f, animProgress / 0.6f));
        float curY = lerp(startY, targetY, Math.min(1f, animProgress / 0.6f));

        if (animProgress >= 0.6f && animProgress <= 0.8f) {
            float t = (animProgress - 0.6f) / 0.2f;
            drawTouchFeedback(canvas, targetX, targetY, t, density);
        }

        drawFingerPointer(canvas, curX, curY, density);
    }

    private void drawMobileStep2(Canvas canvas, float x, float y, float w, float h, float density) {
        // Domain details screen
        float contentY = y + 34 * density;
        textPaint.setColor(0xFF8A8A8A);
        textPaint.setTextSize(density * 7);
        canvas.drawText("DNS Configuration", x + 6 * density, contentY + 8 * density, textPaint);

        // Add Record mobile button
        float btnY = contentY + 16 * density;
        paint.setColor(getProviderColor());
        canvas.drawRoundRect(x + 6 * density, btnY, x + w - 6 * density, btnY + 20 * density, 3 * density, 3 * density, paint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(density * 7);
        canvas.drawText("+ Add DNS Record", x + 14 * density, btnY + 13 * density, textPaint);

        // Animate Touch Point clicking "+ Add DNS Record"
        float startX = x + w * 0.2f;
        float startY = y + h * 0.8f;
        float targetX = x + w * 0.5f;
        float targetY = btnY + 10 * density;

        float curX = lerp(startX, targetX, Math.min(1f, animProgress / 0.6f));
        float curY = lerp(startY, targetY, Math.min(1f, animProgress / 0.6f));

        if (animProgress >= 0.6f && animProgress <= 0.8f) {
            float t = (animProgress - 0.6f) / 0.2f;
            drawTouchFeedback(canvas, targetX, targetY, t, density);
        }

        // Dropdown menu mock popup inside app
        if (animProgress >= 0.65f) {
            paint.setColor(0xFF222222);
            RectF menuRect = new RectF(x + 12 * density, btnY + 24 * density, x + w - 12 * density, btnY + 70 * density);
            canvas.drawRoundRect(menuRect, 3 * density, 3 * density, paint);

            textPaint.setColor(0xFF8A8A8A);
            textPaint.setTextSize(density * 6.5f);
            canvas.drawText("CNAME - Record", x + 18 * density, btnY + 36 * density, textPaint);
            paint.setColor(getProviderColor());
            paint.setAlpha(45);
            canvas.drawRect(x + 14 * density, btnY + 44 * density, x + w - 14 * density, btnY + 64 * density, paint);
            paint.setAlpha(255);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("SRV - Service Locator", x + 18 * density, btnY + 57 * density, textPaint);
        }

        drawFingerPointer(canvas, curX, curY, density);
    }

    private void drawMobileStep3(Canvas canvas, float x, float y, float w, float h, float density) {
        // Mobile layout form input rows (IONOS style SRV record)
        float startY = y + 24 * density;
        float rowHeight = 12 * density;
        float spacing = 2 * density;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.5f * density);
        paint.setColor(0xFF333333);

        String[] labels = {
            getContext().getString(R.string.cdns_lbl_service),
            getContext().getString(R.string.cdns_lbl_protocol),
            getContext().getString(R.string.cdns_lbl_hostname),
            getContext().getString(R.string.cdns_lbl_target),
            getContext().getString(R.string.cdns_lbl_priority),
            getContext().getString(R.string.cdns_lbl_weight),
            getContext().getString(R.string.cdns_lbl_port)
        };
        float currentY = startY;

        for (int i = 0; i < labels.length; i++) {
            // Draw box
            canvas.drawRoundRect(x + 6 * density, currentY + 4 * density, x + w - 6 * density, currentY + rowHeight + 4 * density, 1 * density, 1 * density, paint);
            
            // Draw label
            paint.setStyle(Paint.Style.FILL);
            textPaint.setColor(0xFF888888);
            textPaint.setTextSize(density * 4f);
            canvas.drawText(labels[i], x + 8 * density, currentY + 3 * density, textPaint);
            
            paint.setStyle(Paint.Style.STROKE);
            currentY += rowHeight + spacing + 4 * density;
        }

        // Mobile Save Button
        float btnY = currentY + 2 * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(getProviderColor());
        canvas.drawRoundRect(x + 6 * density, btnY, x + w - 6 * density, btnY + 16 * density, 2 * density, 2 * density, paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(density * 6.5f);
        canvas.drawText(getContext().getString(R.string.cdns_lbl_save), x + w/2f - 12 * density, btnY + 11 * density, textPaint);

        // Typing animations inside the boxes
        textPaint.setColor(0xFFCCCCCC);
        textPaint.setTextSize(density * 5f);
        
        float textYOffset = 13 * density;
        
        if (animProgress > 0.1f) {
            String val = "_minecraft";
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.1f) / 0.1f * val.length()))), x + 8 * density, startY + textYOffset, textPaint);
        }
        if (animProgress > 0.2f) {
            String val = "TCP";
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.2f) / 0.05f * val.length()))), x + 8 * density, startY + (rowHeight + spacing + 4 * density) * 1 + textYOffset, textPaint);
        }
        if (animProgress > 0.3f) {
            String val = "@";
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.3f) / 0.05f * val.length()))), x + 8 * density, startY + (rowHeight + spacing + 4 * density) * 2 + textYOffset, textPaint);
        }
        if (animProgress > 0.4f) {
            String val = recordTarget;
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.4f) / 0.15f * val.length()))), x + 8 * density, startY + (rowHeight + spacing + 4 * density) * 3 + textYOffset, textPaint);
        }
        if (animProgress > 0.6f) {
            String val = "0";
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.6f) / 0.05f * val.length()))), x + 8 * density, startY + (rowHeight + spacing + 4 * density) * 4 + textYOffset, textPaint);
        }
        if (animProgress > 0.7f) {
            String val = "5";
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.7f) / 0.05f * val.length()))), x + 8 * density, startY + (rowHeight + spacing + 4 * density) * 5 + textYOffset, textPaint);
        }
        if (animProgress > 0.8f) {
            String val = String.valueOf(serverPort);
            canvas.drawText(val.substring(0, Math.min(val.length(), (int) ((animProgress - 0.8f) / 0.1f * val.length()))), x + 8 * density, startY + (rowHeight + spacing + 4 * density) * 6 + textYOffset, textPaint);
        }

        // Touch point click save
        if (animProgress > 0.9f) {
            float startTouchX = x + w * 0.3f;
            float startTouchY = startY + (rowHeight + spacing + 4 * density) * 6 + textYOffset;
            float targetTouchX = x + w * 0.5f;
            float targetTouchY = btnY + 8 * density;
            float t = (animProgress - 0.9f) / 0.1f;
            drawFingerPointer(canvas, lerp(startTouchX, targetTouchX, t), lerp(startTouchY, targetTouchY, t), density);
        }
    }


    // ═══ HELPER DRAWING FUNCTIONS ═══
    private float lerp(float start, float stop, float amount) {
        return start + amount * (stop - start);
    }

    private void drawClickRipple(Canvas canvas, float x, float y, float progress, float density) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(getProviderColor());
        paint.setAlpha((int) (255 * (1f - progress)));
        canvas.drawCircle(x, y, progress * 24 * density, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void drawTouchFeedback(Canvas canvas, float x, float y, float progress, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha((int) (140 * (1f - progress)));
        canvas.drawCircle(x, y, progress * 16 * density, paint);
        paint.setAlpha(255);
    }

    private void drawCursor(Canvas canvas, float x, float y, float density) {
        paint.setColor(Color.WHITE);
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + 10 * density, y + 4 * density);
        path.lineTo(x + 6 * density, y + 6 * density);
        path.lineTo(x + 10 * density, y + 12 * density);
        path.lineTo(x + 8 * density, y + 13 * density);
        path.lineTo(x + 4 * density, y + 7 * density);
        path.lineTo(x, y + 10 * density);
        path.close();
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1 * density);
        paint.setColor(Color.BLACK);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFingerPointer(Canvas canvas, float x, float y, float density) {
        // Draw a simple finger tap circular indicator representing mobile touch input
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(180);
        canvas.drawCircle(x, y, 6 * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(x, y, 6 * density, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private int getProviderColor() {
        switch (provider) {
            case "Cloudflare":
                return Color.parseColor("#F38020");
            case "IONOS":
                return Color.parseColor("#002E6E");
            case "GoDaddy":
                return Color.parseColor("#00A699");
            default:
                return Color.parseColor("#FF6B00"); // Koda Orange
        }
    }

    private String getProviderUrl() {
        switch (provider) {
            case "Cloudflare":
                return "dash.cloudflare.com/dns";
            case "IONOS":
                return "login.ionos.de/dns-settings";
            case "GoDaddy":
                return "dcc.godaddy.com/dns-management";
            default:
                return "your-dns-registrar.com/dns-control";
        }
    }
}
