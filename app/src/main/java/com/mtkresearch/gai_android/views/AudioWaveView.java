package com.mtkresearch.gai_android.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class AudioWaveView extends View {
    private Paint paint;
    private float[] amplitudes;
    private static final int BAR_COUNT = 20;
    private static final int BAR_WIDTH = 6;
    private static final int BAR_SPACING = 8;
    private float currentAmplitude = 0f;

    public AudioWaveView(Context context) {
        super(context);
        init();
    }

    public AudioWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0xFF666666); // Gray color
        paint.setStrokeWidth(BAR_WIDTH);
        paint.setStrokeCap(Paint.Cap.ROUND);

        amplitudes = new float[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            amplitudes[i] = 0.1f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int height = getHeight();
        float centerY = height / 2f;
        
        // Shift amplitudes to the left
        System.arraycopy(amplitudes, 1, amplitudes, 0, amplitudes.length - 1);
        amplitudes[amplitudes.length - 1] = currentAmplitude;

        // Draw bars
        float left = BAR_SPACING;
        for (float amplitude : amplitudes) {
            float barHeight = Math.max(4, amplitude * height * 0.8f);
            canvas.drawLine(left, centerY - barHeight/2, 
                          left, centerY + barHeight/2, paint);
            left += BAR_WIDTH + BAR_SPACING;
        }
    }

    public void updateAmplitude(float amplitude) {
        this.currentAmplitude = amplitude;
        invalidate();
    }
}