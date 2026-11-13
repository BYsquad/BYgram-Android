package org.telegram.messenger.plugins;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.telegram.messenger.ApplicationLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class PluginRuntime {
    private Python python;
    private PyObject loader;

    private synchronized Python python() {
        if (python == null) {
            synchronized (Python.class) {
                if (!Python.isStarted()) Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            python = Python.getInstance(); loader = python.getModule("bygram._loader");
        }
        return python;
    }

    PyObject load(File directory, PluginManifest manifest, PluginContext context) {
        python();
        return loader.callAttr("load", directory.getAbsolutePath(), manifest.id(), manifest.entrypoint(), context);
    }
    void unload(PyObject plugin, String id) { if (loader != null) loader.callAttr("unload", plugin, id); }
    com.google.gson.JsonObject dependencies(File directory) {
        if (!new File(directory, "_bygram_elyx.json").isFile()) return new com.google.gson.JsonObject();
        return com.google.gson.JsonParser.parseString(python().getModule("bygram._port_extera")
                .callAttr("dependencies", directory.getAbsolutePath()).toString()).getAsJsonObject();
    }
    void debug(PyObject plugin, String method, PluginDevServer.Terminal terminal, String adapter, int port) {
        Python.getInstance().getModule("bygram._dev_debug").callAttr("debug", plugin, method, terminal, adapter, port,
                ApplicationLoader.applicationContext.getFilesDir().getAbsolutePath());
    }
    byte[] convertExtera(byte[] bytes, boolean archive, String id, String version) throws IOException {
        File cache = ApplicationLoader.applicationContext.getCacheDir();
        File source = File.createTempFile("extera-", archive ? ".elyx" : ".plugin", cache);
        File output = null;
        try {
            output = File.createTempFile("extera-", ".byplugin", cache);
            try (FileOutputStream file = new FileOutputStream(source)) { file.write(bytes); }
            python().getModule("bygram._port_extera").callAttr("convert_mobile",
                    source.getAbsolutePath(), output.getAbsolutePath(), id, version);
            return PluginPackage.read(output, PluginPackage.MAX_ZIP);
        } catch (com.chaquo.python.PyException error) {
            String message = error.getMessage();
            for (String code : new String[]{"CatalogPackageMismatch", "PyPIResolutionTimeout", "PyPIDependenciesUnresolved",
                    "PyPIWheelHashMismatch", "PyPIDownloadLimitExceeded", "PyPIDependencyLimitExceeded", "PyPIResolutionLimitExceeded"}) {
                if (message != null && message.contains(code)) throw new IOException(code, error);
            }
            throw new IOException("InvalidExteraPackage", error);
        } finally {
            source.delete();
            if (output != null) output.delete();
        }
    }
}
