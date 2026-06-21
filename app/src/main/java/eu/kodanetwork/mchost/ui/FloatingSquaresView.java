package eu.kodanetwork.mchost.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

public class FloatingSquaresView extends View {
    private Paint paint;
    private Square[] squares;
    private Handler handler;
    private Runnable updater;

    public FloatingSquaresView(Context context) {
        super(context);
        init();
    }

    public FloatingSquaresView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloatingSquaresView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.BLACK);
        paint = new Paint();
        paint.setColor(0x88FF6B00); // Semi-transparent Koda Orange
        paint.setStyle(Paint.Style.FILL);
        squares = new Square[15];
        
        for (int i = 0; i < squares.length; i++) {
            squares[i] = new Square();
        }

        handler = new Handler(Looper.getMainLooper());
        updater = new Runnable() {
            @Override
            public void run() {
                if (getContext() != null && !eu.kodanetwork.mchost.App.getPrefs(getContext()).getBoolean("animations_enabled", true)) {
                    handler.postDelayed(this, 1000); // Check again every second, but don't animate
                    return;
                }
                for (Square s : squares) {
                    s.update(getWidth(), getHeight());
                }
                invalidate();
                handler.postDelayed(this, 16);
            }
        };
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(updater);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(updater);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Square s : squares) {
            canvas.drawRect(s.x, s.y, s.x + s.size, s.y + s.size, paint);
        }
    }

    private static class Square {
        float x, y;
        float vx, vy;
        float size;
        boolean initialized = false;

        void update(int w, int h) {
            if (!initialized && w > 0 && h > 0) {
                x = (float) (Math.random() * w);
                y = (float) (Math.random() * h);
                vx = (float) ((Math.random() - 0.5) * 4);
                vy = (float) ((Math.random() - 0.5) * 4);
                size = (float) (30 + Math.random() * 70);
                initialized = true;
            }
            if (!initialized) return;

            x += vx;
            y += vy;

            if (x < -size) x = w;
            if (x > w) x = -size;
            if (y < -size) y = h;
            if (y > h) y = -size;
        }
    }
}
