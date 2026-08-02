package com.example.batteryinfo;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private LinearLayout container;
    private LinearLayout batterySection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        container = findViewById(R.id.container);

        // Static sections
        addSection("设备信息", getDeviceInfo());
        addSection("系统信息", getSystemInfo());
        addSection("屏幕信息", getDisplayInfo());
        addSection("CPU 信息", getCpuInfo());
        addSection("内存信息", getMemoryInfo());
        addSection("存储信息", getStorageInfo());
        addSection("传感器", getSensorInfo());

        // Battery section is dynamic (updates on charge/discharge)
        batterySection = addSection("电池信息");
        Intent sticky = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            updateBattery(sticky);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
    }

    // ------------------------------------------------------------------
    // Battery (live)
    // ------------------------------------------------------------------

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    private void updateBattery(Intent intent) {
        if (batterySection == null || intent == null) return;
        batterySection.removeAllViews();

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1); // mV
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1); // 0.1 °C
        String tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        // Design / full capacity in microAh (API 21+)
        long chargeFullMicro = -1;
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) {
            chargeFullMicro = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_FULL);
        }

        addRow(batterySection, "当前电量", pct >= 0 ? pct + " %" : "未知");
        addRow(batterySection, "电池容量(满电)", chargeFullMicro > 0
                ? String.format(Locale.US, "%.0f mAh", chargeFullMicro / 1000.0)
                : "未知 (部分设备不暴露)");
        addRow(batterySection, "健康状态", healthToString(health));
        addRow(batterySection, "充电状态", statusToString(status));
        addRow(batterySection, "充电方式", pluggedToString(plugged));
        addRow(batterySection, "电压", voltage > 0 ? voltage + " mV (" + (voltage / 1000f) + " V)" : "未知");
        addRow(batterySection, "温度", temp > 0 ? String.format(Locale.US, "%.1f °C", temp / 10f) : "未知");
        addRow(batterySection, "电池技术", tech != null ? tech : "未知");
    }

    private static String healthToString(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "已损坏";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "电压过高";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "未知故障";
            case BatteryManager.BATTERY_HEALTH_COLD: return "过冷";
            default: return "未知";
        }
    }

    private static String statusToString(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
            case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            default: return "未知";
        }
    }

    private static String pluggedToString(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "交流电 (AC)";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "无线充电";
            default: return "未连接电源";
        }
    }

    // ------------------------------------------------------------------
    // Device
    // ------------------------------------------------------------------

    private List<Pair<String, String>> getDeviceInfo() {
        List<Pair<String, String>> list = new ArrayList<>();
        list.add(p("品牌", Build.BRAND));
        list.add(p("制造商", Build.MANUFACTURER));
        list.add(p("型号", Build.MODEL));
        list.add(p("设备名", Build.DEVICE));
        list.add(p("主板", Build.BOARD));
        list.add(p("硬件", Build.HARDWARE));
        list.add(p("产品", Build.PRODUCT));
        list.add(p("显示屏", Build.DISPLAY));
        list.add(p("Bootloader", Build.BOOTLOADER));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                list.add(p("序列号", Build.getSerial()));
            } catch (SecurityException e) {
                list.add(p("序列号", "需要权限"));
            }
        } else {
            list.add(p("序列号", Build.SERIAL));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // System
    // ------------------------------------------------------------------

    private List<Pair<String, String>> getSystemInfo() {
        List<Pair<String, String>> list = new ArrayList<>();
        list.add(p("Android 版本", Build.VERSION.RELEASE));
        list.add(p("API 级别", String.valueOf(Build.VERSION.SDK_INT)));
        list.add(p("版本代号", Build.VERSION.CODENAME));
        list.add(p("Build ID", Build.ID));
        list.add(p("Build 类型", Build.TYPE));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            list.add(p("安全补丁", String.valueOf(Build.VERSION.SECURITY_PATCH)));
        } else {
            list.add(p("安全补丁", "不适用"));
        }
        list.add(p("内核版本", readKernelVersion()));
        list.add(p("系统语言", Locale.getDefault().getDisplayLanguage()));
        list.add(p("时区", TimeZone.getDefault().getID()));
        list.add(p("指纹", Build.FINGERPRINT));
        return list;
    }

    private String readKernelVersion() {
        String v = readFileFirstLine("/proc/version");
        if (v != null && !v.isEmpty()) return v.trim();
        return System.getProperty("os.version", "未知");
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private List<Pair<String, String>> getDisplayInfo() {
        List<Pair<String, String>> list = new ArrayList<>();
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = getWindowManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            wm.getDefaultDisplay().getRealMetrics(dm);
        } else {
            wm.getDefaultDisplay().getMetrics(dm);
        }
        int w = dm.widthPixels;
        int h = dm.heightPixels;
        list.add(p("分辨率", w + " × " + h + " px"));
        list.add(p("像素密度 (density)", String.valueOf(dm.density)));
        list.add(p("DPI", String.valueOf(dm.densityDpi)));
        double inches = Math.sqrt(w * w + h * h) / dm.densityDpi;
        list.add(p("屏幕尺寸(约)", String.format(Locale.US, "%.1f 英寸", inches)));
        int orientation = getResources().getConfiguration().orientation;
        list.add(p("当前方向", orientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏"));
        return list;
    }

    // ------------------------------------------------------------------
    // CPU
    // ------------------------------------------------------------------

    private List<Pair<String, String>> getCpuInfo() {
        List<Pair<String, String>> list = new ArrayList<>();
        int cores = Runtime.getRuntime().availableProcessors();
        list.add(p("CPU 核心数", String.valueOf(cores)));

        StringBuilder hardware = new StringBuilder();
        String modelName = null;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String low = line.toLowerCase(Locale.US);
                if (low.startsWith("hardware")) {
                    hardware.append(line.split(":", 2)[1].trim()).append(" ");
                } else if (low.startsWith("model name") && modelName == null) {
                    modelName = line.split(":", 2)[1].trim();
                }
            }
        } catch (IOException ignored) {
            // ignore
        }
        if (hardware.length() > 0) list.add(p("硬件", hardware.toString().trim()));
        if (modelName != null) list.add(p("型号名称", modelName));
        list.add(p("指令集 (ABI)", getAbi()));
        return list;
    }

    private String getAbi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            StringBuilder sb = new StringBuilder();
            for (String abi : Build.SUPPORTED_ABIS) {
                sb.append(abi).append(" ");
            }
            return sb.toString().trim();
        }
        return Build.CPU_ABI;
    }

    // ------------------------------------------------------------------
    // Memory
    // ------------------------------------------------------------------

    private List<Pair<String, String>> getMemoryInfo() {
        List<Pair<String, String>> list = new ArrayList<>();
        String memTotal = readFileFirstLine("/proc/meminfo");
        if (memTotal != null && memTotal.toLowerCase(Locale.US).startsWith("memtotal")) {
            String[] parts = memTotal.trim().split("\\s+");
            if (parts.length >= 2) {
                try {
                    long kb = Long.parseLong(parts[1]);
                    list.add(p("运行内存 (RAM)",
                            String.format(Locale.US, "%.2f GB (%.0f MB)", kb / 1048576.0, kb / 1024.0)));
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        list.add(p("可用内存", String.format(Locale.US, "%.2f GB", mi.availMem / 1073741824.0)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            list.add(p("低内存状态", mi.lowMemory ? "是" : "否"));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    private List<Pair<String, String>> getStorageInfo() {
        List<Pair<String, String>> list = new ArrayList<>();
        list.add(p("内部存储", formatStorage(Environment.getDataDirectory())));
        try {
            File external = Environment.getExternalFilesDir(null);
            if (external != null) {
                list.add(p("外部存储", formatStorage(external)));
            } else {
                list.add(p("外部存储", "不可用"));
            }
        } catch (Exception e) {
            list.add(p("外部存储", "不可用"));
        }
        return list;
    }

    private String formatStorage(File path) {
        try {
            StatFs stat = new StatFs(path.getPath());
            long total = stat.getTotalBytes();
            long avail = stat.getAvailableBytes();
            return String.format(Locale.US, "总 %.2f GB / 可用 %.2f GB", total / 1e9, avail / 1e9);
        } catch (Exception e) {
            return "未知";
        }
    }

    // ------------------------------------------------------------------
    // Sensors
    // ------------------------------------------------------------------

    private List<Pair<String, String>> getSensorInfo() {
        List<Pair<String, String>> list = new ArrayList<>();
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        list.add(p("传感器数量", String.valueOf(sensors.size())));
        for (Sensor s : sensors) {
            list.add(p(s.getName(), s.getVendor()));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------

    private Pair<String, String> p(String k, String v) {
        return new Pair<>(k, v == null ? "未知" : v);
    }

    private LinearLayout addSection(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(15);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(ContextCompat.getColor(this, R.color.purple_500));
        header.setPadding(4, 20, 4, 6);
        container.addView(header);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(16, 6, 16, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6);
        card.setLayoutParams(lp);
        container.addView(card);
        return card;
    }

    private void addSection(String title, List<Pair<String, String>> rows) {
        LinearLayout card = addSection(title);
        for (Pair<String, String> row : rows) {
            addRow(card, row.first, row.second);
        }
    }

    private void addRow(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 9, 0, 9);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        l.setTextSize(14);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        v.setTextSize(14);
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.END);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

        row.addView(l);
        row.addView(v);
        card.addView(row);

        View div = new View(this);
        div.setBackgroundColor(0xFFE3E3E8);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        card.addView(div);
    }

    private static String readFileFirstLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
