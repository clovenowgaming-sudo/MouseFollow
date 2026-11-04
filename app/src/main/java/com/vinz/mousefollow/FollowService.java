package com.vinz.mousefollow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class FollowService extends AccessibilityService {
    static FollowService instance;
    WindowManager wm;
    Follower view;
    TouchView tv;
    WindowManager.LayoutParams lp;
    Handler h = new Handler(Looper.getMainLooper());
    boolean passing = false, calib = false;
    Runnable doneRun = this::done;
    float cx = -1, cy = -1;
    int offX = 0, offY = 0, winSize = 0;

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 34) {
            AccessibilityServiceInfo i = getServiceInfo();
            if (i != null) {
                i.setMotionEventSources(InputDevice.SOURCE_TOUCHSCREEN);
                setServiceInfo(i);
            }
        }
        apply();
    }

    @Override public boolean onUnbind(Intent i) {
        remove();
        instance = null;
        return super.onUnbind(i);
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        calib = true;
        if (view != null) moveTo(cx, cy);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    // Android 14+: terima sentuhan layar tanpa memblokirnya
    @Override public void onMotionEvent(MotionEvent e) {
        if (tv == null) return;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { tv.set(null, null, 0); return; }
        int skip = (a == MotionEvent.ACTION_POINTER_UP) ? e.getActionIndex() : -1;
        float[] xs = new float[5], ys = new float[5];
        int n = 0;
        for (int i = 0; i < e.getPointerCount() && n < 5; i++) {
            if (i == skip) continue;
            xs[n] = e.getRawX(i);
            ys[n] = e.getRawY(i);
            n++;
        }
        tv.set(xs, ys, n);
    }

    static void refresh() { if (instance != null) instance.apply(); }

    void apply() {
        remove();
        if (getSharedPreferences("p", 0).getBoolean("on", false)) add();
        if (getSharedPreferences("p", 0).getBoolean("touch", false)) addTouch();
    }

    void addTouch() {
        WindowManager.LayoutParams t = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        t.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28)
            t.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        tv = new TouchView(this);
        wm.addView(tv, t);
    }

    void add() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        if (cx < 0) { cx = m.widthPixels / 2f; cy = m.heightPixels / 2f; }
        int r = getSharedPreferences("p", 0).getInt("size", 24);
        winSize = (int) ((2 * r + 48) * m.density);
        lp = new WindowManager.LayoutParams(winSize, winSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        lp.x = (int) (cx - winSize / 2f - offX);
        lp.y = (int) (cy - winSize / 2f - offY);
        view = new Follower(this, r * m.density);
        calib = true;
        view.addOnLayoutChangeListener((v, l, t, rr, b, ol, ot, or, ob) -> {
            if (!calib) return;
            calib = false;
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            int nx = loc[0] - lp.x, ny = loc[1] - lp.y;
            if (nx != offX || ny != offY) { offX = nx; offY = ny; moveTo(cx, cy); }
        });
        wm.addView(view, lp);
    }

    void remove() {
        h.removeCallbacks(doneRun);
        passing = false;
        if (view != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
            view = null;
        }
        if (tv != null) {
            try { wm.removeView(tv); } catch (Exception ignored) {}
            tv = null;
        }
    }

    void moveTo(float x, float y) {
        if (view == null) return;
        DisplayMetrics m = getResources().getDisplayMetrics();
        cx = Math.max(0, Math.min(m.widthPixels, x));
        cy = Math.max(0, Math.min(m.heightPixels, y));
        lp.x = (int) (cx - winSize / 2f - offX);
        lp.y = (int) (cy - winSize / 2f - offY);
        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
    }

    void setTouchable(boolean t) {
        if (view == null) return;
        if (t) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
    }

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

    // Lingkaran di tiap jari (maks 5), tidak memblokir sentuhan
    class TouchView extends View {
        float[] xs = new float[5], ys = new float[5];
        int n = 0;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] loc = new int[2];

        TouchView(Context c) { super(c); }

        void set(float[] x, float[] y, int count) {
            if (x != null) { xs = x; ys = y; }
            n = count;
            postInvalidateOnAnimation();
        }

        @Override protected void onDraw(Canvas c) {
            if (n == 0) return;
            getLocationOnScreen(loc);
            float d = getResources().getDisplayMetrics().density;
            float r = getSharedPreferences("p", 0).getInt("size", 24) * d;
            for (int i = 0; i < n; i++) {
                float x = xs[i] - loc[0], y = ys[i] - loc[1];
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x55FF9800);
                c.drawCircle(x, y, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * d);
                p.setColor(0xFFFF9800);
                c.drawCircle(x, y, r, p);
            }
        }
    }

    class Follower extends View {
        final float r;
        final float slop;
        float downX, downY, startCx, startCy;
        long t0;
        boolean secondary, drag, mouse;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Follower(Context c, float radius) {
            super(c);
            r = radius;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float x = getWidth() / 2f, y = getHeight() / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x552196F3);
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setColor(0xFF2196F3);
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, 3 * d, p);
        }

        @Override public boolean onGenericMotionEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER) {
                moveTo(e.getRawX(), e.getRawY());
                return true;
            }
            if (a == MotionEvent.ACTION_SCROLL) {
                moveTo(e.getRawX(), e.getRawY());
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                float hs = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
                DisplayMetrics m = getResources().getDisplayMetrics();
                Path path = new Path();
                path.moveTo(cx, cy);
                path.lineTo(Math.max(0, Math.min(m.widthPixels, cx - hs * 250)),
                            Math.max(0, Math.min(m.heightPixels, cy + v * 250)));
                pass(path, 150);
                return true;
            }
            return super.onGenericMotionEvent(e);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mouse = e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
                    downX = e.getRawX(); downY = e.getRawY();
                    startCx = cx; startCy = cy;
                    t0 = e.getEventTime();
                    drag = false;
                    secondary = (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mouse) {
                        moveTo(e.getRawX(), e.getRawY());
                    } else {
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!drag && Math.hypot(dx, dy) > slop) drag = true;
                        if (drag) moveTo(startCx + dx, startCy + dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    long dur = Math.max(50, Math.min(5000, e.getEventTime() - t0));
                    if (mouse) {
                        if (secondary) { performGlobalAction(GLOBAL_ACTION_BACK); return true; }
                        Path path = new Path();
                        path.moveTo(downX, downY);
                        if (Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > 20)
                            path.lineTo(e.getRawX(), e.getRawY());
                        pass(path, dur);
                    } else if (!drag) {
                        Path path = new Path();
                        path.moveTo(cx, cy);
                        pass(path, dur);
                    }
                    return true;
            }
            return true;
        }
    }
}
