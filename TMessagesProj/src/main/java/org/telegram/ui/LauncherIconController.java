package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.List;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        LauncherIcon icon = getCurrentIcon();
        if (icon == null || icon == LauncherIcon.VINTAGE) {
            setIcon(LauncherIcon.DEFAULT);
        }
    }

    public static LauncherIcon getCurrentIcon() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return icon;
            }
        }
        return null;
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
            setIconAtomically(icon, ctx, pm, flags | PackageManager.SYNCHRONOUS);
            return;
        }
        pm.setComponentEnabledSetting(icon.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, flags);
        for (LauncherIcon i : LauncherIcon.values()) {
            if (i != icon && isEnabled(i)) {
                pm.setComponentEnabledSetting(i.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, flags);
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static void setIconAtomically(LauncherIcon icon, Context ctx, PackageManager pm, int flags) {
        List<PackageManager.ComponentEnabledSetting> changes = new ArrayList<>();
        changes.add(new PackageManager.ComponentEnabledSetting(
                icon.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, flags));
        for (LauncherIcon other : LauncherIcon.values()) {
            if (other != icon && isEnabled(other)) {
                changes.add(new PackageManager.ComponentEnabledSetting(
                        other.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, flags));
            }
        }
        pm.setComponentEnabledSettings(changes);
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.bygram_launcher_icon, 0, R.string.AppName),
        VINTAGE("VintageIcon", R.drawable.bygram_logo_new, 0, R.string.BygramIconCurrent),
        AQUA("AquaIcon", R.drawable.telegram_logo, 0, R.string.BygramIconTelegram),
        PREMIUM("PremiumIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconPremium, true),
        TURBO("TurboIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTurbo, true),
        NOX("NoxIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, true),

        BYGRAM_CLASSIC("BygramClassicIcon", R.drawable.bygram_avatar_classic, 0, R.string.BygramAvatarClassic),
        BYGRAM_ORBIT("BygramOrbitIcon", R.drawable.bygram_avatar_orbit, 0, R.string.BygramAvatarOrbit),
        BYGRAM_DRAFT("BygramDraftIcon", R.drawable.bygram_avatar_draft, 0, R.string.BygramAvatarDraft),
        BYGRAM_MOLE("BygramMoleIcon", R.drawable.bygram_avatar_mole, 0, R.string.BygramAvatarMole),
        BYGRAM_ECHPOCHMAK("BygramEchpochmakIcon", R.drawable.bygram_avatar_echpochmak, 0, R.string.BygramAvatarEchpochmak),
        BYGRAM_AVATAR("BygramAvatarIcon", R.drawable.bygram_avatar_avatar, 0, R.string.BygramAvatarAvatar),
        BYGRAM_CANDY("BygramCandyIcon", R.drawable.bygram_avatar_candy, 0, R.string.BygramAvatarCandy),
        BYGRAM_GUITAR("BygramGuitarIcon", R.drawable.bygram_avatar_guitar, 0, R.string.BygramAvatarGuitar),
        BYGRAM_IMPOSTOR("BygramImpostorIcon", R.drawable.bygram_avatar_impostor, 0, R.string.BygramAvatarImpostor),
        BYGRAM_FROG("BygramFrogIcon", R.drawable.bygram_avatar_frog, 0, R.string.BygramAvatarFrog),
        BYGRAM_HAMSTER("BygramHamsterIcon", R.drawable.bygram_avatar_hamster, 0, R.string.BygramAvatarHamster),
        BYGRAM_SPONGE("BygramSpongeIcon", R.drawable.bygram_avatar_sponge, 0, R.string.BygramAvatarSponge),
        BYGRAM_INVINCIBLE("BygramInvincibleIcon", R.drawable.bygram_avatar_invincible, 0, R.string.BygramAvatarInvincible),
        BYGRAM_FLAPPY("BygramFlappyIcon", R.drawable.bygram_avatar_flappy, 0, R.string.BygramAvatarFlappy),
        BYGRAM_NYAN("BygramNyanIcon", R.drawable.bygram_avatar_nyan, 0, R.string.BygramAvatarNyan),
        BYGRAM_GLACIER("BygramGlacierIcon", R.drawable.bygram_avatar_glacier, 0, R.string.BygramAvatarGlacier),
        BYGRAM_FISH("BygramFishIcon", R.drawable.bygram_avatar_fish, 0, R.string.BygramAvatarFish),
        BYGRAM_PSYCHOSIS("BygramPsychosisIcon", R.drawable.bygram_avatar_psychosis, 0, R.string.BygramAvatarPsychosis),
        BYGRAM_XP("BygramXpIcon", R.drawable.bygram_avatar_xp, 0, R.string.BygramAvatarXp),
        BYGRAM_NASA("BygramNasaIcon", R.drawable.bygram_avatar_nasa, 0, R.string.BygramAvatarNasa),
        BYGRAM_CYBERPUNK("BygramCyberpunkIcon", R.drawable.bygram_avatar_cyberpunk, 0, R.string.BygramAvatarCyberpunk),
        BYGRAM_SPACE("BygramSpaceIcon", R.drawable.bygram_avatar_space, 0, R.string.BygramAvatarSpace),
        BYGRAM_JELLYFISH("BygramJellyfishIcon", R.drawable.bygram_avatar_jellyfish, 0, R.string.BygramAvatarJellyfish),
        BYGRAM_HUMAN("BygramHumanIcon", R.drawable.bygram_avatar_human, 0, R.string.BygramAvatarHuman),
        BYGRAM_SUNSET("BygramSunsetIcon", R.drawable.bygram_avatar_sunset, 0, R.string.BygramAvatarSunset),
        BYGRAM_AMETHYST("BygramAmethystIcon", R.drawable.bygram_avatar_amethyst, 0, R.string.BygramAvatarAmethyst),
        BYGRAM_AURORA("BygramAuroraIcon", R.drawable.bygram_avatar_aurora, 0, R.string.BygramAvatarAurora),
        BYGRAM_OSU("BygramOsuIcon", R.drawable.bygram_avatar_osu, 0, R.string.BygramAvatarOsu),
        BYGRAM_GOOGLE("BygramGoogleIcon", R.drawable.bygram_avatar_google, 0, R.string.BygramAvatarGoogle),
        BYGRAM_GOOGLE_26("BygramGoogle26Icon", R.drawable.bygram_avatar_google_26, 0, R.string.BygramAvatarGoogle26);

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
