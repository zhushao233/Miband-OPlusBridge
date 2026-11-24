// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hchen.himiuix.MiuixBasicView;
import com.itos.coui.host.CouiConfirmDialog;
import io.github.miam1ku.mibandoplusbridge.data.BandCatalog;
import io.github.miam1ku.mibandoplusbridge.data.BandStateRepository;
import io.github.miam1ku.mibandoplusbridge.data.BindingStore;
import io.github.miam1ku.mibandoplusbridge.data.HealthRecordStore;
import io.github.miam1ku.mibandoplusbridge.data.LocalPrefs;
import io.github.miam1ku.mibandoplusbridge.data.TransportObservation;
import io.github.miam1ku.mibandoplusbridge.integration.CredentialProvider;
import io.github.miam1ku.mibandoplusbridge.integration.DeviceCardProvider;
import io.github.miam1ku.mibandoplusbridge.integration.HealthQueueProvider;
import io.github.miam1ku.mibandoplusbridge.notify.BandNotificationListener;
import io.github.miam1ku.mibandoplusbridge.notify.SleepMusic;
import io.github.miam1ku.mibandoplusbridge.notify.SleepProtectionSchedule;
import io.github.miam1ku.mibandoplusbridge.protocol.SppDiagnosticClient;
import io.github.miam1ku.mibandoplusbridge.service.BandLiveService;
import io.github.miam1ku.mibandoplusbridge.service.OwnershipController;
import io.github.miam1ku.mibandoplusbridge.ui.BridgeScreen;
import io.github.miam1ku.mibandoplusbridge.ui.BridgeScreen.PageTransition;
import io.github.miam1ku.mibandoplusbridge.ui.BridgeScreen.StepState;
import io.github.miam1ku.mibandoplusbridge.ui.BridgeScreen.Tone;
import io.github.miam1ku.mibandoplusbridge.ui.Copy;
import io.github.miam1ku.mibandoplusbridge.ui.SetupProgress;
import io.github.miam1ku.mibandoplusbridge.ui.SleepWindowSlider;
import io.github.miam1ku.mibandoplusbridge.ui.UiStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Three-tab module home. Device keeps binding and connection state, Tools keeps shortcuts and
 * habits, Settings keeps preferences and secondary advanced pages. No foreground service status
 * is rendered on the home surface.
 */
public final class MainActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final int BLUETOOTH_PERMISSION = 1;
    private static final int CALL_PERMISSIONS = 2;

    enum Change { ADD, RECONNECT, REMOVE, RESTORE }

    private BridgeScreen screen;
    private BridgeScreen devicePage;
    private BridgeScreen toolsPage;
    private BridgeScreen settingsPage;
    private BridgeScreen setupPage;
    private BridgeScreen bindingPage;
    private BridgeScreen debugPage;
    private BridgeScreen permissionsPage;
    private BridgeScreen appearancePage;
    private BridgeScreen sleepProtectionPage;
    private BridgeScreen aboutPage;
    private int currentTabId = R.id.tab_device;
    private boolean secondaryPage;
    private OnBackPressedCallback backCallback;

    private final List<View> boundViews = new ArrayList<>();
    private final List<View> unboundViews = new ArrayList<>();

    private TextView deviceName;
    private TextView connectionStatus;
    private TextView deviceSub;
    private TextView batteryValue;
    private TextView lastSyncValue;
    private TextView unboundTitle;
    private TextView unboundSub;
    private TextView unboundCta;
    private BridgeScreen.ActionRow syncButton;
    private TextView statusNoticeText;
    private TextView unboundStatusText;

    private TextView setupTitle;
    private TextView stepRoot;
    private TextView stepImport;
    private TextView stepAdd;
    private TextView primaryHint;
    private BridgeScreen.ActionRow setupPrimary;
    private BridgeScreen.ActionRow openKsu;

    private LinearLayout nearbyNotice;
    private LinearLayout notifyNotice;
    private LinearLayout healthAccessNotice;

    private BridgeScreen.ToggleRow sleepPause;
    private MiuixBasicView deviceDetailRow;
    private MiuixBasicView appearanceRow;
    private MiuixBasicView permissionRow;
    private MiuixBasicView sleepProtectionRow;
    private SleepWindowSlider sleepProtectionSlider;
    private AppCompatEditText sleepStartInput;
    private AppCompatEditText sleepEndInput;
    private TextView sleepProtectionInputHint;
    private boolean syncingSleepInputs;

    private LinearLayout bonded;
    private TextView bindingStatus;
    private BridgeScreen.ActionRow openWindow;
    private BridgeScreen.ActionRow bindingRemove;
    private BridgeScreen.ActionRow bindingRestore;

    private TextView debugStatus;
    private MiuixBasicView nearbyRow;
    private MiuixBasicView autoStartRow;

    private String selectedAddress = "";
    private String databaseImportNote = "";
    private boolean hostSupported;
    private boolean callPermissionsAsked;
    private boolean changingOwnership;
    private boolean versionPromptShown;
    private boolean launchHostAfterOpen;
    private boolean profileAfterRestore;
    private boolean rootGranted;
    private boolean probingRoot;
    private boolean manualSyncAwaitingCompletion;
    private boolean syncReceiverRegistered;
    private String manualSyncRequestId = "";
    private Change pendingPermission;

    static boolean openProfileAfterRestore(Change change, boolean restored) {
        return restored && change == Change.RESTORE;
    }

    private final ContentObserver deviceObserver = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { refreshDevice(); }
    };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) {
            refresh();
            refreshDevice();
        }
    };
    private final Runnable expireRefresh = this::refresh;
    private final Runnable manualSyncConnectTimeout = () -> {
        if (!manualSyncAwaitingCompletion) return;
        finishManualSyncFeedback();
        showStatus("连接仍在进行，稍后可在设备页查看同步结果。");
    };
    private final BroadcastReceiver manualSyncReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!manualSyncAwaitingCompletion) return;
            String requestId = intent.getStringExtra(BandLiveService.EXTRA_SYNC_REQUEST_ID);
            if (requestId == null || !requestId.equals(manualSyncRequestId)) return;
            if (BandLiveService.ACTION_SYNC_ROUND_STARTED.equals(intent.getAction())) {
                return;
            }
            if (BandLiveService.ACTION_SYNC_COMPLETED.equals(intent.getAction())) {
                finishManualSyncFeedback();
                showStatus("已与设备同步数据");
                Toast.makeText(MainActivity.this, "已与设备同步数据", Toast.LENGTH_SHORT).show();
                refreshDevice();
                return;
            }
            if (BandLiveService.ACTION_SYNC_FAILED.equals(intent.getAction())) {
                finishManualSyncFeedback();
                String detail = Copy.healthCollection(
                        intent.getStringExtra(BandLiveService.EXTRA_SYNC_ERROR));
                showStatus(detail.isBlank() ? "同步中断，再试一次。" : detail);
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen = BridgeScreen.attachTabs(this, "设备", R.menu.main_bottom,
                R.id.tab_device, this::selectTab);
        screen.setSyncAction(this::syncNow);
        devicePage = screen.page();
        toolsPage = screen.page();
        settingsPage = screen.page();
        setupPage = screen.page();
        bindingPage = screen.page();
        debugPage = screen.page();
        permissionsPage = screen.page();
        appearancePage = screen.page();
        sleepProtectionPage = screen.page();
        aboutPage = screen.page();

        buildDeviceTab();
        buildToolsTab();
        buildSettingsTab();
        buildSetupPage();
        buildBindingPage();
        buildDebugPage();
        buildPermissionsPage();
        buildAppearancePage();
        buildSleepProtectionPage();
        buildAboutPage();

        if (state != null) {
            int restored = state.getInt("currentTab", R.id.tab_device);
            if (restored == R.id.tab_tools || restored == R.id.tab_settings) currentTabId = restored;
        }
        backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackStarted(BackEventCompat event) {
                if (!secondaryPage) return;
                BridgeScreen target = primaryPage(currentTabId);
                if (target != null) screen.beginPredictiveBack(target.body);
            }

            @Override public void handleOnBackProgressed(BackEventCompat event) {
                if (secondaryPage) screen.updatePredictiveBack(event.getProgress());
            }

            @Override public void handleOnBackCancelled() {
                if (secondaryPage) screen.cancelPredictiveBack();
            }

            @Override public void handleOnBackPressed() {
                if (secondaryPage) {
                    showPrimaryTab(currentTabId, true, PageTransition.BACK);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        showPrimaryTab(currentTabId, false, PageTransition.NONE);

        getContentResolver().registerContentObserver(CredentialProvider.URI, false, observer);
        getContentResolver().registerContentObserver(DeviceCardProvider.URI, false, deviceObserver);
        getContentResolver().registerContentObserver(HealthQueueProvider.URI, false, deviceObserver);
        IntentFilter syncEvents = new IntentFilter();
        syncEvents.addAction(BandLiveService.ACTION_SYNC_ROUND_STARTED);
        syncEvents.addAction(BandLiveService.ACTION_SYNC_COMPLETED);
        syncEvents.addAction(BandLiveService.ACTION_SYNC_FAILED);
        registerReceiver(manualSyncReceiver, syncEvents, Context.RECEIVER_NOT_EXPORTED);
        syncReceiverRegistered = true;
        refreshDevice();
        requestRoot();
    }

    // ---- tabs and pages ----

    private void selectTab(int itemId) {
        if (itemId == R.id.tab_device || itemId == R.id.tab_tools || itemId == R.id.tab_settings) {
            showPrimaryTab(itemId, true, PageTransition.TAB);
        }
    }

    private void showPrimaryTab(int itemId, boolean scrollTop) {
        showPrimaryTab(itemId, scrollTop, PageTransition.TAB);
    }

    private void showPrimaryTab(int itemId, boolean scrollTop, PageTransition transition) {
        currentTabId = itemId;
        secondaryPage = false;
        BridgeScreen target = primaryPage(itemId);
        if (target == null) return;
        screen.setPageChrome(itemId == R.id.tab_tools ? "工具"
                : itemId == R.id.tab_settings ? "设置" : "设备", false, null);
        screen.setTabBarVisible(true);
        screen.checkTab(itemId);
        if (scrollTop) screen.scrollToTop();
        screen.showPage(target.body, transition, transition != PageTransition.NONE);
        if (itemId == R.id.tab_tools) refreshTools();
        if (itemId == R.id.tab_settings) refreshSettingsRows();
        if (backCallback != null) backCallback.setEnabled(false);
    }

    private BridgeScreen primaryPage(int itemId) {
        return itemId == R.id.tab_tools ? toolsPage
                : itemId == R.id.tab_settings ? settingsPage : devicePage;
    }

    private void showSecondaryPage(BridgeScreen page, String title, Runnable onBack) {
        secondaryPage = true;
        screen.showPage(page.body, PageTransition.FORWARD, true);
        screen.setPageChrome(title, true, () -> {
            showPrimaryTab(currentTabId, true, PageTransition.BACK);
            if (onBack != null) onBack.run();
        });
        screen.setTabBarVisible(false);
        screen.scrollToTop();
        if (backCallback != null) backCallback.setEnabled(true);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("currentTab", currentTabId);
        super.onSaveInstanceState(outState);
    }

    // ---- device tab ----

    private void buildDeviceTab() {
        buildHero();
        buildNotices();
        buildUnbound();
    }

    private void buildHero() {
        LinearLayout card = devicePage.card();
        boundViews.add(card);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        addWithMargins(card, top, 16, 12, 12, 4);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        connectionStatus = text(14, R.color.accent, true);
        connectionStatus.setText("● 等待连接");
        copy.addView(connectionStatus, textParams(0, 0, 0, 4));

        deviceName = text(29, R.color.on_surface, true);
        deviceName.setText("小米手环");
        deviceName.setLetterSpacing(-0.03f);
        copy.addView(deviceName, textParams(0, 0, 0, 4));

        deviceSub = text(13, R.color.on_surface_variant, false);
        copy.addView(deviceSub, textParams(0, 0, 0, 0));

        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.ic_band_hero);
        art.setContentDescription(null);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(82), dp(100));
        artParams.leftMargin = dp(8);
        top.addView(art, artParams);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        addWithMargins(card, meta, 16, 8, 16, 10);
        batteryValue = metric(meta, "电量");
        lastSyncValue = metric(meta, "上次同步");

        statusNoticeText = text(13, R.color.on_surface_variant, false);
        statusNoticeText.setLineSpacing(0, 1.2f);
        statusNoticeText.setText("设备登记已保留，连接稍后继续。");
        addWithMargins(card, statusNoticeText, 16, 0, 16, 10);
        statusNoticeText.setVisibility(View.GONE);

        syncButton = devicePage.filled(card, "同步剩余项目", this::syncNow);
    }

    private TextView metric(LinearLayout parent, String label) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.rightMargin = dp(18);
        parent.addView(column, params);
        TextView caption = text(12, R.color.on_surface_variant, false);
        caption.setText(label);
        column.addView(caption, textParams(0, 0, 0, 3));
        TextView value = text(18, R.color.on_surface, true);
        value.setText("—");
        column.addView(value, textParams(0, 0, 0, 0));
        return value;
    }

    private void buildNotices() {
        nearbyNotice = devicePage.noticeCard(Tone.ATTENTION);
        devicePage.bodyText(nearbyNotice, "授权附近设备后即可连接手环。");
        devicePage.filled(nearbyNotice, "授权附近设备", () -> {
            pendingPermission = null;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION);
        });

        notifyNotice = devicePage.noticeCard(Tone.NEUTRAL);
        devicePage.bodyText(notifyNotice, "开启通知后，可在通知栏看到手环连接状态。");
        devicePage.outlined(notifyNotice, "开启通知", this::openAppNotificationSettings);

        healthAccessNotice = devicePage.noticeCard(Tone.ACTIVE);
        devicePage.bodyText(healthAccessNotice,
                "在系统设置里把 OPPO 健康的通知使用权关掉再打开一次，手机通知就能转到手环。");
        devicePage.outlined(healthAccessNotice, "打开通知使用权", this::openHealthListenerSettings);
        setShown(nearbyNotice, false);
        setShown(notifyNotice, false);
        setShown(healthAccessNotice, false);
    }

    private void buildUnbound() {
        LinearLayout card = devicePage.card();
        unboundViews.add(card);
        card.setOnClickListener(view -> showSecondaryPage(setupPage, "首次设置", this::refreshDevice));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_tab_device);
        icon.setContentDescription(null);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.leftMargin = dp(16);
        iconParams.topMargin = dp(16);
        iconParams.bottomMargin = dp(10);
        iconParams.rightMargin = dp(16);
        card.addView(icon, iconParams);

        unboundTitle = devicePage.title(card, "还没有绑定手环");
        unboundTitle.setTextSize(22);
        unboundSub = devicePage.caption(card,
                "从小米运动健康的本地数据库导入绑定。密钥和健康历史只保存在本机。");
        unboundSub.setTextSize(14);

        unboundStatusText = text(13, R.color.on_surface_variant, false);
        unboundStatusText.setLineSpacing(0, 1.2f);
        addWithMargins(card, unboundStatusText, 16, 0, 16, 10);
        unboundStatusText.setVisibility(View.GONE);

        unboundCta = text(14, R.color.accent, true);
        unboundCta.setText("开始首次设置  ›");
        unboundCta.setGravity(Gravity.CENTER_VERTICAL);
        unboundCta.setPadding(dp(14), dp(11), dp(14), dp(11));
        unboundCta.setBackground(BridgeScreen.roundedRect(
                color(R.color.accent_soft), dp(14), 0, 0));
        addWithMargins(card, unboundCta, 16, 12, 16, 12);

        TextView prepHeading = devicePage.heading("开始前准备");
        unboundViews.add(prepHeading);
        LinearLayout prep = devicePage.card();
        unboundViews.add(prep);
        MiuixBasicView pair = devicePage.navRow(prep, "在系统蓝牙中配对手环", this::openBluetoothSettings);
        pair.setSummary("配对后再导入本地绑定");
        MiuixBasicView scope = devicePage.navRow(prep, "启用模块作用域", this::showSetupGuide);
        scope.setSummary("小米运动健康、OPPO 健康、设备空间");
    }

    private void syncNow() {
        if (changingOwnership || probingRoot || manualSyncAwaitingCompletion) return;
        if (!new BandStateRepository(this).isRegistered()) {
            showSecondaryPage(setupPage, "首次设置", this::refreshDevice);
            return;
        }
        if (!rootGranted) {
            requestRoot();
            return;
        }
        requestNative(Change.RECONNECT);
    }

    private void refreshDevice() {
        if (isDestroyed() || worker.isShutdown()) return;
        updateControls();
        refreshNotices();
        refreshPermissionRows();
        refreshSettingsRows();
        if (!isUnlocked()) {
            deviceName.setText("小米手环");
            connectionStatus.setText("● 解锁后显示");
            connectionStatus.setTextColor(color(R.color.on_surface_variant));
            setOptional(deviceSub, "解锁手机后显示手环信息。");
            setOptional(batteryValue, "—");
            setOptional(lastSyncValue, "—");
            unboundTitle.setText("还没有绑定手环");
            unboundSub.setText("解锁手机后读取本机绑定信息，已有数据都在。");
            return;
        }

        var state = LocalPrefs.open(this, "band-state");
        boolean registered = new BandStateRepository(this).isRegistered();
        if (registered) {
            deviceName.setText(safeIdentity(
                    BandCatalog.displayName(state.getString("modelId", ""), state.getString("name", "")),
                    "已登记手环"));
            boolean connected = state.getBoolean("connected", false);
            connectionStatus.setText(connected ? "● 已连接" : "● 等待连接");
            connectionStatus.setTextColor(color(connected ? R.color.accent : R.color.on_surface_variant));
            String firmware = firmware(state);
            setOptional(deviceSub, firmware.isEmpty() ? "连接后读取固件与设备信息" : "固件 " + firmware);
            int battery = state.getInt("battery", -1);
            batteryValue.setText(battery >= 0 && battery <= 100 ? battery + "%" : "—");
            lastSyncValue.setText(syncLabel(state.getLong("lastSyncAtMs", 0)));
        }

        boolean hasBinding = false;
        try {
            hasBinding = new BindingStore(this).read() != null;
        } catch (Exception ignored) { }
        unboundTitle.setText(hasBinding ? "绑定已导入" : "还没有绑定手环");
        unboundSub.setText(hasBinding
                ? "添加到健康后即可连接手环。继续完成首次设置。"
                : "从小米运动健康的本地数据库导入绑定。密钥和健康历史只保存在本机。");
        unboundCta.setText(hasBinding ? "继续首次设置  ›" : "开始首次设置  ›");
    }

    private static String firmware(LocalPrefs state) {
        String value = state.getString("verifiedFirmware", "");
        if (value.isBlank()) value = state.getString("firmware", "");
        return value == null ? "" : value.trim();
    }

    private String syncLabel(long value) {
        if (value <= 0) return "—";
        java.util.Calendar sync = java.util.Calendar.getInstance();
        sync.setTimeInMillis(value);
        java.util.Calendar now = java.util.Calendar.getInstance();
        boolean today = sync.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
                && sync.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);
        return new java.text.SimpleDateFormat(today ? "HH:mm" : "MM-dd HH:mm", Locale.getDefault())
                .format(new Date(value));
    }

    private void showStatus(String message) {
        boolean visible = message != null && !message.isBlank();
        if (statusNoticeText != null) {
            statusNoticeText.setVisibility(visible ? View.VISIBLE : View.GONE);
            statusNoticeText.setText(visible ? message : "");
        }
        if (unboundStatusText != null) {
            unboundStatusText.setVisibility(visible ? View.VISIBLE : View.GONE);
            unboundStatusText.setText(visible ? message : "");
        }
    }

    private void hideStatus() {
        showStatus("");
    }

    // ---- tools tab ----

    private void buildToolsTab() {
        LinearLayout links = toolsPage.card();
        deviceDetailRow = toolsPage.navRow(links, "设备详情",
                () -> startActivity(new Intent().setClassName(this,
                        "io.github.miam1ku.mibandoplusbridge.integration.BandDetailsInternalActivity")));
        deviceDetailRow.setSummary("已保存历史、日汇总核对");
        deviceDetailRow.setIcon(rowIcon(R.drawable.ic_tab_device));
        MiuixBasicView weather = toolsPage.navRow(links, "天气同步",
                () -> startActivity(new Intent(this, WeatherActivity.class)));
        weather.setSummary("位置天气、城市关联");
        weather.setIcon(rowIcon(R.drawable.ic_weather));

        toolsPage.heading("使用习惯");
        LinearLayout habit = toolsPage.card();
        if (BandLiveService.sleepStateUnsupportedForCurrentDevice(this)) {
            toolsPage.bodyText(habit, "当前手环会在下次同步时更新睡眠状态。");
            return;
        }
        sleepPause = toolsPage.switchRow(habit, "入睡后暂停音乐",
                "手环报告入睡时暂停正在播放的音乐，醒来后保持暂停。");
        sleepPause.setChecked(SleepMusic.enabled(this));
        sleepPause.setOnStateChangeListener(checked -> {
            if (SleepMusic.setEnabled(this, checked)) {
                BandLiveService.sleepPauseChanged(this);
                return true;
            }
            showStatus("开关状态保存中断，再拨一次即可。");
            return false;
        });
    }

    private void refreshTools() {
        if (deviceDetailRow == null) return;
        deviceDetailRow.setVisibility(new BandStateRepository(this).isRegistered()
                ? View.VISIBLE : View.GONE);
    }

    // ---- settings tab ----

    private void buildSettingsTab() {
        settingsPage.heading(Copy.SETTINGS_APPEARANCE);
        LinearLayout appearance = settingsPage.card();
        appearanceRow = settingsPage.navRow(appearance, Copy.SETTINGS_THEME,
                () -> showSecondaryPage(appearancePage, Copy.SETTINGS_THEME, this::refreshSettingsRows));
        appearanceRow.setIcon(rowIcon(R.drawable.ic_appearance));
        appearanceRow.setSummary(Copy.SETTINGS_THEME_SUMMARY);

        settingsPage.heading(Copy.SETTINGS_SLEEP_SYNC);
        LinearLayout sleepProtection = settingsPage.card();
        sleepProtectionRow = settingsPage.navRow(sleepProtection, Copy.SLEEP_PROTECTION, () -> {
            refreshSleepProtectionControls(SleepProtectionSchedule.load(this));
            showSecondaryPage(sleepProtectionPage, Copy.SLEEP_PROTECTION, this::refreshSettingsRows);
        });
        sleepProtectionRow.setIcon(rowIcon(R.drawable.ic_sleep));

        settingsPage.heading(Copy.SETTINGS_PERMISSIONS);
        LinearLayout permissions = settingsPage.card();
        permissionRow = settingsPage.navRow(permissions, Copy.SETTINGS_PERMISSION_ROW, () -> {
            refreshPermissionRows();
            showSecondaryPage(permissionsPage, Copy.SETTINGS_PERMISSION_ROW, this::refreshPermissionRows);
        });
        permissionRow.setSummary(Copy.SETTINGS_PERMISSION_SUMMARY);
        permissionRow.setIcon(rowIcon(R.drawable.ic_permissions));

        settingsPage.heading(Copy.SETTINGS_ADVANCED);
        LinearLayout advanced = settingsPage.card();
        MiuixBasicView binding = settingsPage.navRow(advanced, Copy.SETTINGS_BINDING, () -> {
            refreshBindingStatus();
            refreshBonded();
            showSecondaryPage(bindingPage, Copy.SETTINGS_BINDING, this::refreshDevice);
        });
        binding.setSummary(Copy.SETTINGS_BINDING_SUMMARY);
        binding.setIcon(rowIcon(R.drawable.ic_tab_device));
        MiuixBasicView debug = settingsPage.navRow(advanced, Copy.SETTINGS_DEBUG, () -> {
            refreshDebugStatus();
            showSecondaryPage(debugPage, Copy.SETTINGS_DEBUG, this::refreshDevice);
        });
        debug.setSummary(Copy.SETTINGS_DEBUG_SUMMARY);
        debug.setIcon(rowIcon(R.drawable.ic_debug));

        settingsPage.heading(Copy.SETTINGS_HELP);
        LinearLayout about = settingsPage.card();
        MiuixBasicView guide = settingsPage.navRow(about, Copy.SETTINGS_GUIDE, this::showSetupGuide);
        guide.setSummary(Copy.SETTINGS_GUIDE_SUMMARY);
        MiuixBasicView aboutEntry = settingsPage.navRow(about, Copy.SETTINGS_ABOUT,
                () -> showSecondaryPage(aboutPage, Copy.SETTINGS_ABOUT, null));
        aboutEntry.setSummary(Copy.VERSION_PREFIX + BuildConfig.VERSION_NAME);
        aboutEntry.setIcon(ContextCompat.getDrawable(this, R.mipmap.ic_launcher));
        refreshSettingsRows();
    }

    private void refreshSettingsRows() {
        if (appearanceRow == null) return;
        appearanceRow.setSummary(Copy.SETTINGS_THEME_SUMMARY);
        if (sleepProtectionRow != null) {
            sleepProtectionRow.setSummary(Copy.sleepProtectionSummary(
                    SleepProtectionSchedule.load(this)));
        }
    }

    private void confirmRestore() {
        CouiConfirmDialog.show(this,
                Copy.RESTORE_MI_HEALTH_CONFIRM,
                Copy.RESTORE_MI_HEALTH_MESSAGE,
                Copy.ACTION_CANCEL,
                Copy.ACTION_RESTORE,
                color(R.color.accent),
                UiStyle.contourGlow(this),
                null,
                () -> changeOwnership(Change.RESTORE));
    }

    // ---- setup page ----

    private void buildSetupPage() {
        LinearLayout card = setupPage.card();
        setupTitle = setupPage.overline(card, "首次设置 · 第 1 步，共 3 步");
        stepRoot = setupPage.stepRow(card, 1, "授权 Root", StepState.CURRENT);
        stepImport = setupPage.stepRow(card, 2, "导入绑定", StepState.LATER);
        stepAdd = setupPage.stepRow(card, 3, "添加到健康", StepState.LATER);
        primaryHint = setupPage.caption(card, "");
        setupPrimary = setupPage.filled(card, Copy.setupLabel(SetupProgress.Step.ROOT), this::runHomePrimary);
        openKsu = setupPage.outlined(card, "打开 Root 管理器", this::openKernelSu);
        setupPage.textButton(card, "打开小米运动健康", () -> launchMiHealth(null));

        setupPage.heading("开始前准备");
        LinearLayout prep = setupPage.card();
        setupPage.caption(prep, Copy.SETUP_PREREQ);
        setupPage.caption("完成三步后，设备页会显示连接、电量与上次同步。");
    }

    private void bindChecklist(SetupProgress progress) {
        setupTitle.setText("首次设置 · 第 " + progress.visibleIndex() + " 步，共 3 步");
        int current = progress.visibleIndex();
        setupPage.setStep(stepRoot, 1, "授权 Root", stepState(1, current));
        setupPage.setStep(stepImport, 2, "导入绑定", stepState(2, current));
        setupPage.setStep(stepAdd, 3, "添加到健康", stepState(3, current));
        String hint = progress.primaryHint();
        primaryHint.setText(hint);
        primaryHint.setVisibility(hint.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static StepState stepState(int index, int current) {
        if (index < current) return StepState.DONE;
        return index == current ? StepState.CURRENT : StepState.LATER;
    }

    private void runHomePrimary() {
        if (changingOwnership || probingRoot) return;
        switch (readProgress().current()) {
            case ROOT -> requestRoot();
            case IMPORT -> startBindingImport();
            case PROFILE -> startProfileCapture();
            case ADD -> requestNative(Change.ADD);
            case DONE -> requestNative(Change.RECONNECT);
        }
    }

    // ---- binding page ----

    private void buildBindingPage() {
        LinearLayout statusCard = bindingPage.card();
        bindingStatus = bindingPage.bodyText(statusCard, Copy.BINDING_STATUS_LOADING);

        bindingPage.heading(Copy.DEVICE_MANAGEMENT);
        LinearLayout manage = bindingPage.card();
        bindingRestore = bindingPage.outlined(manage, Copy.RESTORE_MI_HEALTH, this::confirmRestore);
        bindingRemove = bindingPage.dangerOutlined(manage, Copy.REMOVE_FROM_HEALTH, () ->
                CouiConfirmDialog.show(this,
                        Copy.REMOVE_FROM_HEALTH_CONFIRM,
                        Copy.REMOVE_FROM_HEALTH_MESSAGE,
                        Copy.ACTION_CANCEL,
                        Copy.ACTION_REMOVE,
                        color(R.color.error),
                        UiStyle.contourGlow(this),
                        null,
                        () -> changeOwnership(Change.REMOVE)));
        bindingPage.caption(manage, Copy.BINDING_MANAGEMENT_CAPTION);

        bindingPage.heading(Copy.BINDING_IMPORT_TITLE);
        LinearLayout importCard = bindingPage.card();
        bindingPage.caption(importCard, Copy.BINDING_IMPORT_CAPTION);
        bonded = new LinearLayout(this);
        bonded.setOrientation(LinearLayout.VERTICAL);
        importCard.addView(bonded, new LinearLayout.LayoutParams(-1, -2));
        String preselected = getIntent().getStringExtra("deviceAddress");
        if (preselected != null && preselected.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}")) {
            selectedAddress = preselected;
        }
        openWindow = bindingPage.filled(importCard, Copy.BINDING_IMPORT_OPEN, () -> {
            if (!hostSupported) return;
            if (selectedAddress.isBlank()) {
                bindingStatus.setText(Copy.BINDING_SELECT_FIRST);
                return;
            }
            Bundle selected = new Bundle();
            selected.putString("address", selectedAddress);
            action("openWindow", selected, bindingStatus);
        });
        openWindow.setEnabled(false);
        bindingPage.outlined(importCard, Copy.BINDING_IMPORT_FROM_DB,
                () -> startBindingImport(bindingStatus));
        bindingPage.textButton(importCard, Copy.BINDING_IMPORT_CLOSE,
                () -> action("closeWindow", null, bindingStatus));
        bindingPage.navRow(importCard, Copy.BINDING_OPEN_MI_HEALTH,
                () -> launchMiHealth(bindingStatus));
    }

    private void refreshBindingStatus() {
        if (bindingStatus == null) return;
        boolean registered = new BandStateRepository(this).isRegistered();
        boolean hasBinding = false;
        String model = "";
        try {
            JSONObject binding = new BindingStore(this).read();
            hasBinding = binding != null;
            if (binding != null) model = binding.optString("model", "");
        } catch (Exception ignored) { }
        var state = LocalPrefs.open(this, "band-state");
        String name = BandCatalog.displayName(model, state.getString("name", ""));
        String text;
        if (registered) {
            text = "当前设备：" + name + "\n已由桥接接管，健康历史和绑定保存在本机。";
        } else if (hasBinding) {
            text = "绑定已导入，等待添加到健康。\n打开首次设置即可继续接管。";
        } else {
            text = "等待导入绑定。先配对手环，再从本机数据库读取。";
        }
        if (needsOfficialRestore()) text += "\n小米运动健康仍在暂停，可点「恢复官方管理」。";
        bindingStatus.setText(databaseImportNote.isEmpty() ? text : databaseImportNote);
        openWindow.setEnabled(hostSupported && rootGranted);
        bindingRemove.setVisibility(registered ? View.VISIBLE : View.GONE);
        bindingRemove.setEnabled(!changingOwnership);
        bindingRestore.setEnabled(!changingOwnership && rootGranted);
    }

    // ---- debug and permissions ----

    private void buildDebugPage() {
        debugPage.heading("协议与诊断");
        LinearLayout protocol = debugPage.card();
        debugPage.navRow(protocol, "发送桥接调试日志", this::shareDebugLog);
        debugPage.navRow(protocol, "拉取设备日志",
                () -> startActivity(new Intent(this, DeviceLogActivity.class)));
        debugPage.navRow(protocol, "协议测试", () -> startActivity(new Intent(this, LabActivity.class)));
        if (BuildConfig.DEBUG) {
            debugPage.navRow(protocol, "独立连接诊断",
                    () -> startActivity(new Intent(this, DiagnosticActivity.class)));
            debugPage.outlined(protocol, "采集连接参数（120 秒）", this::startProfileCapture);
            debugPage.outlined(protocol, "采集协议消息（120 秒）", () -> new MaterialAlertDialogBuilder(this)
                    .setTitle("采集所选手环的协议消息")
                    .setMessage("消息可能包含通知和健康数据。原始内容保存在本机私有目录，用于本次协议分析。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("开始采集", (dialog, which) -> action("openCapture", null, debugStatus))
                    .show());
        }

        debugPage.heading("状态");
        LinearLayout statusCard = debugPage.card();
        debugStatus = debugPage.bodyText(statusCard, "调试工具只在本机执行。");
    }

    private void refreshDebugStatus() {
        if (debugStatus == null) return;
        debugStatus.setText(hostSupported
                ? "小米运动健康已安装，可以采集连接参数或协议消息。"
                : "安装小米运动健康并配对手环后，即可使用采集工具。");
    }

    private void buildPermissionsPage() {
        LinearLayout hero = permissionsPage.noticeCard(Tone.ACTIVE);
        permissionsPage.title(hero, "保持手环稳定连接");
        permissionsPage.bodyText(hero,
                "附近设备权限用于连接已配对的手环；自启动与关联启动用于 ColorOS 在后台恢复桥接服务。");

        permissionsPage.heading("权限状态");
        LinearLayout card = permissionsPage.card();
        nearbyRow = permissionsPage.navRow(card, "附近设备", () -> requestPermissions(
                new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION));
        nearbyRow.setSummary("连接已配对的蓝牙手环");
        nearbyRow.setIcon(rowIcon(R.drawable.ic_nearby));
        autoStartRow = permissionsPage.navRow(card, "自启动与关联启动", this::openAutoStartSettings);
        autoStartRow.setSummary("ColorOS 后台运行");
        autoStartRow.setIcon(rowIcon(R.drawable.ic_startup));
        permissionsPage.caption("修改系统权限后返回本页即可重新检查。权限变化时，设备页会继续显示当前连接状态。");
        refreshPermissionRows();
    }

    private void refreshPermissionRows() {
        if (nearbyRow == null) return;
        nearbyRow.setTip(hasBluetoothPermission() ? "已授权" : "待授权");
        autoStartRow.setTip("系统设置");
    }

    private void openAutoStartSettings() {
        String[][] candidates = {
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.oplus.battery", "com.oplus.battery.startupapp.StartupAppListActivity"},
        };
        for (String[] candidate : candidates) {
            Intent intent = new Intent();
            intent.setClassName(candidate[0], candidate[1]);
            try {
                startActivity(intent);
                return;
            } catch (RuntimeException ignored) { }
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, "在系统设置 → 应用 → 自启动中管理", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- appearance page ----

    private void buildAppearancePage() {
        LinearLayout effects = appearancePage.card();
        appearancePage.title(effects, Copy.EFFECTS_TITLE);
        appearancePage.caption(effects, Copy.EFFECTS_CAPTION);
        BridgeScreen.ToggleRow contour = appearancePage.switchRow(effects, Copy.CONTOUR_TITLE,
                Copy.CONTOUR_CAPTION);
        contour.setChecked(UiStyle.contourGlow(this));
        contour.setOnStateChangeListener(checked -> {
            if (!UiStyle.setContourGlow(this, checked)) {
                Toast.makeText(this, Copy.CONTOUR_SAVE_INTERRUPTED, Toast.LENGTH_SHORT).show();
                return false;
            }
            screen.setContourGlowEnabled(checked);
            refreshSettingsRows();
            return true;
        });
        LinearLayout dock = appearancePage.card();
        appearancePage.title(dock, Copy.DOCK_TITLE);
        BridgeScreen.ToggleRow sync = appearancePage.switchRow(dock, Copy.SYNC_ACTION_TITLE,
                Copy.SYNC_ACTION_CAPTION);
        sync.setChecked(UiStyle.syncActionVisible(this));
        sync.setOnStateChangeListener(checked -> {
            if (!UiStyle.setSyncActionVisible(this, checked)) {
                Toast.makeText(this, Copy.DOCK_SAVE_INTERRUPTED, Toast.LENGTH_SHORT).show();
                return false;
            }
            screen.setSyncActionVisible(checked);
            refreshSettingsRows();
            return true;
        });
        appearancePage.caption(Copy.EFFECTS_APPLIED);
    }

    // ---- sleep protection page ----

    private void buildSleepProtectionPage() {
        LinearLayout intro = sleepProtectionPage.noticeCard(Tone.ACTIVE);
        sleepProtectionPage.title(intro, Copy.SLEEP_PROTECTION);
        sleepProtectionPage.bodyText(intro, Copy.SLEEP_PROTECTION_CAPTION);

        sleepProtectionPage.heading(Copy.SLEEP_PROTECTION_RANGE);
        LinearLayout windowCard = sleepProtectionPage.card();

        sleepProtectionSlider = new SleepWindowSlider(this);
        addWithMargins(windowCard, sleepProtectionSlider, 8, 4, 8, 0);
        sleepProtectionSlider.setOnRangeChangedListener((start, end, fromUser) -> {
            if (!fromUser) return;
            SleepProtectionSchedule.Window window = new SleepProtectionSchedule.Window(start, end);
            setSleepProtectionInputs(window);
            showSleepProtectionHint(Copy.sleepProtectionDuration(window), false);
        });

        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        addWithMargins(windowCard, inputs, 16, 2, 16, 8);

        sleepStartInput = sleepTimeInput();
        sleepEndInput = sleepTimeInput();
        addSleepTimeField(inputs, Copy.SLEEP_PROTECTION_START, sleepStartInput, 0, 6);
        addSleepTimeField(inputs, Copy.SLEEP_PROTECTION_END, sleepEndInput, 6, 0);

        sleepProtectionInputHint = sleepProtectionPage.caption(windowCard, "");
        sleepProtectionPage.filled(windowCard, Copy.SLEEP_PROTECTION_SAVE,
                this::saveSleepProtection);
        sleepProtectionPage.caption(
                "开始时刻可以晚于结束时刻，跨午夜时段会按同一段睡眠计算。");

        refreshSleepProtectionControls(SleepProtectionSchedule.load(this));
    }

    private AppCompatEditText sleepTimeInput() {
        AppCompatEditText input = new AppCompatEditText(this);
        input.setHint(Copy.SLEEP_PROTECTION_INPUT_HINT);
        input.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(18);
        input.setTextColor(color(R.color.on_surface));
        input.setHintTextColor(color(R.color.on_surface_variant));
        input.setPadding(dp(10), dp(10), dp(10), dp(10));
        input.setBackground(BridgeScreen.roundedRect(
                color(R.color.surface_variant), dp(12), 0, 0));
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused) applySleepProtectionInputs();
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            applySleepProtectionInputs();
            view.clearFocus();
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
            return true;
        });
        return input;
    }

    private void addSleepTimeField(LinearLayout row, String label, AppCompatEditText input,
                                   int left, int right) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(13, R.color.on_surface_variant, true);
        name.setText(label);
        field.addView(name, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(48));
        inputParams.topMargin = dp(6);
        field.addView(input, inputParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.leftMargin = dp(left);
        params.rightMargin = dp(right);
        row.addView(field, params);
    }

    private void refreshSleepProtectionControls(SleepProtectionSchedule.Window window) {
        if (sleepProtectionSlider == null) return;
        sleepProtectionSlider.setRange(window.startMinute(), window.endMinute());
        setSleepProtectionInputs(window);
        showSleepProtectionHint(Copy.sleepProtectionDuration(window), false);
    }

    private void setSleepProtectionInputs(SleepProtectionSchedule.Window window) {
        syncingSleepInputs = true;
        try {
            if (sleepStartInput != null) sleepStartInput.setText(window.startLabel());
            if (sleepEndInput != null) sleepEndInput.setText(window.endLabel());
        } finally {
            syncingSleepInputs = false;
        }
    }

    private SleepProtectionSchedule.Window readSleepProtectionInputs() {
        if (syncingSleepInputs || sleepStartInput == null || sleepEndInput == null) return null;
        Integer start = SleepProtectionSchedule.parseHour(
                sleepStartInput.getText() == null ? "" : sleepStartInput.getText().toString());
        Integer end = SleepProtectionSchedule.parseHour(
                sleepEndInput.getText() == null ? "" : sleepEndInput.getText().toString());
        if (start == null || end == null) {
            showSleepProtectionHint(Copy.SLEEP_PROTECTION_INPUT_INVALID, true);
            return null;
        }
        SleepProtectionSchedule.Window window = new SleepProtectionSchedule.Window(start, end);
        if (window.durationMinutes() < SleepProtectionSchedule.MIN_DURATION_MINUTES) {
            showSleepProtectionHint(Copy.SLEEP_PROTECTION_TOO_SHORT, true);
            return null;
        }
        return window;
    }

    private void applySleepProtectionInputs() {
        SleepProtectionSchedule.Window window = readSleepProtectionInputs();
        if (window == null) return;
        sleepProtectionSlider.setRange(window.startMinute(), window.endMinute());
        setSleepProtectionInputs(window);
        showSleepProtectionHint(Copy.sleepProtectionDuration(window), false);
    }

    private void saveSleepProtection() {
        SleepProtectionSchedule.Window window = readSleepProtectionInputs();
        if (window == null) return;
        if (!SleepProtectionSchedule.save(this, window)) {
            showSleepProtectionHint(Copy.SLEEP_PROTECTION_SAVE_INTERRUPTED, true);
            return;
        }
        sleepProtectionSlider.setRange(window.startMinute(), window.endMinute());
        setSleepProtectionInputs(window);
        showSleepProtectionHint(Copy.SLEEP_PROTECTION_SAVED, false);
        refreshSettingsRows();
        BandLiveService.sleepProtectionChanged(this);
        Toast.makeText(this, Copy.SLEEP_PROTECTION_SAVED, Toast.LENGTH_SHORT).show();
    }

    private void showSleepProtectionHint(String message, boolean attention) {
        if (sleepProtectionInputHint == null) return;
        sleepProtectionInputHint.setText(message);
        sleepProtectionPage.setTone(sleepProtectionInputHint,
                attention ? Tone.ATTENTION : Tone.ACTIVE);
    }

    // ---- about page ----

    private void buildAboutPage() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.setBackground(BridgeScreen.roundedRect(
                color(R.color.accent), dp(24), 0, 0));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        iconParams.rightMargin = dp(16);
        header.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(20, R.color.on_primary, true);
        name.setText(Copy.APP_NAME);
        labels.addView(name);
        TextView version = text(13, R.color.on_primary, false);
        version.setText(Copy.VERSION_PREFIX + BuildConfig.VERSION_NAME);
        version.setAlpha(0.82f);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-2, -2);
        versionParams.topMargin = dp(4);
        labels.addView(version, versionParams);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        addWithMargins(aboutPage.body, header, 16, 16, 16, 8);
    }

    // ---- lifecycle ----

    @Override protected void onResume() {
        super.onResume();
        refreshNotices();
        refreshTools();
        refreshPermissionRows();
        refreshSettingsRows();
        promptHostVersions();
        if (!rootGranted) requestRoot();
        else continueInit();
    }

    private void continueInit() {
        refreshOwnership();
        refreshDevice();
        refreshBonded();
        refreshBindingStatus();
        if (hostSupported) refresh();
        else worker.execute(() -> {
            boolean supported = HostIdentity.installed(this, HostIdentity.MI_PACKAGE);
            main.post(() -> {
                if (isDestroyed()) return;
                hostSupported = supported;
                if (openWindow != null) openWindow.setEnabled(supported && rootGranted);
                refreshDebugStatus();
                refreshBindingStatus();
                if (supported) refresh();
            });
        });
        ensureCallPermissions();
    }

    @Override protected void onDestroy() {
        finishManualSyncFeedback();
        if (syncReceiverRegistered) {
            unregisterReceiver(manualSyncReceiver);
            syncReceiverRegistered = false;
        }
        getContentResolver().unregisterContentObserver(observer);
        getContentResolver().unregisterContentObserver(deviceObserver);
        main.removeCallbacks(expireRefresh);
        worker.shutdown();
        super.onDestroy();
    }

    // ---- notices ----

    private void refreshNotices() {
        if (nearbyNotice == null) return;
        setShown(nearbyNotice, !hasBluetoothPermission());
        setShown(notifyNotice, !getSystemService(NotificationManager.class).areNotificationsEnabled());
        LocalPrefs prefs = LocalPrefs.open(this, BandNotificationListener.SETTINGS);
        boolean healthAccess = prefs.getBoolean("healthSecure", false)
                && !prefs.getBoolean("healthApproved", true)
                && prefs.getBoolean("healthGrantFailed", false);
        setShown(healthAccessNotice, healthAccess);
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (RuntimeException fallback) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openHealthListenerSettings() {
        Intent detail = new Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS");
        detail.putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME",
                "com.heytap.health/com.heytap.health.watch.commonnotification.HeytapNotificationListenerService");
        try {
            startActivity(detail);
        } catch (RuntimeException failure) {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (RuntimeException ignored) {
                showStatus("在系统设置 → 通知 → 通知使用权中找到 OPPO 健康。");
            }
        }
    }

    private void openBluetoothSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (RuntimeException unavailable) {
            showSetupGuide();
        }
    }

    // ---- help ----

    private void showSetupGuide() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("使用说明")
                .setMessage(Copy.GUIDE)
                .setPositiveButton("好的", null)
                .show();
    }

    private void shareDebugLog() {
        worker.execute(() -> {
            try {
                java.io.File file = io.github.miam1ku.mibandoplusbridge.data.SessionLog.export(this);
                Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                        "io.github.miam1ku.mibandoplusbridge.debuglog", file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/zip");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, Copy.DEBUG_LOG_SUBJECT);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent qq = new Intent(send);
                qq.setPackage("com.tencent.mobileqq");
                main.post(() -> {
                    if (isDestroyed()) return;
                    if (qq.resolveActivity(getPackageManager()) != null) startActivity(qq);
                    else startActivity(Intent.createChooser(send, "发送桥接调试日志"));
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (!isDestroyed()) Toast.makeText(this,
                            "调试日志生成中断，稍后再试一次", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void promptHostVersions() {
        if (isDestroyed() || versionPromptShown) return;
        ArrayList<HostVersionNotice.Installed> installed = new ArrayList<>();
        for (HostVersionNotice.Expectation expect : HostVersionNotice.EXPECTATIONS) {
            try {
                var info = getPackageManager().getPackageInfo(expect.packageName(), 0);
                installed.add(new HostVersionNotice.Installed(expect.packageName(), info.versionName,
                        info.getLongVersionCode(), true));
            } catch (PackageManager.NameNotFoundException missing) {
                installed.add(new HostVersionNotice.Installed(expect.packageName(), "", 0, false));
            }
        }
        String finger = HostVersionNotice.fingerprint(installed);
        if (finger.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("host-version-notice", MODE_PRIVATE);
        if (finger.equals(prefs.getString("dismissed", ""))) return;
        versionPromptShown = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle("建议使用实测版本")
                .setMessage(HostVersionNotice.message(installed))
                .setCancelable(true)
                .setPositiveButton("好的", (dialog, which) -> prefs.edit().putString("dismissed", finger).apply())
                .setOnCancelListener(dialog -> prefs.edit().putString("dismissed", finger).apply())
                .show();
    }

    // ---- root ----

    private void requestRoot() {
        if (worker.isShutdown() || probingRoot || changingOwnership) return;
        probingRoot = true;
        showStatus("正在检查 Root 授权…");
        worker.execute(() -> {
            boolean granted = new OwnershipController(this).probeRoot();
            main.post(() -> {
                if (isDestroyed()) return;
                probingRoot = false;
                rootGranted = granted;
                if (granted) hideStatus();
                else showStatus(Copy.setupHint(SetupProgress.Step.ROOT));
                updateControls();
                refreshDevice();
                if (granted) continueInit();
            });
        });
    }

    private void openKernelSu() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("me.weishu.kernelsu");
        if (launch == null) {
            showStatus("安装 Root 管理器后，为本应用打开超级用户。");
            return;
        }
        startActivity(launch);
    }

    // ---- ownership ----

    private void refreshOwnership() {
        if (worker.isShutdown() || changingOwnership || !rootGranted) return;
        worker.execute(() -> {
            if (!isUnlocked()) return;
            OwnershipController owner = new OwnershipController(this);
            BandStateRepository repository = new BandStateRepository(this);
            String message = "";
            try {
                boolean registered = repository.isRegistered();
                boolean ready = owner.nativeReady();
                if (!registered) {
                    BandLiveService.stop(this);
                    if (!SppDiagnosticClient.stopAllAndWait()) {
                        throw new OwnershipController.Failure("DIAGNOSTIC_SOCKET_STILL_ACTIVE");
                    }
                    repository.disconnected();
                }
                if (registered && ready && hasBluetoothPermission()) {
                    BandLiveService.start(this);
                }
                if (!registered && needsOfficialRestore()) {
                    message = "小米运动健康仍在暂停。可以添加设备，或在「绑定与设备信息」中恢复。";
                } else if (registered && !"NATIVE".equals(owner.mode())) {
                    message = "当前由小米运动健康管理，点「同步剩余项目」重新接管。";
                }
            } catch (Exception failure) {
                message = repository.isRegistered()
                        ? "设备登记已保留。点「同步剩余项目」再连接一次，或恢复官方管理。"
                        : Copy.RESTORE_MI_HEALTH_RETRY;
            }
            String idle = message;
            main.post(() -> {
                if (isDestroyed()) return;
                if (idle.isEmpty()) hideStatus();
                else showStatus(idle);
                updateControls();
            });
        });
    }

    private void changeOwnership(Change change) {
        changeOwnership(change, false, false);
    }

    private void changeOwnership(Change change, boolean manualSync) {
        changeOwnership(change, manualSync, false);
    }

    private void changeOwnership(Change change, boolean manualSync, boolean queuedSyncNotice) {
        if (worker.isShutdown() || changingOwnership) return;
        if (!rootGranted) {
            requestRoot();
            return;
        }
        if (!isUnlocked()) {
            showStatus("解锁手机后再管理设备。");
            return;
        }
        String requestId = manualSync ? beginManualSyncFeedback() : "";
        changingOwnership = true;
        updateControls();
        showStatus(manualSync ? "正在连接并同步…" : "正在处理，请稍候…");
        worker.execute(() -> {
            OwnershipController owner = new OwnershipController(this);
            BandStateRepository repository = new BandStateRepository(this);
            String message;
            boolean registrationSaved = false;
            boolean removalSaved = false;
            boolean officialRestored = false;
            boolean[] syncAccepted = {false};
            try {
                if (change == Change.ADD || change == Change.RECONNECT) {
                    if (!isUnlocked()) throw new IllegalStateException("USER_LOCKED");
                    if (!owner.probeRoot()) throw new OwnershipController.Failure("ROOT_REQUIRED");
                    if (!hasBluetoothPermission()) throw new IllegalStateException("NEARBY_PERMISSION_REQUIRED");
                    if (change == Change.ADD) {
                        repository.registerDevice();
                        registrationSaved = true;
                    }
                    if (!repository.isRegistered()) throw new IllegalStateException("DEVICE_NOT_REGISTERED");
                    if (!owner.nativeReady()) owner.takeOver();
                    if (!repository.isRegistered() || !owner.nativeReady()) {
                        throw new IllegalStateException("NATIVE_OWNERSHIP_REQUIRED");
                    }
                    BandLiveService.start(this);
                    String accepted = manualSync
                            ? BandLiveService.requestManualSync(this, requestId)
                            : BandLiveService.requestSync(this);
                    if (!"ACCEPTED".equals(accepted)) throw new IllegalStateException(accepted);
                    syncAccepted[0] = true;
                    message = manualSync ? "正在连接并同步。" : "设备已登记，正在连接。";
                } else {
                    if (change == Change.REMOVE) {
                        repository.unregisterDevice();
                        removalSaved = true;
                        main.post(this::refreshDevice);
                    }
                    BandLiveService.stop(this);
                    if (!SppDiagnosticClient.stopAllAndWait()) {
                        throw new OwnershipController.Failure("DIAGNOSTIC_SOCKET_STILL_ACTIVE");
                    }
                    repository.disconnected();
                    owner.restoreOfficial();
                    officialRestored = change == Change.RESTORE;
                    message = change == Change.REMOVE
                            ? "已移除，小米运动健康已恢复。绑定和健康历史都已保留。"
                            : "小米运动健康已恢复。绑定和健康历史都已保留。";
                }
            } catch (Exception failure) {
                String hint = Copy.ownershipFailure(failureCode(failure));
                if (removalSaved || (change == Change.RESTORE && !repository.isRegistered())) {
                    message = "已移除，小米运动健康待恢复。" + hint;
                } else if (change == Change.REMOVE) {
                    message = "设备仍在登记中，再点一次「从健康移除」。";
                } else if (registrationSaved || (change == Change.RECONNECT && repository.isRegistered())) {
                    message = "设备登记已保留，连接稍后继续。" + hint;
                } else {
                    message = hint;
                }
            }
            String visible = queuedSyncNotice && syncAccepted[0]
                    ? "当前同步已排队，睡眠保护解除后继续。" : message;
            boolean followProfile = openProfileAfterRestore(change, officialRestored);
            main.post(() -> {
                if (isDestroyed()) return;
                changingOwnership = false;
                if (manualSync && !syncAccepted[0]) finishManualSyncFeedback();
                showStatus(visible);
                updateControls();
                refreshDevice();
                refreshBindingStatus();
                if (change == Change.ADD && repository.isRegistered() && secondaryPage) {
                    showPrimaryTab(R.id.tab_device, true, PageTransition.BACK);
                }
                if (profileAfterRestore && followProfile) {
                    profileAfterRestore = false;
                    startProfileWindow();
                }
            });
        });
    }

    private static String failureCode(Exception failure) {
        return failure instanceof OwnershipController.Failure
                ? ((OwnershipController.Failure) failure).code : failure.getMessage();
    }

    private void requestNative(Change change) {
        if (changingOwnership) return;
        if (!rootGranted) {
            requestRoot();
            return;
        }
        if (!isUnlocked()) {
            showStatus("解锁手机后再管理设备。");
            return;
        }
        if (!hasBluetoothPermission()) {
            pendingPermission = change;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION);
            return;
        }
        if (change == Change.RECONNECT) {
            confirmManualSync();
            return;
        }
        CouiConfirmDialog.show(this,
                "添加到健康并接管？",
                "小米运动健康会暂停，由桥接接管手环。开始前请结束表盘安装、OTA 升级和 NFC 操作。"
                        + "绑定和健康历史都会保留。",
                Copy.ACTION_CANCEL,
                "添加并接管",
                color(R.color.accent),
                UiStyle.contourGlow(this),
                null,
                () -> changeOwnership(change));
    }

    private void confirmManualSync() {
        java.time.LocalTime now = java.time.LocalTime.now();
        int minuteOfDay = now.getHour() * 60 + now.getMinute();
        if (!BandLiveService.isSleepProtectionWindow(this, minuteOfDay)) {
            changeOwnership(Change.RECONNECT, true);
            return;
        }
        CouiConfirmDialog.show(this,
                "立即同步健康记录？",
                "当前处于自定义睡眠保护时段。立即同步可能结束手环当前的睡眠文件，"
                        + "导致睡眠图出现短暂清醒。是否仍要同步？",
                "等待自动同步",
                "仍然同步",
                color(R.color.accent),
                UiStyle.contourGlow(this),
                null,
                () -> changeOwnership(Change.RECONNECT, true));
    }

    private String beginManualSyncFeedback() {
        manualSyncAwaitingCompletion = true;
        manualSyncRequestId = UUID.randomUUID().toString();
        if (screen != null) screen.setSyncActionSpinning(true);
        main.removeCallbacks(manualSyncConnectTimeout);
        main.postDelayed(manualSyncConnectTimeout, 120_000L);
        return manualSyncRequestId;
    }

    private void finishManualSyncFeedback() {
        manualSyncAwaitingCompletion = false;
        manualSyncRequestId = "";
        main.removeCallbacks(manualSyncConnectTimeout);
        if (screen != null) screen.setSyncActionSpinning(false);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CALL_PERMISSIONS) {
            BandLiveService.refreshNotificationSettings(this);
            return;
        }
        if (requestCode != BLUETOOTH_PERMISSION) return;
        Change requested = pendingPermission;
        pendingPermission = null;
        refreshNotices();
        refreshPermissionRows();
        refreshDevice();
        refreshBonded();
        if (hasBluetoothPermission()) {
            if (requested != null) requestNative(requested);
            else refreshOwnership();
        } else {
            showStatus("授权附近设备后即可添加和连接，已有登记保持原样。");
        }
    }

    private void ensureCallPermissions() {
        if (callPermissionsAsked || !isUnlocked() || !new BandStateRepository(this).isRegistered()) return;
        boolean phone = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hangup = checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED;
        if (phone && hangup) return;
        callPermissionsAsked = true;
        if (!phone && !hangup) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS}, CALL_PERMISSIONS);
        } else if (!phone) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, CALL_PERMISSIONS);
        } else {
            requestPermissions(new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, CALL_PERMISSIONS);
        }
    }

    private boolean isUnlocked() {
        return getSystemService(UserManager.class).isUserUnlocked()
                && !getSystemService(KeyguardManager.class).isDeviceLocked();
    }

    private boolean hasBluetoothPermission() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean needsOfficialRestore() {
        var ownership = LocalPrefs.open(this, "ownership");
        return ownership.getBoolean("ownsDisable", false) || ownership.getBoolean("transitionPending", false);
    }

    // ---- controls ----

    private void updateControls() {
        boolean registered = new BandStateRepository(this).isRegistered();
        SetupProgress progress = readProgress();
        for (View view : boundViews) view.setVisibility(registered ? View.VISIBLE : View.GONE);
        for (View view : unboundViews) view.setVisibility(registered ? View.GONE : View.VISIBLE);
        if (setupPrimary != null) {
            setupPrimary.setText(progress.primaryLabel());
            setupPrimary.setEnabled(!changingOwnership && !probingRoot);
            bindChecklist(progress);
        }
        if (openKsu != null) {
            openKsu.setVisibility(rootGranted ? View.GONE : View.VISIBLE);
            openKsu.setEnabled(!probingRoot);
        }
        if (syncButton != null) syncButton.setEnabled(!changingOwnership && !probingRoot);
        if (deviceDetailRow != null) {
            deviceDetailRow.setVisibility(registered ? View.VISIBLE : View.GONE);
        }
        if (bindingRemove != null) {
            bindingRemove.setVisibility(registered ? View.VISIBLE : View.GONE);
            bindingRemove.setEnabled(!changingOwnership);
        }
        if (bindingRestore != null) bindingRestore.setEnabled(!changingOwnership && rootGranted);
        if (openWindow != null) {
            openWindow.setEnabled(!changingOwnership && rootGranted && hostSupported);
        }
    }

    private SetupProgress readProgress() {
        boolean registered = new BandStateRepository(this).isRegistered();
        boolean hasBinding = false;
        String model = "";
        try {
            JSONObject binding = new BindingStore(this).read();
            hasBinding = binding != null;
            if (binding != null) model = binding.optString("model", "");
        } catch (Exception ignored) { }
        boolean hasProfile = hasBinding && TransportObservation.supportsLive(
                TransportObservation.liveProfile(this, model), model);
        boolean nativeOwned = false;
        try {
            nativeOwned = new OwnershipController(this).nativeReady();
        } catch (RuntimeException ignored) { }
        boolean accountConfirmed = false;
        try {
            accountConfirmed = new HealthRecordStore(this).confirmedAccountHash() != null;
        } catch (Exception ignored) { }
        return new SetupProgress(rootGranted, hasBinding, hasProfile, registered, nativeOwned, accountConfirmed);
    }

    // ---- credential provider ----

    private void action(String method, Bundle arguments, TextView target) {
        if (isDestroyed() || worker.isShutdown()) return;
        worker.execute(() -> {
            try {
                Bundle response = getContentResolver().call(CredentialProvider.URI, method, null, arguments);
                main.post(() -> {
                    if (isDestroyed()) return;
                    show(response, target);
                    if ("openWindow".equals(method) || "openDiagnostics".equals(method)
                            || "openCapture".equals(method)) {
                        main.removeCallbacks(expireRefresh);
                        main.postDelayed(expireRefresh, 120_100);
                    }
                });
            } catch (RuntimeException rejected) {
                main.post(() -> {
                    if (!isDestroyed() && target != null) {
                        target.setText("解锁手机、核对所选设备后再点一次。");
                    }
                });
            }
        });
    }

    private void refresh() { action("status", null, bindingStatus); }

    private void show(Bundle response, TextView target) {
        if (target == null) return;
        if (response == null) {
            target.setText("导入服务正在启动，稍后再点一次。");
            return;
        }
        String code = response.getString("status", "CREDENTIAL_STORAGE_FAILED");
        String text = Copy.importResult(code, response.getString("model", ""),
                response.getString("productId", ""), response.getString("firmware", ""));
        String observation = response.getString("transportObservation", "");
        if (!observation.isBlank()) text += "\n连接参数（仅观察）：\n" + observation;
        if (response.getBoolean("diagnosticOpen", false)) text += "\n连接参数采集窗口开启中。";
        if (response.getBoolean("captureOpen", false)) text += "\n协议消息采集窗口开启中。";
        String captureFile = response.getString("captureFile", "");
        if (!captureFile.isBlank()) text += "\n采集记录：" + response.getInt("captureRecords")
                + "，字节：" + response.getLong("captureBytes") + "\n文件：" + captureFile;
        String captureError = response.getString("captureError", "");
        if (!captureError.isBlank()) text += "\n采集中断：" + captureError;
        target.setText(text);
        if ("IMPORT_WINDOW_OPEN".equals(code) || "DIAGNOSTIC_WINDOW_OPEN".equals(code)) {
            showStatus(text);
            if (launchHostAfterOpen) {
                launchHostAfterOpen = false;
                if (hostSupported) launchMiHealth(target);
            }
        }
    }

    private void launchMiHealth(TextView target) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(HostIdentity.MI_PACKAGE);
        if (launch == null) {
            String text = "在桌面打开小米运动健康，确认它已安装并启用。";
            if (target != null) target.setText(text);
            showStatus(text);
            return;
        }
        startActivity(launch);
    }

    // ---- bonded devices ----

    private void refreshBonded() {
        if (bonded == null) return;
        bonded.removeAllViews();
        if (!hasBluetoothPermission()) {
            bindingPage.caption(bonded, "授权附近设备后，这里会列出已配对设备。");
            return;
        }
        BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
        if (adapter == null) {
            bindingPage.caption(bonded, "这台手机需要蓝牙才能连接手环。");
            return;
        }
        java.util.Set<BluetoothDevice> devices;
        try {
            devices = adapter.getBondedDevices();
        } catch (SecurityException denied) {
            bindingPage.caption(bonded, "授权附近设备后，这里会列出已配对设备。");
            return;
        }
        if (devices == null || devices.isEmpty()) {
            bindingPage.caption(bonded, "在系统蓝牙设置中配对手环后，它会出现在这里。");
            return;
        }
        bindingPage.caption(bonded, "选择已配对设备");
        List<BluetoothDevice> listed = sortedBonded(devices);
        preferXiaomiBand(listed);
        for (BluetoothDevice device : listed) {
            String mac = device.getAddress() == null ? ""
                    : device.getAddress().toUpperCase(Locale.ROOT);
            String name = BandCatalog.displayName(null, device.getName());
            boolean chosen = !mac.isBlank() && mac.equals(selectedAddress);
            BridgeScreen.ActionRow choice = bindingPage.outlined(bonded,
                    (chosen ? "✓ " : "") + name, () -> {
                        selectedAddress = mac;
                        refreshBonded();
                    });
            choice.setEnabled(!chosen);
        }
    }

    private static List<BluetoothDevice> sortedBonded(java.util.Set<BluetoothDevice> devices) {
        ArrayList<BluetoothDevice> listed = new ArrayList<>(devices);
        listed.sort(Comparator.comparing((BluetoothDevice device) -> !BandCatalog.looksLikeBand(device.getName()))
                .thenComparing(device -> BandCatalog.displayName(null, device.getName()),
                        String.CASE_INSENSITIVE_ORDER));
        return listed;
    }

    private void preferXiaomiBand(List<BluetoothDevice> devices) {
        if (!selectedAddress.isBlank()) return;
        String onlyBand = null;
        int bands = 0;
        for (BluetoothDevice device : devices) {
            if (!BandCatalog.looksLikeBand(device.getName())) continue;
            String mac = device.getAddress();
            if (mac == null || mac.isBlank()) continue;
            bands++;
            onlyBand = mac.toUpperCase(Locale.ROOT);
        }
        if (bands == 1) {
            selectedAddress = onlyBand;
            return;
        }
        if (devices.size() != 1) return;
        String only = devices.get(0).getAddress();
        if (only != null) selectedAddress = only.toUpperCase(Locale.ROOT);
    }

    // ---- database import ----

    private void startBindingImport() {
        if (changingOwnership) return;
        if (!rootGranted) {
            requestRoot();
            return;
        }
        if (!isUnlocked()) {
            showStatus("解锁手机后再导入绑定。");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("导入绑定？")
                .setMessage("用 root 读取小米运动健康本地数据库中的手环密钥。全程在本机完成，密钥保存在本应用的私有目录里。")
                .setNegativeButton("取消", null)
                .setPositiveButton("读取", (dialog, which) -> importFromDatabase(selectedAddress))
                .show();
    }

    private void startBindingImport(TextView target) {
        startBindingImport();
    }

    private void importFromDatabase(String address) {
        if (worker.isShutdown()) return;
        databaseImportNote = "";
        showStatus("正在读取本地数据库…");
        worker.execute(() -> {
            Bundle selected = new Bundle();
            if (address != null && !address.isBlank()) selected.putString("address", address);
            Bundle response;
            try {
                response = getContentResolver().call(CredentialProvider.URI, "importFromDatabase", null, selected);
            } catch (RuntimeException rejected) {
                response = null;
            }
            Bundle shown = response;
            main.post(() -> {
                if (isDestroyed()) return;
                if (shown != null && "MULTIPLE_BANDS".equals(shown.getString("status"))) {
                    chooseDatabaseBand(shown.getStringArray("choices"));
                    return;
                }
                show(shown, bindingStatus);
                databaseImportNote = bindingStatus.getText().toString();
                showStatus(databaseImportNote);
                updateControls();
                refreshDevice();
            });
        });
    }

    private void chooseDatabaseBand(String[] choices) {
        if (choices == null || choices.length == 0) {
            bindingStatus.setText(Copy.importResult("NO_BAND_KEY", "", "", ""));
            return;
        }
        String[] labels = new String[choices.length];
        String[] addresses = new String[choices.length];
        for (int index = 0; index < choices.length; index++) {
            String[] parts = choices[index].split("\t", 3);
            addresses[index] = parts.length > 0 ? parts[0] : "";
            String model = parts.length > 1 ? parts[1] : "";
            String name = parts.length > 2 ? parts[2] : "";
            labels[index] = BandCatalog.displayName(model, name);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择要导入的手环")
                .setItems(labels, (dialog, which) -> importFromDatabase(addresses[which]))
                .show();
    }

    // ---- connection profile capture ----

    private void startProfileCapture() {
        if (changingOwnership) return;
        if (!rootGranted) {
            requestRoot();
            return;
        }
        if (!isUnlocked()) {
            showStatus("解锁手机后再采集连接参数。");
            return;
        }
        if (!hostSupported) {
            showStatus("安装小米运动健康并配对手环后，即可采集连接参数。");
            return;
        }
        if (needsOfficialRestore()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("先恢复官方管理")
                    .setMessage("采集连接参数时要用到小米运动健康。先恢复官方管理，随后会打开采集窗口。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("恢复", (dialog, which) -> {
                        profileAfterRestore = true;
                        changeOwnership(Change.RESTORE);
                    })
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("采集连接参数？")
                .setMessage("将开启 120 秒采集窗口，随后打开小米运动健康。点开这只手环，让它重新连上。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始采集", (dialog, which) -> startProfileWindow())
                .show();
    }

    private void startProfileWindow() {
        launchHostAfterOpen = true;
        action("openDiagnostics", null, debugStatus);
    }

    // ---- small view helpers ----

    private TextView text(int sp, int colorResource, boolean medium) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(color(colorResource));
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private Drawable rowIcon(int resource) {
        Drawable icon = ContextCompat.getDrawable(this, resource);
        if (icon == null) return null;
        icon = icon.mutate();
        icon.setTint(color(R.color.on_surface_variant));
        return icon;
    }

    private LinearLayout.LayoutParams textParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(left);
        params.topMargin = dp(top);
        params.rightMargin = dp(right);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private void addWithMargins(LinearLayout parent, View child,
                                int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(left);
        params.topMargin = dp(top);
        params.rightMargin = dp(right);
        params.bottomMargin = dp(bottom);
        parent.addView(child, params);
    }

    private static void setOptional(TextView view, String value) {
        boolean show = value != null && !value.isBlank();
        view.setVisibility(show ? View.VISIBLE : View.GONE);
        view.setText(show ? value : "");
    }

    private static String safeIdentity(String value, String fallback) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        return clean.isEmpty() || clean.length() > 80 ? fallback : clean;
    }

    private static void setShown(LinearLayout cardInner, boolean shown) {
        cardInner.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    private int color(int resource) {
        return ContextCompat.getColor(this, resource);
    }

    private int dp(int value) {
        return BridgeScreen.dp(this, value);
    }
}
