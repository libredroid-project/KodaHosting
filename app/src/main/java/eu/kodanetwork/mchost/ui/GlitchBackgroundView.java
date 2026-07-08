package eu.kodanetwork.mchost.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class GlitchBackgroundView extends View {

    private Paint paint;
    private Random random;
    private int[] colors = {Color.BLACK, Color.RED, Color.parseColor("#330000"), Color.parseColor("#880000"), Color.parseColor("#111111")};
    private boolean isRunning = false;

    public GlitchBackgroundView(Context context) {
        super(context);
        init();
    }

    public GlitchBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        random = new Random();
    }

    public void startGlitch() {
        isRunning = true;
        invalidate();
    }

    public void stopGlitch() {
        isRunning = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        // Fill background with mostly black or dark red
        canvas.drawColor(random.nextInt(100) < 10 ? Color.RED : Color.BLACK);

        if (isRunning) {
            // Draw random glitch rectangles
            int numRects = 10 + random.nextInt(30);
            for (int i = 0; i < numRects; i++) {
                paint.setColor(colors[random.nextInt(colors.length)]);
                
                int rectWidth = random.nextInt(width);
                int rectHeight = random.nextInt(height / 10 + 1);
                int x = random.nextInt(width);
                int y = random.nextInt(height);
                
                // Random horizontal shift effect
                if (random.nextBoolean()) {
                    x = 0;
                    rectWidth = width;
                }
                
                canvas.drawRect(x, y, x + rectWidth, y + rectHeight, paint);
            }
            
            // Draw random static noise dots
            paint.setColor(Color.WHITE);
            int numDots = random.nextInt(200);
            for (int i = 0; i < numDots; i++) {
                canvas.drawPoint(random.nextInt(width), random.nextInt(height), paint);
            }

            // Post invalidate for extremely fast updates (30-60 fps)
            postInvalidateDelayed(30);
        }
    }
}
