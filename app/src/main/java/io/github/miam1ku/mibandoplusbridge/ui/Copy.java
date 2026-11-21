// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import io.github.miam1ku.mibandoplusbridge.notify.SleepProtectionSchedule;

/**
 * User-facing copy shared by the module UI.
 * Style: say what the state is, then what to tap next. {@code CopyStyleTest} checks the wording.
 */
public final class Copy {
    private Copy() {}

    // ---- settings and device management ----

    public static final String APP_NAME = "小米手环桥接";
    public static final String VERSION_PREFIX = "版本 ";
    public static final String DEBUG_LOG_SUBJECT = "小米手环桥接调试日志";

    public static final String SETTINGS_APPEARANCE = "外观";
    public static final String SETTINGS_THEME = "主题与布局";
    public static final String SETTINGS_THEME_SUMMARY = "自定义更多主题与布局选项";
    public static final String SETTINGS_PERMISSIONS = "应用权限";
    public static final String SETTINGS_PERMISSION_ROW = "权限与后台";
    public static final String SETTINGS_PERMISSION_SUMMARY = "附近设备、自启动与关联启动";
    public static final String SETTINGS_SLEEP_SYNC = "睡眠同步";
    public static final String SLEEP_PROTECTION = "睡眠保护时段";
    public static final String SLEEP_PROTECTION_CAPTION =
            "按照日常睡眠习惯设置大约入睡和起床时刻。";
    public static final String SLEEP_PROTECTION_RANGE = "核心睡眠时段";
    public static final String SLEEP_PROTECTION_START = "开始";
    public static final String SLEEP_PROTECTION_END = "结束";
    public static final String SLEEP_PROTECTION_INPUT_HINT = "23:00";
    public static final String SLEEP_PROTECTION_INPUT_INVALID =
            "按 HH:00 输入整点时刻，调整后再保存。";
    public static final String SLEEP_PROTECTION_TOO_SHORT =
            "开始与结束时刻至少相隔 4 小时，调整后再保存。";
    public static final String SLEEP_PROTECTION_SAVE = "保存保护时段";
    public static final String SLEEP_PROTECTION_SAVED =
            "保护时段已保存，运行中的服务会立即使用新设置。";
    public static final String SLEEP_PROTECTION_SAVE_INTERRUPTED =
            "保护时段保存中断，再点一次即可。";
    public static final String SLEEP_PROTECTION_SLIDER_DESC = "睡眠保护开始与结束时间";
    public static final String SETTINGS_ADVANCED = "高级";
    public static final String SETTINGS_BINDING = "绑定与设备信息";
    public static final String SETTINGS_BINDING_SUMMARY = "重新导入、设备选择与管理";
    public static final String SETTINGS_DEBUG = "调试与日志";
    public static final String SETTINGS_DEBUG_SUMMARY = "协议测试、诊断与发送日志";
    public static final String SETTINGS_HELP = "帮助与关于";
    public static final String SETTINGS_GUIDE = "使用说明";
    public static final String SETTINGS_GUIDE_SUMMARY = "首次设置与日常使用";
    public static final String SETTINGS_ABOUT = "关于";

    public static final String EFFECTS_TITLE = "凝光视效";
    public static final String EFFECTS_CAPTION =
            "控制底栏、返回按钮等界面轮廓高光与内光晕，模糊和投影保持原样。";
    public static final String CONTOUR_TITLE = "轮廓光";
    public static final String CONTOUR_CAPTION = "关闭后保留组件材质，只隐藏描边与内光晕。";
    public static final String CONTOUR_SAVE_INTERRUPTED = "轮廓光设置保存中断，再试一次";
    public static final String DOCK_TITLE = "底栏";
    public static final String SYNC_ACTION_TITLE = "显示同步手环按钮";
    public static final String SYNC_ACTION_CAPTION = "在底栏右侧显示蓝色线性刷新按钮，按下会显示提示。";
    public static final String DOCK_SAVE_INTERRUPTED = "底栏设置保存中断，再试一次";
    public static final String EFFECTS_APPLIED = "修改后立即生效，当前页面保持打开。";

    public static final String DEVICE_MANAGEMENT = "设备管理";
    public static final String RESTORE_MI_HEALTH = "恢复小米运动健康";
    public static final String RESTORE_MI_HEALTH_CONFIRM = "恢复小米运动健康？";
    public static final String RESTORE_MI_HEALTH_MESSAGE =
            "桥接会断开连接，把手环交还小米运动健康。绑定和健康历史都会保留。";
    public static final String RESTORE_MI_HEALTH_RETRY = "点「恢复小米运动健康」再试一次。";
    public static final String REMOVE_FROM_HEALTH = "从健康移除";
    public static final String REMOVE_FROM_HEALTH_CONFIRM = "从健康移除这只手环？";
    public static final String REMOVE_FROM_HEALTH_MESSAGE =
            "桥接会断开连接，把手环交还小米运动健康。健康历史和加密绑定都会保留，之后可随时重新添加。";
    public static final String BINDING_MANAGEMENT_CAPTION = "绑定和健康历史始终保存在本机。";

    public static final String BINDING_STATUS_LOADING = "正在读取绑定信息…";
    public static final String BINDING_IMPORT_TITLE = "绑定导入";
    public static final String BINDING_IMPORT_CAPTION =
            "从本机小米运动健康数据库读取密钥；旧式导入窗口用于让官方应用在 120 秒内重新读取绑定。";
    public static final String BINDING_IMPORT_OPEN = "开启导入窗口（120 秒）";
    public static final String BINDING_IMPORT_FROM_DB = "从数据库导入";
    public static final String BINDING_IMPORT_CLOSE = "关闭导入和采集窗口";
    public static final String BINDING_OPEN_MI_HEALTH = "打开小米运动健康";
    public static final String BINDING_SELECT_FIRST = "先在上方选择一只已配对设备。";

    public static final String ACTION_CANCEL = "取消";
    public static final String ACTION_RESTORE = "恢复";
    public static final String ACTION_REMOVE = "移除";

    public static String sleepProtectionSummary(SleepProtectionSchedule.Window window) {
        return window.startLabel() + "–" + window.endLabel() + " · 自定义保护时段";
    }

    public static String sleepProtectionDuration(SleepProtectionSchedule.Window window) {
        int total = window.durationMinutes();
        int hours = total / 60;
        int minutes = total % 60;
        return minutes == 0
                ? "保护时长 " + hours + " 小时"
                : "保护时长 " + hours + " 小时 " + minutes + " 分";
    }

    public static String sleepProtectionSliderDesc(SleepProtectionSchedule.Window window) {
        return "睡眠保护 " + window.startLabel() + " 到 " + window.endLabel();
    }

    // ---- setup ----

    public static String setupLabel(SetupProgress.Step step) {
        return switch (step) {
            case ROOT -> "检查 Root";
            case IMPORT -> "导入绑定";
            case PROFILE -> "采集连接参数";
            case ADD -> "添加到健康";
            case DONE -> "立即同步";
        };
    }

    public static String setupHint(SetupProgress.Step step) {
        return switch (step) {
            case ROOT -> "在 Root 管理器中为「小米手环桥接」打开超级用户，回到这里点「检查 Root」。";
            case IMPORT -> "从小米运动健康的本地数据库读取手环密钥，全程在本机完成。";
            case PROFILE -> "绑定已保存，点「添加到健康」即可连接。";
            case ADD -> "小米运动健康会暂停，由桥接接管手环。手环离线时也可以先添加。";
            case DONE -> "";
        };
    }

    public static final String SETUP_PREREQ = "1. 在系统蓝牙里配对手环。\n"
            + "2. 在 LSPosed 启用本模块，勾选小米运动健康、OPPO 健康、设备空间，然后强停这三个应用一次。\n"
            + "3. 在 KernelSU 中允许本应用使用超级用户。";

    public static final String GUIDE = "1. 检查 Root：在 KernelSU 中允许本应用，回来点「检查 Root」。\n"
            + "2. 导入绑定：从小米运动健康的本地数据库读取手环密钥。\n"
            + "3. 添加到健康：桥接接管手环，设备空间和 OPPO 健康里随即出现这只手环。\n\n"
            + "日常使用：在「设备」页查看连接、电量和上次同步；点「同步剩余项目」重新连接并补齐记录。"
            + "设备详情、天气同步和入睡后暂停音乐在「工具」页。\n\n"
            + "开始前准备\n" + SETUP_PREREQ;

    // ---- connection ----

    /** Short reason shown next to "等待连接". */
    public static String offlineReason(String liveStatus) {
        return switch (liveStatus == null ? "" : liveStatus) {
            case "OBSERVED_PROFILE_REQUIRED" -> "到「设置 → 调试与日志」采集连接参数后即可连接";
            case "NATIVE_OWNERSHIP_REQUIRED" -> "点「同步剩余项目」重新接管";
            case "UNPROVISIONED", "BINDING_INCOMPLETE", "BINDING_ADDRESS", "BINDING_REGION",
                    "TOKEN_ENCODING_UNSUPPORTED", "CREDENTIAL_REFRESH_REQUIRED" ->
                    "到「设置 → 绑定与设备信息」重新导入绑定即可连接";
            case "BLUETOOTH_DISABLED" -> "打开蓝牙即可连接";
            default -> "手环靠近手机后会自动连接";
        };
    }

    /** Result line after add, reconnect, remove, or restore stops early. */
    public static String ownershipFailure(String code) {
        return switch (code == null ? "" : code) {
            case "USER_LOCKED" -> "解锁手机后再点一次。";
            case "SERVICE_STOPPING" -> "连接服务正在重启，再点一次「立即同步」。";
            case "UNPROVISIONED", "BINDING_INCOMPLETE" ->
                    "到「设置 → 绑定与设备信息」重新导入绑定，然后再添加。";
            case "BINDING_ADDRESS" -> "重新选择这只手环并导入绑定，蓝牙地址会按大写 MAC 保存。";
            case "BINDING_REGION" -> "在小米运动健康里打开这只手环，等它连上后再导入一次。";
            case "TOKEN_ENCODING_UNSUPPORTED" -> "让手环在小米运动健康里连上一次，再导入绑定。";
            case "OBSERVED_PROFILE_REQUIRED" ->
                    "到「设置 → 调试与日志」点「采集连接参数」，在小米运动健康里重连一次手环。";
            case "FIRMWARE_OR_MODEL_UNSUPPORTED" -> "让手环在小米运动健康里连上一次，读到型号和固件后再添加。";
            case "OOB_BRANCH_UNSUPPORTED" -> "这只设备使用带外配对认证。桥接适配 SPP 和 BLE 的 WearAuthV2 认证。";
            case "PROTOCOL_VERSION_UNSUPPORTED", "SESSION_CONFIGURATION_UNSUPPORTED" ->
                    "桥接适配官方以 SPP 或 BLE 重连的手环。在小米运动健康里重连一次后再试。";
            case "DEVICE_IDENTITY_CHANGED" -> "这次导入的是另一只手环。请导入原来那只，或先移除再添加新手环。";
            case "BAND_STATE_STORAGE_FAILED", "OWNERSHIP_STORAGE_FAILED" -> "清理一些存储空间后再点一次。";
            case "ROOT_REQUIRED", "ROOT_ACTION_FAILED", "ROOT_ACTION_TIMEOUT" ->
                    "在 Root 管理器中确认已允许本应用，再点一次。";
            case "HOST_VERSION_UNSUPPORTED" -> "请把小米运动健康换到实测版本 3.59.1 后再试。";
            case "NEARBY_PERMISSION_REQUIRED" -> "授权附近设备后再点一次。";
            case "DEVICE_NOT_REGISTERED" -> "先点「添加到健康」。";
            case "DIAGNOSTIC_SOCKET_STILL_ACTIVE" -> "连接正在关闭，稍等几秒再恢复官方管理。";
            case "OWNERSHIP_RECOVERY_REQUIRED", "OFFICIAL_ALREADY_DISABLED" ->
                    "先恢复官方管理，确认小米运动健康已启用，再接管。";
            case "OWNERSHIP_ROLLBACK_FAILED" -> "小米运动健康可能仍在暂停，再点一次「恢复小米运动健康」。";
            default -> "这次操作中断了。再点一次；仍然中断时先恢复官方管理。";
        };
    }

    // ---- binding import ----

    public static String importResult(String code, String model, String productId, String firmware) {
        String detail = "\n型号：" + dash(model) + "\n产品：" + dash(productId)
                + "\n固件：" + (firmware == null || firmware.isBlank() ? "连接后读取" : firmware);
        return switch (code == null ? "" : code) {
            case "IMPORT_WINDOW_OPEN" -> "导入窗口已开启。在小米运动健康中打开所选设备，官方应用会重新读取绑定。";
            case "IMPORT_WINDOW_CLOSED" -> "导入和协议采集窗口都已关闭。";
            case "DIAGNOSTIC_WINDOW_OPEN" -> "连接参数采集已开启。只记录连接类型和协议版本。在官方应用中重连手环。";
            case "PROTOCOL_CAPTURE_OPEN" -> "协议消息采集已开启，120 秒后自动关闭。现在执行要测试的操作。";
            case "UNPROVISIONED" -> "等待导入绑定。";
            case "BINDING_SAVED" -> "绑定已保存。" + detail;
            case "BINDING_INCOMPLETE" -> "绑定已保存，点「添加到健康」即可连接。" + detail;
            case "NO_BAND_KEY" -> "先在小米运动健康里完成手环配对，再导入。";
            case "MI_DATABASE_MISSING" -> "安装并登录小米运动健康、配对手环后再导入。";
            case "MI_DATABASE_UNREADABLE" -> "设备数据库正被占用，稍等片刻再导入。";
            case "ROOT_ACTION_FAILED", "ROOT_ACTION_TIMEOUT" -> "在 KernelSU 中确认已允许本应用，再导入一次。";
            case "MULTIPLE_BANDS" -> "数据库里有多只手环，选一只导入。";
            case "USER_LOCKED" -> "解锁手机后再导入。";
            case "CREDENTIAL_STORAGE_FAILED" -> "保存中断了，再点一次「导入绑定」即可恢复。";
            default -> "这次操作中断了，再点一次。";
        };
    }

    // ---- health sync ----

    public static String healthHost(String status) {
        return "UNAVAILABLE".equals(status) ? "打开一次 OPPO 健康，健康记录会接着写入。" : "";
    }

    public static String healthCollection(String status) {
        return switch (status == null ? "" : status) {
            case "ACCOUNT_CONFIRMATION_REQUIRED" -> "健康记录写入当前 OHealth 账号，由 OHealth 同步到云端。";
            case "HEALTH_OUTBOX_FULL" -> "健康记录排队中，OHealth 处理完后会继续采集，记录都已保存。";
            case "HISTORY_FORMAT_UNSUPPORTED" -> "部分健康文件以原始格式保存，等待后续版本解析。";
            case "HISTORY_FILE_REJECTED" -> "一份健康文件需要重新获取，下次同步时自动完成。";
            case "HISTORY_STORAGE_FAILED", "HEALTH_STORAGE_UNAVAILABLE" -> "清理一些存储空间后，健康同步会继续。";
            case "HEALTH_REPLAY_PAUSED", "HISTORY_CONFIRMATION_PENDING" -> "健康同步稍后继续，已保存的记录都在。";
            case "HISTORY_SYNC_QUEUED" -> "同步已排队，睡眠保护解除后继续。";
            case "HISTORY_SYNCING" -> "正在同步健康记录。";
            default -> "";
        };
    }

    // ---- weather ----

    public static String weatherError(String code) {
        return switch (code == null ? "" : code) {
            case "WEATHER_CITY_CONFIRMATION_REQUIRED", "WEATHER_PREVIEW_CHANGED" -> "城市有变化，点「核对手环城市」重新关联。";
            case "WEATHER_CITY_SETUP_REQUIRED" -> "先在小米运动健康里给手环设置一个城市，再切回桥接。";
            case "WEATHER_CITY_LIST_INVALID" -> "在小米运动健康里检查手环的城市列表，再发送。";
            case "WEATHER_SOURCE_STALE", "WEATHER_FORECAST_STALE" -> "天气已过期，点「获取当前位置天气」刷新后再发送。";
            case "WEATHER_BAND_REJECTED" -> "手环这次保留了原有天气，健康连接照常运行，稍后再发送一次。";
            case "WEATHER_BUSY" -> "上一次天气任务还在进行，稍后再点。";
            case "WEATHER_DISCONNECTED", "LIVE_SESSION_UNAVAILABLE", "WEATHER_CLOSED", "NATIVE_OWNERSHIP_REQUIRED" ->
                    "回「设备」页点「同步剩余项目」，连上后再发送。";
            case "WEATHER_DATA_INVALID" -> "点「获取当前位置天气」换一份天气再发送。";
            default -> "天气发送中断，检查连接和天气源后再点一次。";
        };
    }

    public static String weatherSource(String status, boolean pending) {
        if (pending) return "正在等待 OHealth 更新天气";
        return switch (status == null ? "" : status) {
            case "" -> "";
            case "OHEALTH_LOCATION_PERMISSION_REQUIRED" -> "在系统设置里允许 OHealth 使用位置";
            case "OHEALTH_LOCATION_DISABLED" -> "打开系统定位后再获取";
            case "OHEALTH_LOCATION_UNAVAILABLE" -> "正在等待更准确的位置，稍后再获取";
            case "OHEALTH_WEATHER_CLOUD_UNAVAILABLE" -> "检查网络后再获取";
            case "OHEALTH_WEATHER_TIMEOUT" -> "天气处理较慢，打开一次 OHealth 再获取";
            default -> "打开一次 OHealth 再获取天气";
        };
    }

    // ---- shared helpers ----

    public static String dash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public static String stageName(Integer stage) {
        if (stage == null) return "其他阶段";
        return switch (stage) {
            case 2 -> "深睡";
            case 3 -> "浅睡";
            case 4 -> "快速眼动（REM）";
            case 5 -> "清醒";
            case 6 -> "整段";
            default -> "其他阶段";
        };
    }

    /** Quick settings subtitle. */
    public static String tileSubtitle(boolean registered, boolean serviceUp, boolean connected, int battery) {
        if (!registered) return "在应用中添加";
        if (serviceUp && connected) return battery >= 0 && battery <= 100 ? "已连接 · " + battery + "%" : "已连接";
        if (serviceUp) return "正在连接";
        return "点按连接";
    }
}
