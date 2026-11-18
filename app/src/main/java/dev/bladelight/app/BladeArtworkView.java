package dev.bladelight.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Decorative signature, never a report of the physical LED state. */
public final class BladeArtworkView extends View {
    private final Bitmap mark;
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint contrast = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private final boolean dark;
    public BladeArtworkView(Context context, boolean dark) {
        super(context);
        this.dark = dark;
        mark = BitmapFactory.decodeResource(context.getResources(), R.drawable.bladelight_reference);
        // Convert the actual photographic luminance to coverage while drawing.
        // Black becomes transparent; the original fine bloom and dust are preserved.
        light.setColorFilter(luminance(255,255,255,dark ? 255f/252f : 255f/210f,dark ? -255f*3/252 : -255f*45/210));
        contrast.setColorFilter(luminance(112,132,155,.70f,-2.1f));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        bounds.set(left, top, left + size, top + size);
        if (!dark) canvas.drawBitmap(mark,null,bounds,contrast);
        canvas.drawBitmap(mark,null,bounds,light);
    }
    private static ColorMatrixColorFilter luminance(int r,int g,int b,float scale,float offset) {
        return new ColorMatrixColorFilter(new ColorMatrix(new float[]{
            0,0,0,0,r, 0,0,0,0,g, 0,0,0,0,b,
            .2126f*scale,.7152f*scale,.0722f*scale,0,offset
        }));
    }
}
