package org.telegram.messenger.plugins;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Strict, bounded ZIP staging. The caller owns and removes the staging directory. */
public final class PluginPackage {
    public static final int MAX_ZIP = 10 * 1024 * 1024;
    public static final int MAX_TOTAL = 30 * 1024 * 1024;
    public static final int MAX_FILE = 5 * 1024 * 1024;
    public final PluginManifest manifest;
    public final String sha256;
    public final File directory;
    String source = "local_file";
    com.google.gson.JsonObject catalogRelease;
    String signer;

    private PluginPackage(PluginManifest manifest, String sha256, File directory) {
        this.manifest = manifest; this.sha256 = sha256; this.directory = directory;
    }

    public static PluginPackage stage(InputStream input, File staging, int sdk, int host, int telegram) throws Exception {
        if (!staging.mkdir()) throw new IOException("StagingUnavailable");
        File archive = new File(staging, "package.byplugin");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileOutputStream out = new FileOutputStream(archive)) {
            byte[] bytes = new byte[8192]; int count, total = 0;
            while ((count = input.read(bytes)) != -1) {
                total += count;
                check(total <= MAX_ZIP, "PackageLimitExceeded");
                digest.update(bytes, 0, count); out.write(bytes, 0, count);
            }
            out.getFD().sync();
        }
        checkCentralDirectory(archive);
        File content = new File(staging, "content");
        if (!content.mkdir()) throw new IOException("StagingUnavailable");
        Set<String> entries = new HashSet<>();
        Map<String, String> pathCases = new HashMap<>();
        int total = 0, files = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            byte[] buffer = new byte[8192];
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                check(++files <= 512, "PackageLimitExceeded");
                String name = entry.getName();
                check(!name.isEmpty() && !name.startsWith("/") && !name.contains("\\") && !name.contains(":"), "InvalidPackagePath");
                String relative = entry.isDirectory() ? name.substring(0, name.length() - 1) : name;
                String[] parts = relative.split("/", -1);
                check(parts.length <= 12, "PackageLimitExceeded");
                String prefix = "";
                for (String part : parts) {
                    check(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "InvalidPackagePath");
                    for (int i = 0; i < part.length(); i++) check(!Character.isISOControl(part.charAt(i)), "InvalidPackagePath");
                    prefix += (prefix.isEmpty() ? "" : "/") + part;
                    String key = prefix.toLowerCase(Locale.ROOT);
                    String previous = pathCases.put(key, prefix);
                    check(previous == null || previous.equals(prefix), "CaseCollision");
                }
                check(entries.add(relative.toLowerCase(Locale.ROOT)), "DuplicateEntry");
                String lower = relative.toLowerCase(Locale.ROOT);
                check(!lower.matches(".*\\.(pyc|pyo|dex|jar|class|so|pyd|dll|exe|apk|aar)$"), "UnsupportedPackageFile");
                File target = new File(content, relative);
                check(target.getCanonicalPath().startsWith(content.getCanonicalPath() + File.separator), "InvalidPackagePath");
                if (entry.isDirectory()) {
                    check(target.mkdirs() || target.isDirectory(), "InvalidPackagePath");
                    continue;
                }
                check(entry.getSize() >= 0 && entry.getSize() <= MAX_FILE, "PackageLimitExceeded");
                check(target.getParentFile().mkdirs() || target.getParentFile().isDirectory(), "InvalidPackagePath");
                CRC32 crc = new CRC32(); int size = 0;
                try (InputStream in = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(target)) {
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        size += count; total += count;
                        check(size <= MAX_FILE && total <= MAX_TOTAL, "PackageLimitExceeded");
                        if (name.equals("manifest.json")) check(size <= PluginManifest.MAX_BYTES, "PackageLimitExceeded");
                        crc.update(buffer, 0, count); out.write(buffer, 0, count);
                    }
                    out.getFD().sync();
                }
                check(size == entry.getSize() && crc.getValue() == entry.getCrc(), "CorruptPackage");
                if (name.endsWith(".py")) {
                    try {
                        StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(read(target)));
                    } catch (java.nio.charset.CharacterCodingException invalid) { throw new IOException("InvalidPythonEncoding"); }
                }
            }
        }
        File manifestFile = new File(content, "manifest.json");
        check(manifestFile.isFile() && manifestFile.length() <= PluginManifest.MAX_BYTES, "ManifestMissing");
        PluginManifest manifest = PluginManifest.parse(read(manifestFile), sdk, host, telegram);
        check(new File(content, manifest.entrypoint().split(":")[0]).isFile(), "EntrypointMissing");
        if (manifest.metadata().has("icon")) {
            File icon = new File(content, manifest.metadata().get("icon").getAsString());
            check(icon.isFile() && icon.length() <= 256 * 1024, "InvalidIcon");
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
        PluginPackage pkg = new PluginPackage(manifest, hash.toString(), content);
        pkg.signer = PluginSignature.verify(content);
        return pkg;
    }

    private static void checkCentralDirectory(File archive) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(archive, "r")) {
            long end = in.length() - 22, eocd = -1;
            for (long pos = end; pos >= Math.max(0, end - 65535); pos--) {
                in.seek(pos);
                if (u32(in) == 0x06054b50L) {
                    in.seek(pos + 20);
                    if (pos + 22 + u16(in) == in.length()) { eocd = pos; break; }
                }
            }
            check(eocd >= 0, "InvalidZip");
            in.seek(eocd + 4);
            check(u16(in) == 0 && u16(in) == 0, "UnsupportedZip");
            int count = u16(in);
            check(count == u16(in) && count > 0 && count <= 512, "PackageLimitExceeded");
            long size = u32(in), offset = u32(in);
            check(offset + size == eocd, "UnsupportedZip");
            in.seek(offset);
            for (int i = 0; i < count; i++) {
                long start = in.getFilePointer();
                check(u32(in) == 0x02014b50L, "InvalidZip");
                in.skipBytes(4);
                int flags = u16(in), method = u16(in);
                check((flags & 0x2041) == 0 && (method == 0 || method == 8), "UnsupportedZip");
                in.skipBytes(16);
                int name = u16(in), extra = u16(in), comment = u16(in);
                check(u16(in) == 0, "UnsupportedZip");
                in.skipBytes(2);
                long attrs = u32(in), localOffset = u32(in);
                int type = (int) (attrs >> 16) & 0170000;
                check(type == 0 || type == 0100000 || type == 0040000, "UnsupportedZipEntry");
                long next = start + 46L + name + extra + comment;
                check(next <= eocd && localOffset < offset, "InvalidZip");
                in.seek(localOffset);
                check(u32(in) == 0x04034b50L, "InvalidZip");
                in.skipBytes(2);
                check(u16(in) == flags && u16(in) == method, "InvalidZip");
                in.seek(next);
            }
            check(in.getFilePointer() == eocd, "InvalidZip");
        }
    }

    private static int u16(RandomAccessFile in) throws IOException { return in.readUnsignedByte() | in.readUnsignedByte() << 8; }
    private static long u32(RandomAccessFile in) throws IOException { return (long) u16(in) | (long) u16(in) << 16; }
    static void check(boolean value, String reason) throws IOException { if (!value) throw new IOException(reason); }
    static byte[] read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) { check(out.size() + count <= MAX_FILE, "PackageLimitExceeded"); out.write(buffer, 0, count); }
            return out.toByteArray();
        }
    }
    static void delete(File file) throws IOException {
        if (!file.exists()) { file.delete(); return; }
        File absolute = file.getAbsoluteFile();
        File parent = absolute.getParentFile();
        File normalized = parent == null ? absolute : new File(parent.getCanonicalFile(), absolute.getName());
        File[] children = normalized.getCanonicalFile().equals(normalized) ? file.listFiles() : null;
        if (children != null) for (File child : children) delete(child);
        check(file.delete(), "DeleteFailed");
    }
}
