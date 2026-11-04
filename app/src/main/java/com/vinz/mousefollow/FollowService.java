package com.vinz.mousefollow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class FollowService extends AccessibilityService {
    static FollowService instance;
    WindowManager wm;
    Follower view;
    WindowManager.LayoutParams lp;
    Handler h = new Handler(Looper.getMainLooper());
    boolean passing = false;
    Runnable doneRun = this::done;

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        apply();
    }

    @Override public boolean onUnbind(Intent i) {
        remove();
        instance = null;
        return super.onUnbind(i);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    static void refresh() { if (instance != null) instance.apply(); }

    void apply() {
        boolean on = getSharedPreferences("p", 0).getBoolean("on", false);
        if (on) add(); else remove();
    }

    void add() {
        if (view != null) { view.invalidate(); return; }
        lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        if (Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        view = new Follower(this);
        wm.addView(view, lp);
    }

    void remove() {
        h.removeCallbacks(doneRun);
        passing = false;
        if (view != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
            view = null;
        }
    }

    void setTouchable(boolean t) {
        if (view == null) return;
        if (t) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
    }

    // Teruskan gesture ke aplikasi di belakang overlay
    void pass(Path path, long dur) {
        if (passing || view == null) return;
        passing = true;
        setTouchable(false);
        h.postDelayed(() -> {
            GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, dur)).build();
            boolean ok = dispatchGesture(g, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) { done(); }
                @Override public void onCancelled(GestureDescription d) { done(); }
            }, null);
            if (!ok) done();
        }, 30);
        h.postDelayed(doneRun, dur + 1500);
    }

    void done() {
        h.removeCallbacks(doneRun);
        if (!passing) return;
        passing = false;
        setTouchable(true);
    }

    class Follower extends View {
        float x = -1000, y = -1000, x0, y0;
        long t0;
        boolean secondary;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] loc = new int[2];

        Follower(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            getLocationOnScreen(loc);
            float d = getResources().getDisplayMetrics().density;
            float r = getSharedPreferences("p", 0).getInt("size", 24) * d;
            float cx = x - loc[0], cy = y - loc[1];
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x552196F3);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setColor(0xFF2196F3);
            c.drawCircle(cx, cy, r, p);
        }

        @Override public boolean onGenericMotionEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER) {
                x = e.getRawX(); y = e.getRawY();
                invalidate();
                return true;
            }
            if (a == MotionEvent.ACTION_SCROLL) {
                x = e.getRawX(); y = e.getRawY();
                invalidate();
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                float hs = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
                DisplayMetrics m = getResources().getDisplayMetrics();
                Path path = new Path();
                path.moveTo(x, y);
                float ex = Math.max(0, Math.min(m.widthPixels, x - hs * 250));
                float ey = Math.max(0, Math.min(m.heightPixels, y + v * 250));
                path.lineTo(ex, ey);
                pass(path, 150);
                return true;
            }
            return super.onGenericMotionEvent(e);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            x = e.getRawX(); y = e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = x; y0 = y; t0 = e.getEventTime();
                    secondary = (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    invalidate();
                    if (secondary) { performGlobalAction(GLOBAL_ACTION_BACK); return true; }
                    Path path = new Path();
                    path.moveTo(x0, y0);
                    float dist = (float) Math.hypot(x - x0, y - y0);
                    if (dist > 20) path.lineTo(x, y);
                    long dur = Math.max(50, Math.min(5000, e.getEventTime() - t0));
                    pass(path, dur);
                    return true;
            }
            return true;
        }
    }
}
