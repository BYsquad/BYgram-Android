package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                if (icon == LauncherIcon.VINTAGE) setIcon(LauncherIcon.DEFAULT);
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        int flags = PackageManager.DONT_KILL_APP;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            flags |= PackageManager.SYNCHRONOUS;
        }
        pm.setComponentEnabledSetting(icon.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, flags);
        for (LauncherIcon i : LauncherIcon.values()) {
            if (i != icon) {
                pm.setComponentEnabledSetting(i.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, flags);
            }
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.bygram_logo, 0, R.string.AppName),
        VINTAGE("VintageIcon", R.drawable.bygram_logo, 0, R.string.BygramIconCurrent),
        AQUA("AquaIcon", R.mipmap.ic_launcher, 0, R.string.BygramIconTelegram),
        PREMIUM("PremiumIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconPremium, true),
        TURBO("TurboIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTurbo, true),
        NOX("NoxIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, true);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
