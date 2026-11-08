package de.pixdel.ssarecomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

/**
 * Draws the on-screen controls over the game. The touches themselves go to the game (the
 * native touch driver handles them); this view only shows the state the driver reports, polled
 * once per display frame. Drawn by Android, it costs the game's renderer nothing (an ImGui
 * overlay made every game frame wait for a UI-thread repaint).
 */
public class TouchOverlayView extends View implements Choreographer.FrameCallback {
    private static native void nativeSetViewSize(int width, int height);
    private static native int nativeGetState(float[] out);

    // Order matches the native controls: A B X Y LB RB LT RT Back Start Menu Portal Eye.
    private static final String[] LABELS = {
        "A", "B", "X", "Y", "LB", "RB", "LT", "RT", "Back", "Start", "Menu", "Portal", ""
    };
    private static final int[] COLORS = {
        Color.rgb(60, 180, 75), Color.rgb(220, 60, 60), Color.rgb(60, 120, 230),
        Color.rgb(235, 190, 40), Color.rgb(200, 200, 200), Color.rgb(200, 200, 200),
        Color.rgb(200, 200, 200), Color.rgb(200, 200, 200), Color.rgb(200, 200, 200),
        Color.rgb(200, 200, 200), Color.rgb(170, 140, 255), Color.rgb(255, 160, 60),
        Color.rgb(200, 200, 200)
    };
    private static final int EYE = 12;

    private final float[] state = new float[8 + 3 * 16];
    private final float[] last = new float[state.length];
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean nativeReady = false;

    public TouchOverlayView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(4f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        try {
            nativeSetViewSize(w, h);
            nativeReady = true;
        } catch (UnsatisfiedLinkError e) {
            nativeReady = false;  // the game library isn't loaded yet; retried each frame
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!nativeReady && getWidth() > 0) {
            onSizeChanged(getWidth(), getHeight(), 0, 0);
        }
        if (nativeReady) {
            try {
                nativeGetState(state);
                if (!java.util.Arrays.equals(state, last)) {
                    System.arraycopy(state, 0, last, 0, state.length);
                    invalidate();
                }
            } catch (UnsatisfiedLinkError e) {
                nativeReady = false;
            }
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    // Never consume touches: they belong to the game (SDL's surface below this view).
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    private int alpha(int color, float a) {
        float opacity = last[2] > 0 ? last[2] : 0.45f;
        int al = Math.max(0, Math.min(255, Math.round(a * opacity * 255f)));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int flags = (int) last[0];
        if ((flags & 1) == 0) return;
        int held = (int) last[1];

        // The eye: shows / hides the controls.
        float ex = last[8 + 3 * EYE], ey = last[9 + 3 * EYE], er = last[10 + 3 * EYE];
        boolean hidden = (flags & 4) != 0;
        stroke.setColor(alpha(Color.WHITE, hidden ? 0.8f : 1f));
        stroke.setStrokeWidth(4f);
        canvas.drawCircle(ex, ey, er, stroke);
        rect.set(ex - er * 0.75f, ey - er * 0.45f, ex + er * 0.75f, ey + er * 0.45f);
        canvas.drawOval(rect, stroke);
        fill.setColor(alpha(Color.WHITE, 1f));
        canvas.drawCircle(ex, ey, er * 0.22f, fill);
        if (hidden) {
            canvas.drawLine(ex - er * 0.7f, ey - er * 0.7f, ex + er * 0.7f, ey + er * 0.7f, stroke);
        }
        if ((flags & 2) == 0) return;
        // touch_controls = menu: only the Menu and Portal buttons (the rest is on a controller).
        boolean menuOnly = (flags & 16) != 0;

        for (int c = 0; c < EYE; c++) {
            if (menuOnly && c != 10 && c != 11) continue;
            float x = last[8 + 3 * c], y = last[9 + 3 * c], r = last[10 + 3 * c];
            boolean isHeld = (held & (1 << c)) != 0;
            boolean face = c <= 3;
            fill.setColor(alpha(face ? COLORS[c] : Color.rgb(40, 40, 40), isHeld ? 0.95f : 0.55f));
            stroke.setColor(alpha(Color.WHITE, isHeld ? 1f : 0.7f));
            stroke.setStrokeWidth(3f);
            if (c >= 4 && c <= 7) {
                rect.set(x - r * 1.2f, y - r * 0.75f, x + r * 1.2f, y + r * 0.75f);
                canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, fill);
                canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, stroke);
            } else {
                canvas.drawCircle(x, y, r, fill);
                canvas.drawCircle(x, y, r, stroke);
            }
            float size = face ? r * 1.1f : r * (LABELS[c].length() > 2 ? 0.62f : 0.85f);
            text.setTextSize(size);
            text.setColor(alpha(face ? Color.WHITE : COLORS[c], 1f));
            float baseline = y - (text.descent() + text.ascent()) / 2f;
            canvas.drawText(LABELS[c], x, baseline, text);
        }

        if (menuOnly) return;
        // The stick: a ring where the thumb went down and the knob under it; a hint while unused.
        boolean stickActive = (flags & 8) != 0;
        float sr = last[7];
        float bx, by, kx, ky;
        if (stickActive) {
            bx = last[3]; by = last[4]; kx = last[5]; ky = last[6];
            float dx = kx - bx, dy = ky - by;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > sr && len > 0) {
                kx = bx + dx / len * sr;
                ky = by + dy / len * sr;
            }
        } else {
            bx = kx = 2.0f * sr;
            by = ky = getHeight() - 2.0f * sr;
        }
        fill.setColor(alpha(Color.rgb(40, 40, 40), stickActive ? 0.5f : 0.3f));
        canvas.drawCircle(bx, by, sr, fill);
        stroke.setColor(alpha(Color.WHITE, 0.6f));
        canvas.drawCircle(bx, by, sr, stroke);
        fill.setColor(alpha(Color.rgb(220, 220, 220), stickActive ? 0.9f : 0.5f));
        canvas.drawCircle(kx, ky, sr * 0.45f, fill);
    }
}
