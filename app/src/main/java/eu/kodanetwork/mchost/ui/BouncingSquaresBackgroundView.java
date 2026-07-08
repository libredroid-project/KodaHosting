package eu.kodanetwork.mchost.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BouncingSquaresBackgroundView extends View {
    private Paint paintSquare;
    private List<Square> squares;
    private Handler handler;
    private Runnable updater;
    private Random random = new Random();

    private final int MAX_SQUARES = 20;

    public BouncingSquaresBackgroundView(Context context) {
        super(context);
        init();
    }

    public BouncingSquaresBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.parseColor("#0C0C0C"));

        paintSquare = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSquare.setColor(Color.parseColor("#FF6B00")); // Koda orange
        paintSquare.setStyle(Paint.Style.FILL);
        paintSquare.setAlpha(80); // Semi-transparent
        
        // Soft glow
        paintSquare.setShadowLayer(10f, 0, 0, Color.parseColor("#44FF6B00"));
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        squares = new ArrayList<>();

        handler = new Handler(Looper.getMainLooper());
        updater = new Runnable() {
            @Override
            public void run() {
                if (getWidth() > 0 && getHeight() > 0) {
                    if (squares.isEmpty()) {
                        for (int i = 0; i < MAX_SQUARES; i++) {
                            squares.add(new Square(getWidth(), getHeight()));
                        }
                    }
                    updateSquares(getWidth(), getHeight());
                    invalidate();
                }
                handler.postDelayed(this, 16);
            }
        };
    }

    private void updateSquares(int w, int h) {
        // Move squares
        for (Square s : squares) {
            s.update(w, h);
        }

        // Check collisions between squares
        for (int i = 0; i < squares.size(); i++) {
            for (int j = i + 1; j < squares.size(); j++) {
                Square s1 = squares.get(i);
                Square s2 = squares.get(j);

                // Simple circle-based collision for smoother bouncing (even if drawn as squares)
                float dx = s1.centerX() - s2.centerX();
                float dy = s1.centerY() - s2.centerY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float minDist = (s1.size / 2) + (s2.size / 2);

                if (dist < minDist) {
                    // Normalize vector
                    float nx = dx / dist;
                    float ny = dy / dist;

                    // Relative velocity
                    float dvx = s1.vx - s2.vx;
                    float dvy = s1.vy - s2.vy;

                    // Velocity along the normal
                    float velAlongNormal = dvx * nx + dvy * ny;

                    // Do not resolve if velocities are separating
                    if (velAlongNormal > 0) continue;

                    // Bounce (restitution = 1 for elastic collision)
                    float restitution = 1f;
                    
                    // Assuming equal mass
                    float impulse = -(1 + restitution) * velAlongNormal / 2f;

                    float impulseX = impulse * nx;
                    float impulseY = impulse * ny;

                    s1.vx += impulseX;
                    s1.vy += impulseY;
                    s2.vx -= impulseX;
                    s2.vy -= impulseY;

                    // Separate to avoid sticking
                    float overlap = 0.5f * (minDist - dist);
                    s1.x += overlap * nx;
                    s1.y += overlap * ny;
                    s2.x -= overlap * nx;
                    s2.y -= overlap * ny;
                }
            }
        }
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
            canvas.drawRect(s.x, s.y, s.x + s.size, s.y + s.size, paintSquare);
        }
    }

    private class Square {
        float x, y;
        float vx, vy;
        float size;

        Square(int w, int h) {
            size = 40f + random.nextFloat() * 80f;
            x = random.nextFloat() * (w - size);
            y = random.nextFloat() * (h - size);
            vx = (random.nextFloat() - 0.5f) * 4f;
            vy = (random.nextFloat() - 0.5f) * 4f;
        }

        float centerX() { return x + size / 2; }
        float centerY() { return y + size / 2; }

        void update(int w, int h) {
            x += vx;
            y += vy;

            // Bounce off walls
            if (x < 0) {
                x = 0;
                vx = -vx;
            } else if (x + size > w) {
                x = w - size;
                vx = -vx;
            }

            if (y < 0) {
                y = 0;
                vy = -vy;
            } else if (y + size > h) {
                y = h - size;
                vy = -vy;
            }
            
            // Keep velocity stable
            float speed = (float) Math.sqrt(vx * vx + vy * vy);
            if (speed > 4f) {
                vx = (vx / speed) * 4f;
                vy = (vy / speed) * 4f;
            } else if (speed < 1f) {
                vx = (vx / speed) * 2f;
                vy = (vy / speed) * 2f;
            }
        }
    }
}
