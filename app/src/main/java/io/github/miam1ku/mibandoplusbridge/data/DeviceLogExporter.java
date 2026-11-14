// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.data;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/**
 * Builds a small, shareable device-session bundle from the same bounded logs used by the
 * connection service. Secrets, raw protocol payloads, notification content, and device IDs are
 * deliberately not copied into the archive.
 */
public final class DeviceLogExporter {
    public static final String FORMAT = "miband-oplusbridge-device-log-v1";
    private static final long LIVE_STATUS_LIMIT = 2_048;
    private static final long SESSION_LOG_LIMIT = 256 * 1024;
    private static final int KEPT_ARCHIVES = 8;
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());
    private static final String README = """
            小米手环桥接 · 设备会话日志

            该压缩包由“高级设置 → 调试 → 拉取设备日志”生成。

            文件说明：
            - manifest.txt：导出格式、时间和文件索引
            - device-state.txt：手环型号、固件、电量和最近连接/同步状态
            - transport.txt：当前连接方式和协议分支
            - environment.txt：桥接版本、Android 版本和手机型号
            - live-status.txt：连接服务最后一次写入的状态
            - session-log.txt：桥接保留的连接、鉴权、命令阶段、健康同步和错误事件

            隐私说明：
            - 压缩包仅保留连接排障所需字段；绑定密钥、登录令牌、手环 MAC、设备 ID、通知正文和原始协议载荷留在本机。
            - 会话日志中的 MAC 地址和控制字符会被清理。
            - 文件只保存在本应用缓存目录，并通过系统分享交给用户选择的应用。
            """;

    private DeviceLogExporter() {}

    /** Creates a timestamped ZIP under the app cache and returns it for FileProvider sharing. */
    public static File export(Context context) throws Exception {
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        LocalPrefs state = LocalPrefs.open(app, "band-state");
        PackageInfo own = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
        LocalPrefs ownership = LocalPrefs.open(app, "ownership");
        int battery = state.getInt("battery", -1);

        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("format", FORMAT);
        details.put("createdAt", DISPLAY_TIME.format(Instant.now()));
        details.put("app.package", app.getPackageName());
        details.put("app.versionName", String.valueOf(own.versionName));
        details.put("app.versionCode", String.valueOf(own.getLongVersionCode()));
        details.put("device.name", BandCatalog.displayName(
                state.getString("modelId", ""), state.getString("name", "")));
        details.put("device.model", state.getString("modelId", ""));
        details.put("device.hardware", state.getString("verifiedHardware", ""));
        details.put("device.firmware", state.getString("verifiedFirmware",
                state.getString("firmware", "")));
        details.put("device.battery", battery >= 0 && battery <= 100 ? battery + "%" : "unknown");
        details.put("device.charging", String.valueOf(state.getBoolean("charging", false)));
        details.put("device.connected", String.valueOf(state.getBoolean("connected", false)));
        details.put("device.registered", String.valueOf(state.getBoolean("registered", false)));
        details.put("ownership.mode", ownership.getString("mode", "UNKNOWN"));
        details.put("ownership.transitionPending",
                String.valueOf(ownership.getBoolean("transitionPending", false)));
        details.put("android.release", Build.VERSION.RELEASE);
        details.put("android.sdk", String.valueOf(Build.VERSION.SDK_INT));
        details.put("phone.manufacturer", Build.MANUFACTURER);
        details.put("phone.model", Build.MODEL);
        details.put("session-log.bytes", String.valueOf(logFile(app).length()));

        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put("README.txt", README);
        entries.put("manifest.txt", manifest(details));
        entries.put("device-state.txt", deviceState(state));
        entries.put("transport.txt", transport(app, state.getString("modelId", "")));
        entries.put("environment.txt", environment(app, own));
        entries.put("live-status.txt", readLimited(
                new File(app.getNoBackupFilesDir(), "live-status.txt"), LIVE_STATUS_LIMIT, true));
        entries.put("session-log.txt", readLimited(logFile(app), SESSION_LOG_LIMIT, false));

        File directory = new File(app.getCacheDir(), "device-logs");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("DEVICE_LOG_DIRECTORY_FAILED");
        }
        trimOldArchives(directory);
        File output = new File(directory,
                "miband-device-log-" + FILE_TIME.format(Instant.now()) + ".zip");
        writeZip(output, entries);
        return output;
    }

    static String manifest(Map<String, String> details) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> row : details.entrySet()) {
            text.append(oneLine(row.getKey())).append('=')
                    .append(oneLine(row.getValue())).append('\n');
        }
        return text.toString();
    }

    static void writeZip(File output, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            zip.setLevel(9);
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey());
                item.setTime(0L);
                zip.putNextEntry(item);
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String deviceState(LocalPrefs state) {
        StringBuilder text = new StringBuilder();
        append(text, "registered", state.getBoolean("registered", false));
        append(text, "connected", state.getBoolean("connected", false));
        append(text, "modelId", state.getString("modelId", ""));
        append(text, "verifiedHardware", state.getString("verifiedHardware", ""));
        append(text, "verifiedFirmware", state.getString("verifiedFirmware", ""));
        append(text, "battery", state.getInt("battery", -1));
        append(text, "charging", state.getBoolean("charging", false));
        appendTime(text, "registeredAt", state.getLong("registeredAtMs", 0));
        appendTime(text, "lastUpdate", state.getLong("lastUpdateMs", 0));
        appendTime(text, "lastSync", state.getLong("lastSyncAtMs", 0));
        appendTime(text, "stepsAt", state.getLong("stepsAtMs", 0));
        appendTime(text, "heartRateAt", state.getLong("heartRateAtMs", 0));
        return text.toString();
    }

    private static String transport(Context context, String model) {
        JSONObject profile = TransportObservation.liveProfile(context, model);
        StringBuilder text = new StringBuilder();
        append(text, "transport", profile.optString("transport", ""));
        append(text, "framingVersion", TransportObservation.framingVersion(profile));
        append(text, "authentication", profile.optString("authImplementation", ""));
        append(text, "appCapability", profile.optInt("appCapability", 0));
        append(text, "officialAuthConnected", profile.optBoolean("officialAuthConnected", false));
        append(text, "oobPresent", profile.optBoolean("authOobPresent", false));
        append(text, "appDeviceIdPresent", profile.optBoolean("authAppDeviceIdPresent", false));
        return text.toString();
    }

    private static String environment(Context context, PackageInfo own) {
        StringBuilder text = new StringBuilder();
        append(text, "package", context.getPackageName());
        append(text, "versionName", own.versionName);
        append(text, "versionCode", own.getLongVersionCode());
        append(text, "androidRelease", Build.VERSION.RELEASE);
        append(text, "sdkInt", Build.VERSION.SDK_INT);
        append(text, "manufacturer", Build.MANUFACTURER);
        append(text, "brand", Build.BRAND);
        append(text, "model", Build.MODEL);
        append(text, "locale", Locale.getDefault().toLanguageTag());
        append(text, "timezone", TimeZone.getDefault().getID());
        return text.toString();
    }

    private static String readLimited(File file, long limit, boolean redact) throws IOException {
        if (!file.isFile() || limit <= 0) return "";
        byte[] bytes = Files.readAllBytes(file.toPath());
        int length = (int) Math.min(bytes.length, limit);
        String text = new String(bytes, bytes.length - length, length, StandardCharsets.UTF_8);
        return redact ? SessionLog.redact(text) : text;
    }

    private static void trimOldArchives(File directory) {
        File[] files = directory.listFiles((dir, name) ->
                name.startsWith("miband-device-log-") && name.endsWith(".zip"));
        if (files == null || files.length < KEPT_ARCHIVES) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        int remove = files.length - KEPT_ARCHIVES + 1;
        for (int index = 0; index < remove; index++) files[index].delete();
    }

    private static File logFile(Context context) {
        return new File(context.getNoBackupFilesDir(), "session-log.txt");
    }

    private static void append(StringBuilder text, String key, Object value) {
        text.append(key).append('=').append(oneLine(String.valueOf(value))).append('\n');
    }

    private static void appendTime(StringBuilder text, String key, long millis) {
        text.append(key).append('=');
        if (millis > 0) text.append(DISPLAY_TIME.format(Instant.ofEpochMilli(millis)));
        text.append('\n');
    }

    private static String oneLine(String value) {
        if (value == null) return "";
        return SessionLog.redact(value.replace('\n', ' ').replace('\r', ' '));
    }
}
