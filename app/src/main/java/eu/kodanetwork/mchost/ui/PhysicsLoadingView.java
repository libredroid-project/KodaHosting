package eu.kodanetwork.mchost.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PhysicsLoadingView extends View implements SensorEventListener {

    private static class Square {
        float x, y, size;
        float vx, vy;
        int color;
        public Square(float x, float y, float size, int color) {
            this.x = x; this.y = y; this.size = size; this.color = color;
            Random r = new Random();
            this.vx = (r.nextFloat() - 0.5f) * 20;
            this.vy = (r.nextFloat() - 0.5f) * 20;
        }
    }

    private List<Square> squares = new ArrayList<>();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ax = 0, ay = 0;
    private SensorManager sensorManager;
    private Square grabbed = null;

    public PhysicsLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Random r = new Random();
        for (int i = 0; i < 15; i++) {
            squares.add(new Square(200, 200, 80 + r.nextInt(100), 
                r.nextBoolean() ? 0xFFFF6B00 : 0xFF333333));
        }
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void start() {
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        for (Square s : squares) {
            if (s != grabbed) {
                s.vx += ax * 0.5f;
                s.vy += ay * 0.5f;
                s.x += s.vx;
                s.y += s.vy;
                s.vx *= 0.99f; // friction
                s.vy *= 0.99f;
            }

            // Wall bounce
            if (s.x < 0) { s.x = 0; s.vx *= -0.6f; }
            if (s.x + s.size > w) { s.x = w - s.size; s.vx *= -0.6f; }
            if (s.y < 0) { s.y = 0; s.vy *= -0.6f; }
            if (s.y + s.size > h) { s.y = h - s.size; s.vy *= -0.6f; }

            paint.setColor(s.color);
            canvas.drawRect(s.x, s.y, s.x + s.size, s.y + s.size, paint);
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                for (Square s : squares) {
                    if (tx >= s.x && tx <= s.x + s.size && ty >= s.y && ty <= s.y + s.size) {
                        grabbed = s;
                        break;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (grabbed != null) {
                    grabbed.x = tx - grabbed.size/2;
                    grabbed.y = ty - grabbed.size/2;
                    grabbed.vx = 0; grabbed.vy = 0;
                }
                break;
            case MotionEvent.ACTION_UP:
                grabbed = null;
                break;
        }
        return true;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        ax = -event.values[0]; // Invert for natural fall
        ay = event.values[1];
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
