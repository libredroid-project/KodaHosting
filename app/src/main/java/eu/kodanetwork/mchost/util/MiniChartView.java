package eu.kodanetwork.mchost.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Tiny chart used inside the dark server-card tiles.
 * MODE_BARS draws a row of rounded bars (TPS), MODE_LINE draws a smooth
 * sparkline (RAM). History is kept per data key so RecyclerView recycling
 * (a different server rebinding into the same ViewHolder) resets the chart
 * instead of mixing values from two servers.
 */
public class MiniChartView extends View {

    public static final int MODE_BARS = 0;
    public static final int MODE_LINE = 1;

    private static final int HISTORY = 24;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] values = new float[HISTORY];
    private int count = 0;
    private String dataKey = "";

    private int mode = MODE_BARS;
    private float maxValue = 1f;
    private int chartColor = 0xFFF2EFE9;      // cream on espresso tiles
    private int accentColor = 0xFFFF6B00;     // last bar / newest point

    public MiniChartView(Context c, AttributeSet attrs) {
        super(c, attrs);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void configure(int mode, float maxValue, int chartColor, int accentColor) {
        this.mode = mode;
        this.maxValue = Math.max(0.001f, maxValue);
        this.chartColor = chartColor;
        this.accentColor = accentColor;
        invalidate();
    }

    /** Push a 0..1 normalized value; resets history when the data source changes. */
    public void push(String key, float normalized) {
        if (!dataKey.equals(key)) {
            dataKey = key;
            count = 0;
        }
        float clamped = Math.max(0f, Math.min(1f, normalized));
        if (count < HISTORY) {
            values[count++] = clamped;
        } else {
            System.arraycopy(values, 1, values, 0, HISTORY - 1);
            values[HISTORY - 1] = clamped;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || count == 0) {
            drawPlaceholder(canvas, w, h);
            return;
        }
        if (mode == MODE_BARS) {
            drawBars(canvas, w, h);
        } else {
            drawLine(canvas, w, h);
        }
    }

    private void drawPlaceholder(Canvas canvas, int w, int h) {
        // flat dashed baseline while no data yet
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(0x33F2EFE9);
        float y = h - dp(4);
        float dash = dp(4), gap = dp(4);
        for (float x = 0; x < w; x += dash + gap) {
            canvas.drawLine(x, y, Math.min(x + dash, w), y, paint);
        }
    }

    private void drawBars(Canvas canvas, int w, int h) {
        // the last N values as bars, oldest left → newest right
        int shown = Math.min(count, 9);
        float barWidth = w / 11f;
        float gap = barWidth * 0.5f;
        float minH = dp(3);
        for (int i = 0; i < shown; i++) {
            float v = values[count - shown + i];
            int idxFromRight = shown - 1 - i;
            float barH = Math.max(minH, v * (h - dp(2)));
            float left = i * (barWidth + gap);
            boolean newest = idxFromRight == 0;
            paint.setColor(newest ? accentColor : chartColor);
            paint.setAlpha(newest ? 255 : 170);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(left, h - barH, left + barWidth, h,
                    barWidth / 2f, barWidth / 2f, paint);
        }
        paint.setAlpha(255);
    }

    private void drawLine(Canvas canvas, int w, int h) {
        path.reset();
        int shown = Math.min(count, HISTORY);
        float stepX = shown > 1 ? w / (float) (shown - 1) : w;
        float pad = dp(3);
        for (int i = 0; i < shown; i++) {
            float v = values[count - shown + i];
            float x = i * stepX;
            float y = h - pad - v * (h - pad * 2);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(chartColor);
        paint.setPathEffect(new CornerPathEffect(dp(4)));
        canvas.drawPath(path, paint);
        // newest point marker
        if (shown > 0) {
            float v = values[count - 1];
            float x = (shown - 1) * stepX;
            float y = h - pad - v * (h - pad * 2);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accentColor);
            canvas.drawCircle(x, y, dp(2.5f), paint);
        }
        paint.setPathEffect(null);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
