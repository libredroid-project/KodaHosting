package org.librecommunications.app.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Custom view that draws and animates floating squares in the background.
 */
public class FloatingSquaresView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Square> squares = new ArrayList<>();
    private final Random random = new Random();
    private boolean isAnimating = false;
    private static final int SQUARE_COUNT = 25;

    private static class Square {
        float x, y;
        float size;
        float speed;
        int alpha;
    }

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
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        squares.clear();
        for (int i = 0; i < SQUARE_COUNT; i++) {
            squares.add(createSquare(w, h));
        }
        if (!isAnimating) {
            isAnimating = true;
            postInvalidateOnAnimation();
        }
    }

    private Square createSquare(int width, int height) {
        Square square = new Square();
        square.size = random.nextInt(120) + 40;
        square.x = random.nextInt(Math.max(1, width));
        // Start below the screen
        square.y = height + square.size + random.nextInt(600);
        square.speed = random.nextFloat() * 2.5f + 1.0f;
        square.alpha = random.nextInt(80) + 20; // Keep alpha subtle
        return square;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        for (Square square : squares) {
            paint.setAlpha(square.alpha);
            canvas.drawRect(square.x, square.y, square.x + square.size, square.y + square.size, paint);

            // Move the square upwards
            square.y -= square.speed;
            
            // Reset square when it goes off screen
            if (square.y + square.size < 0) {
                square.y = height + square.size;
                square.x = random.nextInt(Math.max(1, width));
                square.size = random.nextInt(120) + 40;
                square.speed = random.nextFloat() * 2.5f + 1.0f;
                square.alpha = random.nextInt(80) + 20;
            }
        }

        if (isAnimating) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAnimating = false;
    }
}
