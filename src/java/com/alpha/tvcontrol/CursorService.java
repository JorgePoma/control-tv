package com.alpha.tvcontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * On-screen mouse pointer for apps that are awkward with arrow keys. The phone page holds a
 * direction down and the pointer glides (accelerating) until released; OK taps at the pointer.
 */
public class CursorService extends AccessibilityService {
    private static volatile CursorService instance;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int SIZE = 44;
    private static final long FRAME_MS = 16;

    private WindowManager wm;
    private Pointer view;
    private WindowManager.LayoutParams lp;
    private boolean shown;
    private float x, y;
    private int dirX, dirY;
    private long heldSince;
    private int screenW, screenH;

    private final Runnable tick = this::onTick;

    private void onTick() {
        if (!shown || (dirX == 0 && dirY == 0)) return;
        float held = (System.currentTimeMillis() - heldSince) / 1000f;
        float speed = Math.min(4f + held * 12f, 22f); // px per frame, ramps up while held
        moveBy(dirX * speed, dirY * speed);
        MAIN.postDelayed(tick, FRAME_MS);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        screenW = size.x;
        screenH = size.y;
        x = screenW / 2f;
        y = screenH / 2f;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        hide();
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    static boolean available() {
        return instance != null;
    }

    static boolean isShown() {
        CursorService s = instance;
        return s != null && s.shown;
    }

    static boolean setShown(boolean on) {
        return post(s -> { if (on) s.show(); else s.hide(); });
    }

    /** dx/dy in -1..1: start gliding that way; 0,0 stops. */
    static boolean steer(int dx, int dy) {
        return post(s -> {
            boolean wasIdle = s.dirX == 0 && s.dirY == 0;
            s.dirX = dx;
            s.dirY = dy;
            if (dx == 0 && dy == 0) return;
            if (wasIdle) s.heldSince = System.currentTimeMillis();
            if (wasIdle) {
                s.moveBy(dx * 10f, dy * 10f);
                MAIN.removeCallbacks(s.tick);
                MAIN.postDelayed(s.tick, 120); // short pause so a quick tap nudges just a little
            }
        });
    }

    static boolean click() {
        return post(CursorService::tap);
    }

    private void show() {
        if (shown) return;
        x = screenW / 2f;
        y = screenH / 2f;
        view = new Pointer(this);
        lp = new WindowManager.LayoutParams(SIZE, SIZE,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        wm.addView(view, lp);
        shown = true;
    }

    private void hide() {
        dirX = dirY = 0;
        MAIN.removeCallbacks(tick);
        if (!shown) return;
        try { wm.removeView(view); } catch (RuntimeException ignored) { }
        view = null;
        shown = false;
    }

    private void moveBy(float dx, float dy) {
        x = Math.max(0, Math.min(screenW - 1, x + dx));
        y = Math.max(0, Math.min(screenH - 1, y + dy));
        if (!shown) return;
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        wm.updateViewLayout(view, lp);
    }

    private void tap() {
        if (!shown) return;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                .build();
        dispatchGesture(g, null, null);
        if (view != null) view.pulse();
    }

    private interface Action { void apply(CursorService s); }

    private static boolean post(Action a) {
        CursorService s = instance;
        if (s == null) return false;
        MAIN.post(() -> a.apply(s));
        return true;
    }

    /** Classic arrow pointer, white with a dark outline so it reads on any background. */
    private static final class Pointer extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private boolean pressed;

        Pointer(Context c) {
            super(c);
            fill.setColor(Color.WHITE);
            edge.setColor(0xFF111214);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(3f);
            edge.setStrokeJoin(Paint.Join.ROUND);
            arrow.moveTo(2, 2);
            arrow.lineTo(2, 34);
            arrow.lineTo(11, 26);
            arrow.lineTo(17, 40);
            arrow.lineTo(23, 37);
            arrow.lineTo(17, 24);
            arrow.lineTo(29, 24);
            arrow.close();
        }

        void pulse() {
            pressed = true;
            invalidate();
            postDelayed(() -> { pressed = false; invalidate(); }, 150);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            fill.setColor(pressed ? 0xFF4F8CFF : Color.WHITE);
            canvas.drawPath(arrow, fill);
            canvas.drawPath(arrow, edge);
        }
    }
}
