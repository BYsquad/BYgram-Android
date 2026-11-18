package org.telegram.messenger.plugins;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import com.chaquo.python.PyObject;
import java.io.File;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PluginEntry.FilePlace;
import org.telegram.ui.ActionBar.BaseFragment;

/** Extension handlers owned by the enabling plugin generation. */
public final class PluginFileHooks {
    private static final ConcurrentHashMap<String, Handler> handlers = new ConcurrentHashMap<>();

    private static final class Handler {
        final PluginContext owner;
        final String secret = UUID.randomUUID().toString();
        final int allow, deny;
        final PyObject open, icon;
        AutoCloseable registration;

        Handler(PluginContext owner, int allow, int deny, PyObject open, PyObject icon) {
            this.owner = owner;
            this.allow = allow;
            this.deny = deny;
            this.open = open;
            this.icon = icon;
        }
    }

    static synchronized String register(PluginContext owner, String ext, int allow, int deny, PyObject open, PyObject icon) throws Exception {
        if (!ext.matches("[a-z0-9][a-z0-9_+.-]{0,31}") || open == null) throw new IllegalArgumentException("InvalidFileHook");
        Handler handler = new Handler(owner, allow, deny, open, icon);
        if (handlers.putIfAbsent(ext, handler) != null) return null;
        handler.registration = owner.scope.own(() -> handlers.remove(ext, handler));
        return handler.secret;
    }

    static synchronized String unregister(PluginContext owner, String ext, String secret) throws Exception {
        Handler handler = handlers.get(ext);
        if (handler == null) return "ExtensionNotRegistered";
        if (handler.owner != owner || !handler.secret.equals(secret)) return "SecretInvalid";
        handler.registration.close();
        return null;
    }

    private static Handler find(MessageObject message, String name, FilePlace place) {
        if (handlers.isEmpty() || message == null || name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        Handler handler = null;
        for (int dot = lower.indexOf('.'); dot >= 0 && handler == null; dot = lower.indexOf('.', dot + 1)) {
            handler = handlers.get(lower.substring(dot + 1));
        }
        int bit = 1 << place.ordinal();
        if (handler == null || handler.allow != 0 && (handler.allow & bit) == 0 || (handler.deny & bit) != 0) return null;
        if (message.messageOwner == null || message.messageOwner.noforwards || message.isSecretMedia()
                || DialogObject.isEncryptedDialog(message.getDialogId())
                || MessagesController.getInstance(message.currentAccount).isPeerNoForwards(message.getDialogId())) return null;
        try {
            return handler.owner.accountAvailable(message.currentAccount, PluginContext.currentIdentity(message.currentAccount)) ? handler : null;
        } catch (IllegalStateException | IllegalArgumentException unavailable) {
            return null;
        }
    }

    public static boolean open(MessageObject message, File file, String name, Activity activity, BaseFragment parent, FilePlace place) {
        Handler handler = find(message, name, place);
        if (handler == null || !file.isFile() || !handler.owner.scope.beginCallback()) return false;
        try {
            handler.open.call(place.ordinal() + 1, file, name, message, activity, parent);
            return true;
        } catch (Exception error) {
            handler.owner.failed(error);
            return false;
        } finally {
            handler.owner.scope.endCallback();
        }
    }

    public static Drawable icon(MessageObject message, String name, FilePlace place) {
        Handler handler = find(message, name, place);
        if (handler == null || handler.icon == null || !handler.owner.scope.beginCallback()) return null;
        try {
            PyObject result = handler.icon.call(message.currentAccount);
            return result == null ? null : result.toJava(Drawable.class);
        } catch (Exception error) {
            handler.owner.failed(error);
            return null;
        } finally {
            handler.owner.scope.endCallback();
        }
    }
}
