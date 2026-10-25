"""Run production action selection and bounded video copying on the JVM, without Android/JNI."""
from pathlib import Path
import json
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/BygramGroupActivity.java").read_text(encoding="utf-8")
video = (root / "TMessagesProj/src/main/java/org/telegram/ui/Components/BygramVideoWallpaper.java").read_text(encoding="utf-8")


def method(text, signature):
    start = text.index(signature)
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


selection = method(source, "static String findActivity(")
actions = method(source, "static String actionText(")
copy = method(video, "private static void copyVideo(")
action_names = sorted(set(re.findall(r"TLRPC\.(TL_sendMessage\w+)", source)))
resource_names = sorted(set(re.findall(r"R.string.(\w+)", actions)))
stubs = "static class TLRPC { static class SendMessageAction {} " + " ".join(
    f"static class {name} extends SendMessageAction {{}}" for name in action_names + ["TL_sendMessageCancelAction"]
) + " }\n"
stubs += "static class R { static class string { " + ", ".join(
    ("static final int " if i == 0 else "") + name + "=" + str(i) for i, name in enumerate(resource_names)
) + "; }}\n"
stubs += "static class LocaleController { static String getString(int id) { return new String[] {" + ",".join(map(json.dumps, resource_names)) + "}[id]; }}\n"
harness = r"""
import java.util.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
public class LocalActivityCheck {
    static class MessagesController { static class PrintingUser { long userId, lastTime; TLRPC.SendMessageAction action; } }
    STUBS
    SELECTION
    ACTIONS
    COPY
    static Map<Long, ConcurrentHashMap<Integer, ArrayList<MessagesController.PrintingUser>>> events = new HashMap<>();
    static Map<Long, String> groups = new HashMap<>();
    static void add(long group, int topic, long user, long at, TLRPC.SendMessageAction action) {
        MessagesController.PrintingUser event = new MessagesController.PrintingUser();
        event.userId = user; event.lastTime = at; event.action = action;
        events.computeIfAbsent(-group, k -> new ConcurrentHashMap<>()).computeIfAbsent(topic, k -> new ArrayList<>()).add(event);
    }
    static void expect(String text, long now) {
        String actual = findActivity(groups, events, 42, now);
        if (!text.equals(actual)) throw new AssertionError("Expected " + text + ", got " + actual);
    }
    public static void main(String[] args) throws Exception {
        groups.put(1L, "Group one"); groups.put(2L, "Group two");
        expect("", 10000);
        add(1, 0, 99, 9900, new TLRPC.TL_sendMessageTypingAction());
        add(3, 0, 42, 9900, new TLRPC.TL_sendMessageTypingAction());
        expect("", 10000); // Another user and a non-common group never leak into the private header.
        add(1, 0, 42, 5000, new TLRPC.TL_sendMessageTypingAction());
        expect("Typing · Group one", 10000);
        add(2, 77, 42, 9000, new TLRPC.TL_sendMessageChooseStickerAction());
        expect("ChoosingSticker · Group two", 10000); // Newest target action, including forum topics.
        expect("", 14900); // Exact TTL boundary.
        add(1, 0, 42, 15000, new TLRPC.TL_sendMessageGamePlayAction());
        expect("SendingGame · Group one", 44999);
        expect("", 45000);
        add(2, 0, 42, 45001, new TLRPC.TL_sendMessageCancelAction());
        expect("", 45002);
        events.clear();
        add(1, 0, 42, 50000, new TLRPC.TL_sendMessageUploadVideoAction());
        expect("SendingVideoStatus · Group one", 50001);
        events.clear(); expect("", 50002); // Native cancel/message removal returns to fallback.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyVideo(new ByteArrayInputStream(new byte[]{1,2,3}), output);
        if (!Arrays.equals(output.toByteArray(), new byte[]{1,2,3})) throw new AssertionError("Copy changed bytes");
        final long[] remaining = {100L * 1024 * 1024 + 1};
        final long[] written = {0};
        InputStream large = new InputStream() {
            public int read() { throw new AssertionError("Unbuffered copy"); }
            public int read(byte[] b) { if (remaining[0] == 0) return -1; int n = (int)Math.min(b.length, remaining[0]); remaining[0] -= n; return n; }
        };
        OutputStream counter = new OutputStream() {
            public void write(int value) { written[0]++; }
            public void write(byte[] b, int off, int count) { written[0] += count; }
        };
        try { copyVideo(large, counter); throw new AssertionError("Oversized video accepted"); }
        catch (IOException expected) { if (written[0] > 100L * 1024 * 1024) throw new AssertionError("Wrote over limit"); }
        try { copyVideo(new InputStream() { public int read() throws IOException { throw new IOException("broken"); } }, counter); throw new AssertionError("Read failure swallowed"); }
        catch (IOException expected) {}
        System.out.println("PASS: local action scope, newest action, topics, expiry, cancel, video bytes, size limit and read failure");
    }
}
""".replace("STUBS", stubs).replace("SELECTION", selection).replace("ACTIONS", actions).replace("COPY", copy)
with tempfile.TemporaryDirectory(prefix="bygram-local-check-") as directory:
    path = Path(directory) / "LocalActivityCheck.java"
    path.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory, str(path)], check=True)
    subprocess.run(["java", "-ea", "-cp", directory, "LocalActivityCheck"], check=True)
