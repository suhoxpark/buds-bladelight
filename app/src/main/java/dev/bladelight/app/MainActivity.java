package dev.bladelight.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Daily-use presentation. Bluetooth work remains owned by the application controller. */
public final class MainActivity extends Activity {
    private static final String UI_PREFS = "blade_appearance";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private StateStore store;
    private BladeController controller;
    private BluetoothAdapter adapter;
    private LinearLayout body;
    private Switch automatic;
    private BladeSheet activeSheet;
    private TextView deviceLabel, banner;
    private Button on, off;
    private boolean dark, rendering, resumed, destroyed, settingsPage;
    private int bg, surface, ink, muted, accent;
    private String error = "";
    private enum Action { ON, OFF, RECOVER }
    private boolean backRegistered;
    private android.window.OnBackInvokedCallback settingsBack;
    private final Runnable ticker = new Runnable() {
        public void run() { if (resumed && !destroyed) { render(); ui.postDelayed(this, 600); } }
    };

    @Override protected void attachBaseContext(Context base) {
        int choice = base.getSharedPreferences(UI_PREFS, MODE_PRIVATE).getInt("theme", 0);
        if (choice != 0) {
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (choice == 2 ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            base = base.createConfigurationContext(config);
        }
        super.attachBaseContext(base);
    }

    @Override public void onCreate(Bundle saved) {
        setTheme(R.style.AppTheme);
        super.onCreate(saved);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = Color.parseColor(dark ? "#08090B" : "#F2F3F5");
        surface = Color.parseColor(dark ? "#15171B" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#F4F6FA" : "#171A20");
        muted = Color.parseColor(dark ? "#969DAA" : "#636A77");
        accent = Color.parseColor(dark ? "#E4EBF5" : "#202B3D");
        store = new StateStore(this);
        controller = BladeController.get(this);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        if (saved != null) { settingsPage = saved.getBoolean("settings"); error = saved.getString("error", ""); }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().getDecorView(); // Materialize decor before requesting its insets controller.
        getWindow().getInsetsController().setSystemBarsAppearance(dark ? 0 :
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        buildPage();
    }

    private void buildPage() {
        automatic = null; on = null; off = null; banner = null; deviceLabel = null;
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(bg);
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = column(); body.setPadding(dp(24), 0, dp(24), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        // Keep the same comfortable line length in landscape, split screen and tablets.
        FrameLayout.LayoutParams content = new FrameLayout.LayoutParams(
                getResources().getConfiguration().screenWidthDp > 600 ? dp(560) : -1, -1, Gravity.CENTER_HORIZONTAL);
        frame.addView(scroll, content); setContentView(frame); frame.requestApplyInsets();
        if (settingsPage) buildSettings(); else buildHome();
        if (Build.VERSION.SDK_INT >= 33) {
            if (settingsBack == null) settingsBack = () -> { settingsPage = false; buildPage(); };
            if (backRegistered) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(settingsBack);
            backRegistered = settingsPage;
            if (backRegistered) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, settingsBack);
        }
        render();
    }

    private void buildHome() {
        FrameLayout toolbar = new FrameLayout(this);
        TextView title = text("BladeLight",29,ink,"sans-serif-medium");
        title.setLetterSpacing(-0.045f); title.setAccessibilityHeading(true);
        title.setMaxWidth(dp(210)); title.setMaxLines(1);
        title.setAutoSizeTextTypeUniformWithConfiguration(22,29,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        toolbar.addView(title,new FrameLayout.LayoutParams(-2,-2,Gravity.START | Gravity.CENTER_VERTICAL));
        ImageButton settings = iconButton("settings",getString(R.string.settings));
        toolbar.addView(settings,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END | Gravity.CENTER_VERTICAL));
        settings.setOnClickListener(v -> { settingsPage = true; error = ""; buildPage(); });
        body.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(76)));
        deviceLabel = text("",14,muted,"sans-serif-medium");
        deviceLabel.setGravity(Gravity.CENTER_VERTICAL); deviceLabel.setMaxLines(2); deviceLabel.setEllipsize(TextUtils.TruncateAt.END);
        deviceLabel.setPadding(dp(16),dp(12),dp(14),dp(12)); deviceLabel.setMinHeight(dp(52));
        BladeIcon buds = new BladeIcon("buds",muted); buds.setBounds(0,0,dp(22),dp(22));
        BladeIcon chevron = new BladeIcon("next",muted); chevron.setBounds(0,0,dp(16),dp(16));
        deviceLabel.setCompoundDrawablesRelative(buds,null,chevron,null); deviceLabel.setCompoundDrawablePadding(dp(12));
        deviceLabel.setBackground(ripple(surface,18)); deviceLabel.setOnClickListener(v -> chooseDevice());
        body.addView(deviceLabel,new LinearLayout.LayoutParams(-1,-2)); space(20);
        BladeArtworkView artwork = new BladeArtworkView(this, dark);
        body.addView(artwork,new LinearLayout.LayoutParams(-1,dp(280),1)); space(24);
        LinearLayout commands = new LinearLayout(this);
        on = action(getString(R.string.on),getString(R.string.turn_on),"sun",true); off = action(getString(R.string.off),getString(R.string.turn_off),"power",false);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0,dp(64),1); a.setMarginEnd(dp(6));
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0,dp(64),1); b.setMarginStart(dp(6));
        commands.addView(on,a); commands.addView(off,b); body.addView(commands);
        on.setOnClickListener(v -> command(true)); off.setOnClickListener(v -> command(false)); space(20);
        LinearLayout autoCard = new LinearLayout(this); autoCard.setGravity(Gravity.CENTER_VERTICAL);
        autoCard.setPadding(dp(18),dp(16),dp(18),dp(16)); autoCard.setMinimumHeight(dp(80));
        autoCard.setBackground(shape(surface,22));
        ImageView autoIcon = new ImageView(this); autoIcon.setImageDrawable(new BladeIcon("auto",muted));
        autoIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams autoIconSize = new LinearLayout.LayoutParams(dp(24),dp(24)); autoIconSize.setMarginEnd(dp(14));
        autoCard.addView(autoIcon,autoIconSize);
        LinearLayout autoLabels = column(); autoLabels.addView(text(getString(R.string.auto_on),16,ink,"sans-serif-medium"));
        autoLabels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        autoCard.addView(autoLabels,new LinearLayout.LayoutParams(0,-2,1));
        automatic = new Switch(this); automatic.setContentDescription(getString(R.string.auto_on));
        automatic.setPadding(dp(14),dp(6),0,dp(6)); automatic.setMinHeight(dp(48)); automatic.setMinimumWidth(dp(62));
        automatic.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                new int[]{dark ? 0xFF283446 : Color.WHITE,dark ? 0xFF989FAC : Color.WHITE}));
        automatic.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                new int[]{accent,dark ? 0xFF424853 : 0xFFA5ACB8}));
        autoCard.addView(automatic,new LinearLayout.LayoutParams(-2,-2)); body.addView(autoCard);
        autoCard.setOnClickListener(v -> { if (automatic.isEnabled()) automatic.toggle(); });
        automatic.setOnCheckedChangeListener((button, enabled) -> {
            if (rendering) return;
            try {
                store.setAutoEnabled(enabled);
                if (enabled) {
                    toast(getString(R.string.auto_next));
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
                }
            } catch (Exception e) { error = getString(R.string.save_failed); }
            render();
        });
        banner = text("", 14, muted, "sans-serif"); banner.setGravity(Gravity.CENTER); banner.setMinHeight(dp(48));
        banner.setPadding(dp(8), dp(16), dp(8), dp(8)); banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(banner); banner.setOnClickListener(v -> resolveProblem());
    }

    private void buildSettings() {
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton("back", getString(R.string.back)); toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));
        back.setOnClickListener(v -> { settingsPage = false; buildPage(); });
        TextView heading = text(getString(R.string.settings), 22, ink, "sans-serif-medium"); heading.setAccessibilityHeading(true);
        toolbar.addView(heading); body.addView(toolbar); space(24);
        LinearLayout card = column(); card.setBackground(shape(surface, 28));
        card.addView(settingRow("buds", getString(R.string.buds), store.registeredAddress().isEmpty() ? getString(R.string.select_buds) : store.registeredName(), this::chooseDevice));
        divider(card);
        String[] choices = {getString(R.string.system_theme), getString(R.string.light_theme), getString(R.string.dark_theme)};
        card.addView(settingRow("theme", getString(R.string.theme), choices[themeChoice()], this::chooseTheme)); body.addView(card); space(20);
        LinearLayout help = column(); help.setBackground(shape(surface, 28));
        help.addView(settingRow("info", getString(R.string.help), "", () -> {
            BladeSheet sheet = sheet("info",getString(R.string.help),"");
            sheet.addMessage(getString(R.string.help_text));
            sheet.addButton(getString(R.string.ok),true,null); sheet.show();
        }));
        body.addView(help);
        TextView version = text(getString(R.string.version, appVersion()), 13, muted, "sans-serif"); version.setGravity(Gravity.CENTER); version.setPadding(0,dp(32),0,0); body.addView(version);
    }

    private View settingRow(String icon, String title, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(82));
        row.setPadding(dp(20), dp(18), dp(16), dp(18)); row.setBackground(ripple(Color.TRANSPARENT, 26));
        ImageView mark = new ImageView(this); mark.setImageDrawable(new BladeIcon(icon, muted)); mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(mark, new LinearLayout.LayoutParams(dp(24),dp(24)));
        LinearLayout labels = column(); labels.setPadding(dp(16),0,dp(12),0); labels.addView(text(title,17,ink,"sans-serif-medium"));
        if (!value.isEmpty()) { TextView sub = text(value,14,muted,"sans-serif"); sub.setPadding(0,dp(5),0,0); labels.addView(sub); }
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        ImageView arrow = new ImageView(this); arrow.setImageDrawable(new BladeIcon("next",muted)); arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(18))); row.setOnClickListener(v -> action.run());
        row.setFocusable(true); row.setContentDescription(title + (value.isEmpty() ? "" : ", " + value));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return row;
    }
    private int themeChoice() { return getSharedPreferences(UI_PREFS, MODE_PRIVATE).getInt("theme",0); }
    private BladeSheet sheet(String icon,String title,String subtitle) {
        if(activeSheet!=null) activeSheet.dismiss();
        activeSheet=new BladeSheet(this,dark,icon,title,subtitle); return activeSheet;
    }
    private void chooseTheme() {
        BladeSheet sheet=sheet("theme",getString(R.string.theme),"");
        String[] names={getString(R.string.system_theme),getString(R.string.light_theme),getString(R.string.dark_theme)};
        String[] icons={"phone","sun","moon"};
        for(int i=0;i<names.length;i++) {
            final int choice=i;
            sheet.addChoice(names[i],"",icons[i],themeChoice()==i,()->{
                if(!getSharedPreferences(UI_PREFS,MODE_PRIVATE).edit().putInt("theme",choice).commit()) {toast(getString(R.string.theme_save_failed));return;}
                recreate();
            });
        }
        sheet.show();
    }
    private boolean hasPermission() { return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED; }
    private boolean ensurePermission() {
        if (hasPermission()) return true;
        if (getPreferences(MODE_PRIVATE).getBoolean("asked_bluetooth",false) && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
            BladeSheet sheet=sheet("bluetooth",getString(R.string.permission),"");
            sheet.addMessage(getString(R.string.permission_message));
            sheet.addButton(getString(R.string.open_settings),true,()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))));
            sheet.addButton(getString(R.string.cancel),false,null); sheet.show();
        } else {
            getPreferences(MODE_PRIVATE).edit().putBoolean("asked_bluetooth",true).apply();
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},1);
        }
        return false;
    }
    @SuppressLint("MissingPermission") private void chooseDevice() {
        if (controller.isBusy() || store.hasPending()) { toast(getString(R.string.busy)); return; }
        if (!ensurePermission()) return;
        if (adapter == null || !adapter.isEnabled()) { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return; }
        List<BluetoothDevice> devices = new ArrayList<>(); List<String> labels = new ArrayList<>();
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            String name = device.getName();
            if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE || name == null || !name.toLowerCase(Locale.ROOT).replace(" ", "").contains("buds3pro")) continue;
            devices.add(device); labels.add(name);
        }
        List<String> originalNames = new ArrayList<>(labels);
        for (int i = 0; i < labels.size(); i++) {
            if (java.util.Collections.frequency(originalNames,originalNames.get(i)) > 1) {
                String address = devices.get(i).getAddress();
                labels.set(i,originalNames.get(i) + " · …" + address.substring(address.length()-5));
            }
        }
        if (devices.isEmpty()) {
            BladeSheet sheet=sheet("buds",getString(R.string.no_buds),"");
            sheet.addMessage(getString(R.string.pair_message));
            sheet.addButton(getString(R.string.bluetooth_settings),true,()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
            sheet.addButton(getString(R.string.cancel),false,null); sheet.show(); return;
        }
        BladeSheet sheet=sheet("buds",getString(R.string.select_buds),"");
        for(int i=0;i<devices.size();i++) {
            BluetoothDevice device=devices.get(i);
            boolean selected=device.getAddress().equals(store.registeredAddress());
            sheet.addChoice(labels.get(i),"","buds",selected,()->{
                if(device.getAddress().equals(store.registeredAddress()))return;
                BladeSheet confirm=sheet("buds",getString(R.string.register_buds),device.getName());
                confirm.addMessage(getString(R.string.register_message));
                confirm.addButton(getString(R.string.register),true,()->{
                    if(controller.isBusy()||store.hasPending()){toast(getString(R.string.busy));return;}
                    try{store.register(device.getAddress(),device.getName());error="";buildPage();}
                    catch(Exception e){toast(getString(R.string.register_failed));}
                });
                confirm.addButton(getString(R.string.cancel),false,null);confirm.show();
            });
        }
        sheet.show();
    }
    @SuppressLint("MissingPermission") private void command(boolean enabled) {
        if (!ensurePermission()) return;
        if (store.registeredAddress().isEmpty()) { chooseDevice(); return; }
        if (adapter == null || !adapter.isEnabled()) { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return; }
        try { store.claimAuto(store.epoch()); }
        catch (Exception e) { error = getString(R.string.save_failed); render(); return; }
        error = ""; Action action = enabled ? Action.ON : Action.OFF;
        if (!controller.request(store.registeredAddress(),enabled,() -> true,BladeController.Fault.NONE,listener(action))) toast(getString(R.string.busy));
        render();
    }
    private void recoverPending() {
        if (!hasPermission() || !store.hasPending() || controller.isBusy()) return;
        error = ""; controller.recover(listener(Action.RECOVER)); render();
    }
    private BladeController.Listener listener(Action action) {
        return new BladeController.Listener() {
            public void log(String message) { /* Controller retains the diagnostic log outside the UI. */ }
            public void finished(boolean success, boolean pending, String detail) {
                ui.post(() -> {
                    if (destroyed) return;
                    error = success ? "" : pending ? getString(R.string.recovery_needed) : getString(R.string.retry_connection);
                    if (success && resumed) toast(getString(action == Action.RECOVER ? R.string.recovery_done : action == Action.ON ? R.string.on_requested : R.string.off_requested));
                    render();
                });
            }
        };
    }
    private void resolveProblem() {
        if (!hasPermission()) { ensurePermission(); return; }
        if (store.hasPending()) recoverPending(); else if (store.registeredAddress().isEmpty()) chooseDevice();
        else if (!error.isEmpty()) startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }
    private void render() {
        if (destroyed || automatic == null) return;
        boolean ready = !store.registeredAddress().isEmpty(), busy = controller.isBusy();
        rendering = true; automatic.setChecked(store.autoEnabled()); rendering = false;
        automatic.setEnabled(ready && hasPermission()); automatic.setAlpha(automatic.isEnabled() ? 1f : .5f);
        String deviceName = ready ? store.registeredName() : getString(R.string.register_buds);
        if (!TextUtils.equals(deviceLabel.getText(),deviceName)) deviceLabel.setText(deviceName);
        deviceLabel.setContentDescription(ready ? getString(R.string.change_buds_accessibility, store.registeredName()) : getString(R.string.register_buds));
        on.setEnabled(ready && !busy); off.setEnabled(ready && !busy);
        on.setAlpha(on.isEnabled() ? 1f : .45f); off.setAlpha(off.isEnabled() ? 1f : .45f);
        String message = busy ? getString(R.string.working)
                : !hasPermission() ? getString(R.string.grant_permission)
                : store.hasPending() ? getString(R.string.recovery_needed) : error;
        if (!TextUtils.equals(banner.getText(),message)) banner.setText(message);
        banner.setTextColor(!busy && (store.hasPending() || !error.isEmpty()) ? (dark ? 0xFFFFB4A5 : 0xFFAD392C) : muted);
        banner.setClickable(!busy && !message.isEmpty()); banner.setFocusable(!busy && !message.isEmpty());
        // Preserve space while hiding an empty status from accessibility.
        banner.setImportantForAccessibility(message.isEmpty() ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code == 1 && hasPermission()) { if (store.hasPending()) recoverPending(); else if (store.registeredAddress().isEmpty()) chooseDevice(); }
        render();
    }
    @Override protected void onResume() { super.onResume(); resumed = true; ui.post(ticker); recoverPending(); }
    @Override protected void onPause() { resumed = false; ui.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; ui.removeCallbacks(ticker); if (Build.VERSION.SDK_INT >= 33 && backRegistered) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(settingsBack); if(activeSheet!=null) activeSheet.dismiss(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean("settings",settingsPage); out.putString("error",error); super.onSaveInstanceState(out); }
    // API33+ uses the OnBackInvokedDispatcher registered in buildPage; this is the API31/32 fallback.
    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { if (settingsPage) { settingsPage = false; buildPage(); } else super.onBackPressed(); }

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return ""; }
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, int size, int color, String family) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setTypeface(Typeface.create(family,Typeface.NORMAL)); return t; }
    private void space(int height) { body.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private void divider(LinearLayout group) { View v = new View(this); v.setBackgroundColor(dark ? 0xFF303239 : 0xFFEEEFF3); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(1)); p.setMarginStart(dp(60)); group.addView(v,p); }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private Drawable ripple(int color,int radius) { return new RippleDrawable(ColorStateList.valueOf(dark ? 0x30FFFFFF : 0x18000000),shape(color,radius),shape(Color.WHITE,radius)); }
    private Button action(String label,String description,String icon,boolean primary) {
        Button b = new Button(this); b.setText(label); b.setContentDescription(description); b.setAllCaps(false); b.setTextSize(18);
        int color = primary ? (dark ? 0xFF192231 : Color.WHITE) : ink;
        b.setTextColor(color); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setBackground(ripple(primary ? accent : surface,20)); b.setStateListAnimator(null);
        b.setPadding(dp(16),0,dp(16),0); BladeIcon glyph = new BladeIcon(icon,color); glyph.setBounds(0,0,dp(23),dp(23));
        if (getResources().getConfiguration().fontScale <= 1.3f && getResources().getConfiguration().screenWidthDp >= 360)
            b.setCompoundDrawablesRelative(glyph,null,null,null);
        b.setCompoundDrawablePadding(dp(10)); return b;
    }
    private ImageButton iconButton(String name,String label) {
        ImageButton b = new ImageButton(this); b.setImageDrawable(new BladeIcon(name,ink)); b.setContentDescription(label);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setPadding(dp(12),dp(12),dp(12),dp(12)); b.setBackground(ripple(Color.TRANSPARENT,24)); return b;
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show(); }

}
