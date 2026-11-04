package com.vinz.mousefollow;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status;
    Switch sw, swTouch;
    SharedPreferences sp;

    void needService() {
        if (FollowService.instance == null) {
            Toast.makeText(this, "Aktifkan dulu layanan Mouse Follow di Aksesibilitas", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("p", 0);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 96, 48, 48);
        l.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView t = new TextView(this);
        t.setText("Mouse Follow");
        t.setTextSize(26);
        l.addView(t);

        status = new TextView(this);
        status.setPadding(0, 24, 0, 24);
        l.addView(status);

        sw = new Switch(this);
        sw.setText("Lingkaran ikut mouse  ");
        sw.setTextSize(18);
        sw.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean("on", on).apply();
            if (on) needService();
            FollowService.refresh();
        });
        l.addView(sw);

        swTouch = new Switch(this);
        swTouch.setText("Lingkaran saat layar disentuh (maks 5 jari)  ");
        swTouch.setTextSize(18);
        swTouch.setPadding(0, 32, 0, 0);
        swTouch.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean("touch", on).apply();
            if (on) {
                needService();
                if (Build.VERSION.SDK_INT < 34)
                    Toast.makeText(this, "Fitur sentuhan butuh Android 14 ke atas", Toast.LENGTH_LONG).show();
            }
            FollowService.refresh();
        });
        l.addView(swTouch);

        TextView s = new TextView(this);
        s.setText("\nUkuran lingkaran");
        l.addView(s);
        SeekBar sb = new SeekBar(this);
        sb.setMax(60);
        sb.setProgress(sp.getInt("size", 24));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar k, int p, boolean u) {
                sp.edit().putInt("size", Math.max(8, p)).apply();
                FollowService.refresh();
            }
            public void onStartTrackingTouch(SeekBar k) {}
            public void onStopTrackingTouch(SeekBar k) {}
        });
        l.addView(sb, new LinearLayout.LayoutParams(-1, -2));

        Button open = new Button(this);
        open.setText("Buka Pengaturan Aksesibilitas");
        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        l.addView(open);

        setContentView(l);
    }

    @Override protected void onResume() {
        super.onResume();
        status.setText(FollowService.instance != null
            ? "Layanan aksesibilitas: AKTIF"
            : "Layanan aksesibilitas: BELUM aktif (Pengaturan > Aksesibilitas > Mouse Follow)");
        sw.setChecked(sp.getBoolean("on", false));
        swTouch.setChecked(sp.getBoolean("touch", false));
    }
}
