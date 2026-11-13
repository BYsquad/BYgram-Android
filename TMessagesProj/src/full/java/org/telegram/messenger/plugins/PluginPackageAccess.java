package org.telegram.messenger.plugins;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** File operations on the package tree, separate from plugin data and settings. */
final class PluginPackageAccess {
    private static final int PREVIEW_BYTES = 128 * 1024;

    static List<String> files(File directory) throws IOException {
        ArrayList<String> names = new ArrayList<>();
        collect(directory.getCanonicalFile(), "", names, 0);
        Collections.sort(names);
        return names;
    }

    private static void collect(File directory, String prefix, List<String> names, int depth) throws IOException {
        PluginPackage.check(depth <= 12, "PackageLimitExceeded");
        File[] children = directory.listFiles();
        PluginPackage.check(children != null, "PackageMissing");
        for (File child : children) {
            PluginPackage.check(child.getCanonicalFile().equals(child), "InvalidPackagePath");
            String name = prefix + child.getName();
            if (child.isDirectory()) collect(child, name + "/", names, depth + 1);
            else {
                PluginPackage.check(child.isFile() && names.size() < 512, "PackageLimitExceeded");
                names.add(name);
            }
        }
    }

    static String source(File directory, String name, String truncated) throws IOException {
        File root = directory.getCanonicalFile();
        File file = new File(root, name);
        PluginPackage.check(file.getCanonicalFile().equals(file) && file.getPath().startsWith(root.getPath() + File.separator)
                && (name.endsWith(".py") || name.equals("manifest.json")), "InvalidPackagePath");
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            transfer(input, buffer, PREVIEW_BYTES);
            String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            return input.read() == -1 ? text : text + "\n\n" + truncated;
        }
    }

    static void export(File directory, File original, File destination) throws IOException {
        AtomicFile archive = new AtomicFile(destination);
        FileOutputStream output = archive.startWrite();
        try {
            if (original.isFile()) {
                PluginPackage.check(original.getCanonicalFile().equals(original.getAbsoluteFile()), "InvalidPackagePath");
                try (InputStream input = new FileInputStream(original)) {
                    transfer(input, output, PluginPackage.MAX_ZIP);
                    PluginPackage.check(input.read() == -1, "PackageLimitExceeded");
                }
            } else {
                // Older installs retained only the extracted tree. Signatures cover file contents.
                File root = directory.getCanonicalFile();
                try (ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(output) {
                    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                        out.write(bytes, offset, length);
                    }
                    @Override public void close() throws IOException { flush(); }
                })) {
                    int remaining = PluginPackage.MAX_TOTAL;
                    for (String name : files(root)) {
                        File file = new File(root, name);
                        PluginPackage.check(file.getCanonicalFile().equals(file), "InvalidPackagePath");
                        ZipEntry entry = new ZipEntry(name);
                        entry.setTime(0);
                        zip.putNextEntry(entry);
                        try (InputStream input = new FileInputStream(file)) {
                            remaining -= transfer(input, zip, Math.min(remaining, PluginPackage.MAX_FILE));
                            PluginPackage.check(input.read() == -1, "PackageLimitExceeded");
                        }
                        zip.closeEntry();
                    }
                }
                PluginPackage.check(output.getChannel().size() <= PluginPackage.MAX_ZIP, "PackageLimitExceeded");
            }
            archive.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            archive.failWrite(output);
            throw error;
        }
    }

    private static int transfer(InputStream input, OutputStream output, int limit) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        while (total < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - total));
            if (count == -1) break;
            output.write(buffer, 0, count);
            total += count;
        }
        return total;
    }
}
