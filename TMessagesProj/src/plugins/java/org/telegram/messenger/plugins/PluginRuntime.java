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
                .callAttr("dependencies", directory.getAbsolutePath(), true).toString()).getAsJsonObject();
    }
    void debug(PyObject plugin, String method, PluginDevServer.Terminal terminal, String adapter, int port) {
        Python.getInstance().getModule("bygram._dev_debug").callAttr("debug", plugin, method, terminal, adapter, port,
                ApplicationLoader.applicationContext.getFilesDir().getAbsolutePath());
    }
    void ideSession(String adapter, int port, boolean start) {
        python().getModule("bygram._dev_debug").callAttr("ide_session", adapter, port, start,
                ApplicationLoader.applicationContext.getFilesDir().getAbsolutePath());
    }
    com.google.gson.JsonObject catalogMetadata(File file, String format, int host, int base) throws IOException {
        if ("bygram".equals(format)) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
                java.util.zip.ZipEntry entry = zip.getEntry("manifest.json");
                PluginPackage.check(entry != null && entry.getSize() > 0 && entry.getSize() <= PluginManifest.MAX_BYTES, "InvalidManifest");
                try (java.io.InputStream input = zip.getInputStream(entry)) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    for (int count; (count = input.read(buffer)) != -1; ) {
                        PluginPackage.check(bytes.size() + count <= PluginManifest.MAX_BYTES, "InvalidManifest");
                        bytes.write(buffer, 0, count);
                    }
                    return PluginManifest.parse(bytes.toByteArray(), 1, host, base).metadata();
                }
            }
        }
        // The file loader cache need not have a source suffix.
        return com.google.gson.JsonParser.parseString(python().getModule("bygram._port_extera")
                .callAttr("catalog_metadata", file.getAbsolutePath(), "archive".equals(format)).toString()).getAsJsonObject();
    }
    byte[] convertExtera(byte[] bytes, boolean archive, String id, String version) throws IOException {
        return convertExtera(bytes, archive, id, version, false);
    }
    byte[] convertExtera(byte[] bytes, boolean archive, String id, String version, boolean developer) throws IOException {
        File cache = ApplicationLoader.applicationContext.getCacheDir();
        File source = File.createTempFile("extera-", archive ? ".elyx" : ".plugin", cache);
        File output = null;
        try {
            output = File.createTempFile("extera-", ".byplugin", cache);
            try (FileOutputStream file = new FileOutputStream(source)) { file.write(bytes); }
            python().getModule("bygram._port_extera").callAttr("convert_mobile",
                    source.getAbsolutePath(), output.getAbsolutePath(), id, version, developer);
            return PluginPackage.read(output, PluginPackage.MAX_ZIP);
        } catch (com.chaquo.python.PyException error) {
            String message = error.getMessage();
            for (String code : new String[]{"CatalogPackageMismatch", "Expected Python 3.11 bytecode", "PyPIResolutionTimeout", "PyPIDependenciesUnresolved",
                    "PyPIWheelHashMismatch", "PyPIDownloadLimitExceeded", "PyPIDependencyLimitExceeded", "PyPIResolutionLimitExceeded"}) {
                if (message != null && message.contains(code))
                    throw new IOException(code.equals("Expected Python 3.11 bytecode") ? "InvalidPythonBytecode" : code, error);
            }
            throw new IOException("InvalidExteraPackage", error);
        } finally {
            source.delete();
            if (output != null) output.delete();
        }
    }
    String exteraId(String id) {
        return python().getModule("bygram._port_extera").callAttr("developer_id", id).toString();
    }
    com.google.gson.JsonObject compareElyx(File root, com.google.gson.JsonObject payload) {
        return com.google.gson.JsonParser.parseString(python().getModule("bygram._dev_elyx")
                .callAttr("compare", root.getAbsolutePath(), payload.toString()).toString()).getAsJsonObject();
    }
    byte[] patchElyx(File root, com.google.gson.JsonObject payload) {
        return python().getModule("bygram._dev_elyx").callAttr("patch", root.getAbsolutePath(), payload.toString()).toJava(byte[].class);
    }
}
