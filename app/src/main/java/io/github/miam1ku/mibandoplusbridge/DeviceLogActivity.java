// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge;

import android.content.ClipData;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.text.format.DateFormat;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import io.github.miam1ku.mibandoplusbridge.data.BandCatalog;
import io.github.miam1ku.mibandoplusbridge.data.BandStateRepository;
import io.github.miam1ku.mibandoplusbridge.data.DeviceLogExporter;
import io.github.miam1ku.mibandoplusbridge.data.LocalPrefs;
import io.github.miam1ku.mibandoplusbridge.data.SessionLog;
import io.github.miam1ku.mibandoplusbridge.integration.DeviceCardProvider;
import io.github.miam1ku.mibandoplusbridge.service.BandLiveService;
import io.github.miam1ku.mibandoplusbridge.ui.BridgeScreen;
import io.github.miam1ku.mibandoplusbridge.ui.Copy;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pulls the current band-session evidence into one shareable archive. */
public final class DeviceLogActivity extends AppCompatActivity {
    private static final long REFRESH_MS = 2_000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OplusBandDeviceLog");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private BridgeScreen screen;
    private TextView deviceTitle;
    private TextView deviceChip;
    private TextView deviceDetail;
    private TextView status;
    private MaterialButton pull;
    private MaterialButton reconnect;
    private MaterialButton share;
    private File lastBundle;
    private boolean pulling;

    private final Runnable refreshTick = this::pollState;
    private final ContentObserver stateObserver = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) {
            refreshDevice();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen = BridgeScreen.attach(this, "拉取设备日志", true);

        android.widget.LinearLayout device = screen.section("设备");
        deviceTitle = screen.title(device, "正在读取手环…");
        deviceChip = screen.chip(device, "检查连接", BridgeScreen.Tone.NEUTRAL);
        deviceDetail = screen.caption(device, "");

        android.widget.LinearLayout explanation = screen.section("日志内容");
        screen.bodyText(explanation,
                "拉取当前会话的连接、鉴权、命令阶段、健康同步和异常线索，并附上手环型号、固件、"
                        + "电量、连接方式及手机环境。");
        screen.caption(explanation,
                "压缩包仅保留型号、固件、电量、连接阶段和同步状态；绑定密钥、令牌、手环 MAC、"
                        + "设备 ID、通知正文及原始协议载荷留在本机。");

        android.widget.LinearLayout operation = screen.section("操作");
        pull = screen.filled(operation, "开始拉取", this::pull);
        reconnect = screen.outlined(operation, "尝试重新连接", this::reconnect);
        share = screen.outlined(operation, "再次分享上次日志", this::shareLast);
        share.setVisibility(android.view.View.GONE);
        status = screen.bodyText(operation, "有已连接的手环后即可开始。");

        getContentResolver().registerContentObserver(DeviceCardProvider.URI, false, stateObserver);
        refreshDevice();
        scheduleRefresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDevice();
    }

    private void refreshDevice() {
        if (isDestroyed()) return;
        UserManager users = getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) {
            deviceTitle.setText("解锁后读取手环");
            screen.setChip(deviceChip, "已锁定", BridgeScreen.Tone.NEUTRAL);
            deviceDetail.setText("设备日志需要先解锁手机。");
            pull.setEnabled(false);
            reconnect.setVisibility(android.view.View.GONE);
            return;
        }
        BandStateRepository repository = new BandStateRepository(this);
        boolean registered;
        try {
            registered = repository.isRegistered();
        } catch (RuntimeException unavailable) {
            registered = false;
        }
        if (!registered) {
            deviceTitle.setText("等待已连接的手环");
            screen.setChip(deviceChip, "等待连接", BridgeScreen.Tone.NEUTRAL);
            deviceDetail.setText("先在首页导入绑定并添加到健康，再回来拉取设备日志。");
            pull.setEnabled(false);
            reconnect.setVisibility(android.view.View.GONE);
            return;
        }

        LocalPrefs device = LocalPrefs.open(this, "band-state");
        String name = BandCatalog.displayName(
                device.getString("modelId", ""), device.getString("name", ""));
        boolean connected = device.getBoolean("connected", false);
        deviceTitle.setText(name.isBlank() ? "已登记手环" : name);
        screen.setChip(deviceChip, connected ? "已连接" : "等待连接",
                connected ? BridgeScreen.Tone.POSITIVE : BridgeScreen.Tone.NEUTRAL);
        StringBuilder detail = new StringBuilder();
        String model = device.getString("modelId", "");
        String firmware = device.getString("verifiedFirmware",
                device.getString("firmware", ""));
        if (!model.isBlank()) detail.append("型号 ").append(model);
        if (!firmware.isBlank()) {
            if (detail.length() > 0) detail.append("\n");
            detail.append("固件 ").append(firmware);
        }
        int battery = device.getInt("battery", -1);
        if (battery >= 0 && battery <= 100) {
            if (detail.length() > 0) detail.append("\n");
            detail.append("电量 ").append(battery).append('%');
        }
        long lastSync = device.getLong("lastSyncAtMs", 0);
        if (lastSync > 0) {
            if (detail.length() > 0) detail.append("\n");
            detail.append("上次同步 ")
                    .append(DateFormat.getMediumDateFormat(this).format(new java.util.Date(lastSync)))
                    .append(' ')
                    .append(DateFormat.getTimeFormat(this).format(new java.util.Date(lastSync)));
        }
        deviceDetail.setText(detail.length() == 0 ? "尚无验证过的设备信息。" : detail.toString());
        pull.setEnabled(connected && !pulling);
        reconnect.setVisibility(connected ? android.view.View.GONE : android.view.View.VISIBLE);
        if (!connected && !pulling && lastBundle == null) {
            String reason = Copy.offlineReason(readLiveStatus());
            status.setText(reason.isBlank()
                    ? "手环连接建立后即可拉取。可点「尝试重新连接」，或在首页点「立即同步」。"
                    : reason);
        }
    }

    private void scheduleRefresh() {
        main.removeCallbacks(refreshTick);
        main.postDelayed(refreshTick, REFRESH_MS);
    }

    private void pollState() {
        if (isDestroyed()) return;
        refreshDevice();
        scheduleRefresh();
    }

    private void reconnect() {
        String result = BandLiveService.requestSync(this);
        String message = switch (result) {
            case "ACCEPTED" -> "正在重新连接手环，连接后即可开始拉取。";
            case "DEVICE_NOT_REGISTERED" -> "先连接手环，再回来拉取设备日志。";
            case "NATIVE_OWNERSHIP_REQUIRED" -> "当前由官方应用管理。请在首页点「立即同步」重新接管。";
            case "OPEN_CONFIG_REQUIRED" -> "请打开小米手环桥接首页完成配置后再试。";
            default -> "回首页查看手环状态，再重新发起连接。";
        };
        status.setText(message);
        refreshDevice();
    }

    private void pull() {
        if (pulling) return;
        LocalPrefs device = LocalPrefs.open(this, "band-state");
        if (!device.getBoolean("registered", false) || !device.getBoolean("connected", false)) {
            status.setText("先让手环连上，再开始拉取。");
            refreshDevice();
            return;
        }
        pulling = true;
        pull.setEnabled(false);
        pull.setText("正在拉取…");
        reconnect.setVisibility(android.view.View.GONE);
        status.setText("正在整理当前设备会话，随后会打开系统分享…");
        SessionLog.start(this);
        String sync = BandLiveService.requestSync(this);
        SessionLog.line(this, "DEVICE_LOG_PULL requested sync=" + sync);
        worker.execute(() -> {
            try {
                // Give an accepted sync request a moment to append its first visible stages.
                Thread.sleep("ACCEPTED".equals(sync) ? 600 : 100);
                File bundle = DeviceLogExporter.export(this);
                long bytes = bundle.length();
                SessionLog.line(this, "DEVICE_LOG_PULL ready bytes=" + bytes);
                main.post(() -> {
                    if (isDestroyed()) return;
                    pulling = false;
                    lastBundle = bundle;
                    pull.setText("重新拉取");
                    share.setVisibility(android.view.View.VISIBLE);
                    status.setText("已生成 " + bundle.getName() + "（" + size(bytes) + "）。");
                    refreshDevice();
                    share(bundle);
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (isDestroyed()) return;
                    pulling = false;
                    pull.setText("重试拉取");
                    status.setText("日志生成中断：" + safeFailure(failure)
                            + "。确认手环仍连接后再试一次。");
                    refreshDevice();
                });
            }
        });
    }

    private void shareLast() {
        if (lastBundle != null && lastBundle.isFile()) share(lastBundle);
    }

    private void share(File bundle) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    "io.github.miam1ku.mibandoplusbridge.debuglog", bundle);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "小米手环桥接设备日志");
            send.setClipData(ClipData.newRawUri("device-log", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "分享设备日志"));
        } catch (RuntimeException failure) {
            status.setText("日志已保存在应用缓存，可稍后点「再次分享上次日志」。");
        }
    }

    private String readLiveStatus() {
        File file = new File(getNoBackupFilesDir(), "live-status.txt");
        if (!file.isFile() || file.length() <= 0 || file.length() > 2_048) return "";
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Exception unavailable) {
            return "";
        }
    }

    private static String safeFailure(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : SessionLog.redact(message);
    }

    private static String size(long bytes) {
        if (bytes < 1_024) return bytes + " B";
        if (bytes < 1_024 * 1_024) return (bytes + 1_023) / 1_024 + " KB";
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override protected void onDestroy() {
        getContentResolver().unregisterContentObserver(stateObserver);
        main.removeCallbacks(refreshTick);
        worker.shutdown();
        super.onDestroy();
    }
}
