package org.telegram.messenger.plugins;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.telegram.messenger.ApplicationLoader;
import java.io.File;

final class PluginRuntime {
    private Python python;
    private PyObject loader;

    PyObject load(File directory, PluginManifest manifest, PluginContext context) {
        synchronized (this) { if (python == null) {
            synchronized (Python.class) {
                if (!Python.isStarted()) Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            python = Python.getInstance(); loader = python.getModule("bygram._loader");
        } }
        return loader.callAttr("load", directory.getAbsolutePath(), manifest.id(), manifest.entrypoint(), context);
    }
    void unload(PyObject plugin, String id) { if (loader != null) loader.callAttr("unload", plugin, id); }
}
