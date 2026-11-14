package org.telegram.messenger.plugins;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import com.chaquo.python.PyObject;
import org.telegram.messenger.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Debug-only fixed fixtures. No paths, packages or commands are accepted from intents. */
public final class PluginDiagnosticActivity extends Activity {
    private static final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.DEBUG_VERSION || !BuildConfig.BYGRAM_FULL) { finish(); return; }
        TextView view = new TextView(this); view.setText("Plugin integration checks…"); setContentView(view);
        if (!running.compareAndSet(false, true)) {
            view.setText("Plugin checks are already running. Reopen this page after completion.");
            return;
        }
        new Thread(() -> {
            try {
                String result;
                try { result = checks(); }
                catch (Throwable error) { result = "FAIL: " + android.util.Log.getStackTraceString(error); }
                final String text = result;
                android.util.Log.i("BYgramPluginTest", text);
                try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "plugin-self-check.txt"))) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) { }
                runOnUiThread(() -> view.setText(text));
            } finally { running.set(false); }
        }, "BYgramPluginTest").start();
    }
    private String checks() throws Exception {
        if (HookBridge.available()) PluginBackendProbe.ensure(this);
        if (!com.chaquo.python.Python.isStarted())
            com.chaquo.python.Python.start(new com.chaquo.python.android.AndroidPlatform(getApplicationContext()));
        PyObject python = com.chaquo.python.Python.getInstance().getBuiltins();
        python.callAttr("exec", "from extera_utils.text_formatting import parse_text\n"
                + "import bs4, requests\n"
                + "from PIL import Image, ImageFont\nfrom lxml import etree\nfrom io import BytesIO\n"
                + "pixels = BytesIO(); Image.new('RGB', (2, 2)).save(pixels, 'JPEG'); pixels.seek(0)\n"
                + "assert Image.open(pixels).size == (2, 2) and ImageFont.load_default() is not None\n"
                + "assert etree.fromstring(b'<ok/>').tag == 'ok' and etree.LIBXSLT_VERSION >= (1, 1, 32)\n"
                + "assert bs4.BeautifulSoup('<b>ok</b>', 'html.parser').b.text == 'ok'\n"
                + "from hook_utils import find_class\n"
                + "from android_utils import R\n"
                + "assert find_class('org.telegram.messenger.plugins.HookBridge$Probe') is not None\n"
                + "formatted = parse_text('\\U0001f642 <b>bold</b>')\n"
                + "assert formatted['entities'][0].offset == 3 and formatted['entities'][0].length == 4\n"
                + "events = []\nR(lambda: events.append(True)).run()\nassert events == [True]\n",
                python.callAttr("dict"));
        return checks(true);
    }
    private String checks(boolean androidUi) throws Exception {
        File root = new File(getCacheDir(), "plugin-integration-fixture");
        PluginPackage.delete(root); PluginPackage.check(root.mkdir(), "FixtureUnavailable");
        String manifest = "{\"manifest_version\":1,\"id\":\"test.bygram.fixture\",\"name\":\"Test\",\"description\":\"\",\"author\":\"BYgram\","
                + "\"version\":\"1.0.0\",\"entrypoint\":\"main.py:Fixture\",\"sdk\":{\"min\":1,\"max\":1},\"python\":\"3.11\","
                + "\"capabilities\":[\"ui\",\"network\",\"hooks.raw\"],\"account_scope\":\"global\",\"settings_schema\":1,"
                + "\"requires_hook_backend\":true,\"bygram\":{\"min_build\":1,\"max_build\":2147483647},\"telegram\":{\"min_build\":1,\"max_build\":2147483647},"
                + "\"hook_targets\":[{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"value\",\"args\":[\"int\"]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"constructor\":true,\"args\":[]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"label\",\"args\":[\"java.lang.String\"]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"label\",\"args\":[\"int\"]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"throwsOriginal\",\"args\":[]}]}";
        PluginManifest metadata = PluginManifest.parse(manifest.getBytes(StandardCharsets.UTF_8), 1, PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
        String code = "from bygram import Plugin\nclass Fixture(Plugin):\n"
                + " def compatibility(self, directory):\n"
                + "  from pathlib import Path\n"
                + "  from elyx import Asset, mvel_execute\n"
                + "  from java import jclass\n"
                + "  from extera_utils.classes import Base, java_subclass, jclassbuilder, jMVELmethod, jMVELoverride, jfield, jpreconstructor, jconstructor, joverload\n"
                + "  from bygram._menu_condition import compile_condition\n"
                + "  assert compile_condition('sum = 0; foreach (x : [1, 2, 3]) { sum += x; }; return sum == 6 && account == 2;')({'account': 2})\n"
                + "  assert mvel_execute('return n + 2;', {'n': 3}, 'int') == 5\n"
                + "  @java_subclass('java.util.ArrayList', custom_name='TestProxy')\n"
                + "  class Test(Base):\n"
                + "   total = jfield('int', default=0)\n"
                + "   @staticmethod\n"
                + "   @jclassbuilder()\n"
                + "   def build(dex, generated, parent):\n"
                + "    TypeId = jclass('com.android.dx.TypeId')\n"
                + "    dex.declare(generated.getField(TypeId.INT, 'customField'), 1, None)\n"
                + "   add = jMVELoverride(arguments=[('value', 'java.lang.Object')], code='total += 1; return SUPER_add(value);')\n"
                + "   result = jMVELmethod(return_type='int', arguments=[('extra', 'int')], code='return total + extra + argc;')\n"
                + "  peer = Test.new_instance()\n"
                + "  assert peer.add('ok') and peer.size() == 1 and peer.total == 1 and peer.result(5) == 7\n"
                + "  assert peer.java.getClass().getField('customField') is not None\n"
                + "  assert 'TestProxy' in str(peer.java.getClass().getName())\n"
                + "  assert Test.from_java(peer.java) is peer\n"
                + "  @java_subclass('java.util.ArrayList')\n"
                + "  class Constructor(Base):\n"
                + "   @jpreconstructor(['int'])\n"
                + "   def prepare(cls, capacity): return 0\n"
                + "   def __init__(self, label): self.events = [label]\n"
                + "   @jconstructor(['int'])\n"
                + "   def initialized(self, capacity): self.events.append(capacity)\n"
                + "   def on_post_init(self, capacity): self.events.append(self.size())\n"
                + "   @joverload('add', ['java.lang.Object'])\n"
                + "   def append_item(self, item): return super().append_item(item)\n"
                + "  constructed = Constructor.new_instance(-1, init_args=['init'])\n"
                + "  assert constructed.events == ['init', -1, 0]\n"
                + "  assert constructed.add('ok') and constructed.size() == 1\n"
                + "  @java_subclass('java.util.ArrayList', methods=[('add', ['java.lang.Object'], 'append_item')], constructors=[[], ['int']])\n"
                + "  class Declared(Base):\n"
                + "   def append_item(self, item): return super().append_item(str(item).upper())\n"
                + "  empty = Declared.new_instance()\n"
                + "  sized = Declared.new_instance(4)\n"
                + "  assert empty.java.getClass() == sized.java.getClass()\n"
                + "  assert empty.add('ok') and empty.get(0) == 'OK'\n"
                + "  assert sized.add('other') and sized.get(0) == 'OTHER'\n"
                + "  try: Declared.new_instance(None, arg_types=['java.util.Collection'])\n"
                + "  except TypeError: pass\n"
                + "  else: raise AssertionError('unlisted constructor accepted')\n"
                + "  svg = Path(directory) / 'image.svg'\n"
                + "  svg.write_text('<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\" fill=\"red\"/></svg>')\n"
                + "  asset = Asset.from_path(svg)\n"
                + "  assert asset.to_svg_bitmap(10, 10).getWidth() == 10\n"
                + "  assert asset.to_svg_drawable() is not None\n"
                + "  lottie = Path(directory) / 'motion.json'\n"
                + "  lottie.write_text('{\"v\":\"5.7.4\",\"fr\":30,\"ip\":0,\"op\":30,\"w\":32,\"h\":32,\"layers\":[]}')\n"
                + "  assert Asset.from_path(lottie).to_lottie_drawable(16, 16).getFramesCount() > 0\n"
                + " def on_load(self): self.observed_events = []; self.context.settings.set('loaded', True)\n"
                + " def observed(self, event): self.observed_events.append(event)\n"
                + " def on_enable(self):\n  self.context.settings.set('enabled', True)\n  self.context.settings.set('nothing', None)\n  assert self.context.settings.get('nothing', 'default') is None\n"
                + " def create_settings(self):\n  from bygram.settings import TextInput, Action\n  return [TextInput('label', 'Label', 'ok', 4), Action('Click', lambda: None)]\n"
                + " def migrate_settings(self, old_version, new_version, values):\n  values['migrated'] = True\n  return values\n";
        try (FileOutputStream out = new FileOutputStream(new File(root, "main.py"))) { out.write(code.getBytes(StandardCharsets.UTF_8)); }
        PluginRuntime runtime = new PluginRuntime(); PluginScope scope = new PluginScope(metadata.id(), 1);
        PluginContext context = new PluginContext(scope, metadata, root, new File(root, "data"), PluginManager.get());
        PyObject plugin = runtime.load(root, metadata, context);
        boolean hooksAvailable = HookBridge.available();
        try {
            if (androidUi) plugin.callAttr("compatibility", root.getAbsolutePath());
            context.migrate(plugin); plugin.callAttr("on_load");
            context.configureSettings(plugin.callAttr("_settings_json").toString());
            for (int i = 0; i < 600; i++) context.configureSettings(plugin.callAttr("_settings_json").toString());
            plugin.callAttr("on_enable");
            PluginPackage.check(context.settingsGet("loaded", "").equals("true") && context.settingsGet("enabled", "").equals("true"), "LifecycleFailed");
            try { context.settingsSet("label", "\"too long\"", ""); throw new IOException("SettingValidatorFailed"); }
            catch (IllegalArgumentException expected) { }
            com.google.gson.JsonObject schema = new com.google.gson.JsonObject(); schema.addProperty("version", 2);
            PluginStore.write(new android.util.AtomicFile(new File(root, "data/schema.json")), schema);
            context.migrate(plugin);
            PluginPackage.check(PluginStore.read(new android.util.AtomicFile(new File(root, "data/global.json"))).get("migrated").getAsBoolean(), "MigrationFailed");
            PluginPackage.check(context.settingsGet("migrated", "").equals("true"), "MigrationCacheStale");
            try { context.writeText("global.json", "{}"); throw new IOException("StorageOverwroteSettings"); }
            catch (IOException expected) { PluginPackage.check("ReservedStoragePath".equals(expected.getMessage()), "StorageBoundaryFailed"); }
            try {
                context.configureSettings("{\"title\":\"Test\",\"items\":[{\"type\":\"Switch\",\"key\":\"atomic\",\"title\":\"A\",\"default\":true},{\"type\":\"Switch\",\"key\":\"bad\",\"title\":\"B\",\"default\":12}]}");
                throw new IOException("InvalidPageAccepted");
            } catch (IllegalArgumentException expected) { }
            context.settingsSet("atomic", "\"not a switch\"", "");
            hostChecks(root, manifest, context, plugin, androidUi);
            if (hooksAvailable) {
                PluginManifest secondMetadata = PluginManifest.parse(manifest.replace("test.bygram.fixture", "test.bygram.second").getBytes(StandardCharsets.UTF_8),
                        1, PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
                PluginScope secondScope = new PluginScope(secondMetadata.id(), 2);
                PluginContext secondContext = new PluginContext(secondScope, secondMetadata, root, new File(root, "data-second"), PluginManager.get());
                PyObject secondPlugin = runtime.load(root, secondMetadata, secondContext);
                try {
                    com.chaquo.python.Python.getInstance().getModule("bygram._selftest").callAttr("two_plugins", plugin, secondPlugin);
                    com.chaquo.python.Python.getInstance().getModule("bygram._selftest").callAttr("original_error", secondPlugin);
                    int dispatchers = HookBridge.dispatcherCount();
                    com.chaquo.python.Python.getInstance().getModule("bygram._selftest").callAttr("repeat", secondPlugin);
                    com.chaquo.python.Python.getInstance().getModule("bygram._selftest").callAttr("repeat", secondPlugin);
                    PluginPackage.check(HookBridge.dispatcherCount() == dispatchers && HookBridge.registrationCount() == 0, "HookMetadataGrewPerCycle");
                } finally { secondScope.close(); runtime.unload(secondPlugin, secondMetadata.id()); }
                com.chaquo.python.Python.getInstance().getModule("bygram._selftest").callAttr("hooks", plugin);
            } else {
                try { HookBridge.verify(); throw new IOException("BackendGateFailed"); }
                catch (IllegalStateException expected) {
                    PluginPackage.check("HookBackendUnavailable".equals(expected.getMessage()), "BackendGateFailed");
                }
            }
        } finally { scope.close(); runtime.unload(plugin, metadata.id()); }
        PluginPackage.check(HookBridge.registrationCount() == 0, "HookRetainedPluginCallbacks");
        PluginPackage.check(HookBridge.dispatcherCount() == 0, "HookRetainedNativeDispatchers");
        try { context.settingsSet("late", "true", ""); throw new IOException("GenerationFailed"); }
        catch (IllegalStateException expected) { }
        PluginPackage.check(HookBridge.Probe.value(2) == 4, "UnhookFailed");
        if (hooksAvailable) {
            for (int cycle = 0; cycle < 5; cycle++) {
                java.util.HashMap<Integer, String> values = new java.util.HashMap<>();
                for (int i = 0; i < 10000; i++) values.put(i, "value" + i);
                System.gc(); System.runFinalization(); Thread.sleep(100);
                PluginPackage.check("value9999".equals(values.get(9999)) && HookBridge.Probe.value(2) == 4, "PostHookGcFailed");
            }
        }
        storageChecks(root, manifest, code);
        lifecycleChecks(root, manifest);
        File outside = new File(getCacheDir(), "plugin-integration-outside-fixture");
        PluginPackage.check(outside.mkdir() || outside.isDirectory(), "FixtureUnavailable");
        File sentinel = new File(outside, "keep.txt");
        try (FileOutputStream out = new FileOutputStream(sentinel)) { out.write(1); }
        android.system.Os.symlink(outside.getAbsolutePath(), new File(root, "link").getAbsolutePath());
        PluginPackage.delete(root);
        PluginPackage.check(sentinel.exists(), "DeleteFollowedSymlink");
        PluginPackage.delete(outside);
        return "PASS: Java/Python bridge, private import, lifecycle, persistent settings, generation invalidation; "
                + (hooksAvailable ? "two-plugin hook ordering/conflict/owner cleanup, before/after chain, primitive conversion, replacement original once and unhook, post-hook GC/HashMap stress. "
                    : "Raw hooks SKIPPED: HookBackendUnavailable, gate rejects before native initialization. ")
                + "Atomic registry backup recovery, pending update and rollback; bulk cleanup before author callbacks and restart quarantine. "
                + "API " + android.os.Build.VERSION.SDK_INT + ", " + android.os.Build.SUPPORTED_ABIS[0];
    }
    private void hostChecks(File root, String manifest, PluginContext context, PyObject plugin, boolean androidUi) throws Exception {
        PluginManifest otherManifest = PluginManifest.parse(manifest.replace("test.bygram.fixture", "test.bygram.host_other").getBytes(StandardCharsets.UTF_8),
                1, PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
        PluginScope otherScope = new PluginScope(otherManifest.id(), 3);
        PluginContext other = new PluginContext(otherScope, otherManifest, root, new File(root, "data-host-other"), PluginManager.get());
        try (AutoCloseable first = context.subscribe("network.completed", plugin.get("observed"));
             AutoCloseable second = other.subscribe("network.completed", plugin.get("observed"))) {
            context.networkCompleted(12);
            PluginPackage.check(plugin.get("observed_events").asList().size() == 1, "NetworkEventCrossedOwner");
            other.networkCompleted(8);
            PluginPackage.check(plugin.get("observed_events").asList().size() == 2, "NetworkEventMissing");
        } finally { otherScope.close(); }
        context.networkCompleted(1);
        PluginPackage.check(plugin.get("observed_events").asList().size() == 2, "ClosedEventDelivered");
        if (androidUi) {
            try { org.telegram.ui.PluginHost.theme(context, "not-a-theme-key", 0); throw new IOException("UnknownThemeKeyAccepted"); }
            catch (IllegalArgumentException expected) { }
        }
        java.lang.reflect.Constructor<PluginManager> constructor = PluginManager.class.getDeclaredConstructor(); constructor.setAccessible(true);
        PluginManager isolated = constructor.newInstance();
        com.google.gson.JsonObject selected = context.manifest.metadata();
        selected.addProperty("id", "test.bygram.account_events"); selected.addProperty("account_scope", "selected_accounts");
        PluginManifest selectedManifest = PluginManifest.parse(selected.toString().getBytes(StandardCharsets.UTF_8), 1, PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
        PluginScope selectedScope = new PluginScope(selectedManifest.id(), 4);
        PluginContext selectedContext = new PluginContext(selectedScope, selectedManifest, root, new File(root, "data-account-events"), isolated);
        String identity = "0:123:00000000-0000-0000-0000-000000000001";
        java.lang.reflect.Field grants = PluginManager.class.getDeclaredField("accountGrants"); grants.setAccessible(true);
        grants.set(isolated, java.util.Collections.singletonMap(selectedManifest.id(), java.util.Collections.singleton(identity)));
        try {
            try { context.subscribe("account.logged_out", plugin.get("observed")); throw new IOException("GlobalAccountEventsAllowed"); }
            catch (IllegalStateException expected) { PluginPackage.check("AccountRequired".equals(expected.getMessage()), "AccountEventPolicyFailed"); }
            try (AutoCloseable owner = selectedContext.subscribe("account.logged_out", plugin.get("observed"));
                 AutoCloseable global = org.telegram.ui.PluginHost.subscribe(context, "account.logged_out", plugin.get("observed"));
                 AutoCloseable added = selectedContext.subscribe("account.added", plugin.get("observed"))) {
                org.telegram.ui.PluginHost.emitAccount("account.logged_out", identity, "{}");
                PluginPackage.check(plugin.get("observed_events").asList().size() == 3, "AccountEventLeakedToGlobalPlugin");
                org.telegram.ui.PluginHost.emitAccount("account.logged_out", "0:123:new-login", "{}");
                org.telegram.ui.PluginHost.emitAccount("account.added", "1:456:new-login", "{}");
                PluginPackage.check(plugin.get("observed_events").asList().size() == 3, "UngrantedAccountEventDelivered");
            }
        } finally { selectedScope.close(); isolated.queue.shutdown(); }
    }
    private void storageChecks(File root, String manifest, String code) throws Exception {
        File storeRoot = new File(root, "store");
        PluginStore store = new PluginStore(storeRoot);
        stageInstall(store, manifest, code, "one");
        String id = "test.bygram.fixture";
        String oldHash = store.plugins().getAsJsonObject(id).get("hash").getAsString();
        File data = new File(store.data, id);
        PluginPackage.check(data.mkdir(), "FixtureUnavailable");
        char[] characters = new char[600 * 1024]; java.util.Arrays.fill(characters, 'x');
        com.google.gson.JsonObject large = new com.google.gson.JsonObject(); large.addProperty("value", new String(characters));
        PluginStore.write(new android.util.AtomicFile(new File(data, "global.json")), large);
        PluginStore.write(new android.util.AtomicFile(new File(data, "account-0-123.json")), large);
        stageInstall(store, manifest.replace("1.0.0", "2.0.0"), code, "two");
        PluginPackage.check(store.plugins().getAsJsonObject(id).get("hash").getAsString().equals(oldHash), "UpdateActivatedEarly");
        store.applyPending(PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
        PluginPackage.check(new File(storeRoot, "registry.json").length() < 64 * 1024, "SnapshotBloatedRegistry");
        PluginPackage.check(!store.plugins().getAsJsonObject(id).get("desired").getAsBoolean(), "UpdateAutoEnabled");
        store.rollback(id);
        PluginPackage.check(store.plugins().getAsJsonObject(id).get("hash").getAsString().equals(oldHash), "RollbackFailed");
        PluginPackage.check(PluginStore.read(new android.util.AtomicFile(new File(data, "global.json"))).get("value").getAsString().length() == characters.length, "SettingsRollbackFailed");
        File registry = new File(storeRoot, "registry.json");
        try (FileOutputStream out = new FileOutputStream(new File(storeRoot, "registry.json.bak"))) { out.write(PluginPackage.read(registry)); }
        try (FileOutputStream out = new FileOutputStream(registry)) { out.write("corrupt".getBytes(StandardCharsets.UTF_8)); }
        PluginStore recovered = new PluginStore(storeRoot);
        PluginPackage.check(recovered.plugins().has(id), "AtomicRecoveryFailed");
        try (FileOutputStream out = new FileOutputStream(registry)) { out.write("corrupt".getBytes(StandardCharsets.UTF_8)); }
        PluginStore shadow = new PluginStore(storeRoot); shadow.applyRollbacks();
        PluginPackage.check(shadow.plugins().has(id), "RegistryBackupFailed");
    }
    private void stageInstall(PluginStore store, String manifest, String code, String name) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json")); zip.write(manifest.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("main.py")); zip.write(code.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        PluginPackage staged = PluginPackage.stage(new ByteArrayInputStream(buffer.toByteArray()), new File(store.staging, name), 1,
                PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
        store.install(staged);
    }
    private void lifecycleChecks(File root, String manifest) throws Exception {
        java.lang.reflect.Constructor<PluginManager> constructor = PluginManager.class.getDeclaredConstructor(); constructor.setAccessible(true);
        PluginManager manager = constructor.newInstance();
        PluginStore store = new PluginStore(new File(root, "lifecycle-store"));
        java.lang.reflect.Field storeField = PluginManager.class.getDeclaredField("store"); storeField.setAccessible(true); storeField.set(manager, store);
        java.lang.reflect.Field runningField = PluginManager.class.getDeclaredField("running"); runningField.setAccessible(true);
        java.util.Map<String, Object> running = (java.util.Map<String, Object>) runningField.get(manager);
        java.lang.reflect.Field runtimeField = PluginManager.class.getDeclaredField("runtime"); runtimeField.setAccessible(true);
        PluginRuntime runtime = (PluginRuntime) runtimeField.get(manager);
        Class<?> runningType = Class.forName("org.telegram.messenger.plugins.PluginManager$Running");
        java.lang.reflect.Constructor<?> runningConstructor = runningType.getDeclaredConstructor(PluginScope.class); runningConstructor.setAccessible(true);
        java.lang.reflect.Field pluginField = runningType.getDeclaredField("plugin"); pluginField.setAccessible(true);
        java.lang.reflect.Field enablingField = runningType.getDeclaredField("enabling"); enablingField.setAccessible(true);
        PyObject[] plugins = new PyObject[2]; PluginScope[] scopes = new PluginScope[2];
        java.util.concurrent.atomic.AtomicInteger closed = new java.util.concurrent.atomic.AtomicInteger();
        String code = "from bygram import Plugin\nimport threading\nclass Fixture(Plugin):\n"
                + " def __init__(self): self.waiting = False; self.event = threading.Event()\n"
                + " def on_disable(self): self.waiting = True; self.event.wait(5)\n";
        try {
            for (int i = 0; i < 2; i++) {
                String text = manifest.replace("test.bygram.fixture", "test.bygram.stopping" + i);
                stageInstall(store, text, code, "stopping" + i);
                PluginManifest metadata = PluginManifest.parse(text.getBytes(StandardCharsets.UTF_8), 1, PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
                PluginScope scope = scopes[i] = new PluginScope(metadata.id(), 100 + i);
                PluginContext context = new PluginContext(scope, metadata, store.packageDir(store.plugins().getAsJsonObject(metadata.id())), new File(store.data, metadata.id()), manager);
                plugins[i] = runtime.load(store.packageDir(store.plugins().getAsJsonObject(metadata.id())), metadata, context);
                scope.own(closed::incrementAndGet);
                Object instance = runningConstructor.newInstance(scope); pluginField.set(instance, plugins[i]); enablingField.setBoolean(instance, true); running.put(metadata.id(), instance);
            }
            java.lang.reflect.Method stopAll = PluginManager.class.getDeclaredMethod("stopAll"); stopAll.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            Thread stopping = new Thread(() -> { try { stopAll.invoke(manager); } catch (Throwable error) { failure.set(error); } }, "PluginCleanupFixture");
            stopping.start(); long deadline = android.os.SystemClock.elapsedRealtime() + 4000;
            while (!plugins[0].get("waiting").toBoolean() && !plugins[1].get("waiting").toBoolean() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(10);
            PluginPackage.check(closed.get() == 2 && !scopes[0].isActive() && !scopes[1].isActive(), "BulkCleanupBlockedByAuthor");
            for (PyObject plugin : plugins) plugin.get("event").callAttr("set");
            stopping.join(5000); PluginPackage.check(!stopping.isAlive() && failure.get() == null && running.isEmpty(), "BulkCleanupFailed");
            PluginScope loading = new PluginScope("test.bygram.stopping0", 150);
            PluginManifest loadingManifest = PluginManifest.parse(manifest.replace("test.bygram.fixture", loading.id).getBytes(StandardCharsets.UTF_8),
                    1, PluginManager.hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
            PluginContext loadingContext = new PluginContext(loading, loadingManifest,
                    store.packageDir(store.plugins().getAsJsonObject(loading.id)), new File(store.data, loading.id), manager);
            PyObject loadingPlugin = runtime.load(store.packageDir(store.plugins().getAsJsonObject(loading.id)), loadingManifest, loadingContext);
            Object loadingInstance = runningConstructor.newInstance(loading); pluginField.set(loadingInstance, loadingPlugin); running.put(loading.id, loadingInstance);
            java.lang.reflect.Method stop = PluginManager.class.getDeclaredMethod("stop", String.class); stop.setAccessible(true); stop.invoke(manager, loading.id);
            PluginPackage.check(!loadingPlugin.get("waiting").toBoolean(), "DisableCalledBeforeEnable");
            PluginScope blocked = new PluginScope("test.bygram.stopping0", 200);
            PluginPackage.check(blocked.beginCallback(), "FixtureUnavailable");
            running.put(blocked.id, runningConstructor.newInstance(blocked));
            java.lang.reflect.Method fail = PluginManager.class.getDeclaredMethod("fail", String.class, Exception.class); fail.setAccessible(true);
            fail.invoke(manager, blocked.id, new IOException("FixtureFailure"));
            PluginPackage.check(store.plugins().getAsJsonObject(blocked.id).get("error").getAsString().equals("PluginRestartRequired"), "RestartQuarantineOverwritten");
            store.plugins().getAsJsonObject(blocked.id).addProperty("desired", true); store.save();
            java.lang.reflect.Method mutate = PluginManager.class.getDeclaredMethod("stopForMutation", String.class); mutate.setAccessible(true);
            try { mutate.invoke(manager, blocked.id); throw new IOException("MutationAcceptedBlockedPlugin"); }
            catch (java.lang.reflect.InvocationTargetException expected) {
                PluginPackage.check("PluginRestartRequired".equals(expected.getCause().getMessage()), "MutationErrorChanged");
            }
            PluginPackage.check(!new PluginStore(store.root).plugins().getAsJsonObject(blocked.id).get("desired").getAsBoolean(), "MutationFailureAutoEnabledPlugin");
            blocked.endCallback();
        } finally {
            for (int i = 0; i < plugins.length; i++) if (plugins[i] != null) {
                plugins[i].get("event").callAttr("set"); scopes[i].close(); runtime.unload(plugins[i], scopes[i].id);
            }
            manager.queue.shutdown();
        }
    }
}
