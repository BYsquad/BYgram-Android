const example = (caption, source) => `<figure><figcaption>${caption}</figcaption><pre><code>${source
  .trim().replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')}</code></pre></figure>`;

export const guideGroups = [
  ['Getting started', [
    ['', 'BYgram plugin documentation', 'Build Android plugins in Python 3.11 with BYgram SDK v1.', [
      ['Choose an API', '<p>Use <code>bygram.Plugin</code> for the native SDK. Existing exteraGram plugins can use the compatibility modules <code>base_plugin</code>, <code>client_utils</code>, <code>file_utils</code> and <code>extera_utils</code>.</p>'],
      ['Build a first plugin', '<p>Start with the <a href="package/">package guide</a>, then read the <a href="lifecycle/">lifecycle reference</a>. The <a href="../#catalog">BYgram catalog</a> contains installable examples.</p>'],
      ['Port a project', '<p>Follow the <a href="elyx/project-structure/">Elyx project guide</a> for structured archives or consult the individual <a href="api/accounts/">compatibility API articles</a>.</p>'],
    ]],
    ['package/', 'Build and install a package', 'Create a manifest, package your source and enable it in BYgram.', [
      ['Package layout', '<p>A <code>.byplugin</code> contains <code>manifest.json</code> and a Python entry module such as <code>main.py</code>. Use a dotted identifier, a numeric three-component version and an <code>entrypoint</code> in <code>path.py:ClassName</code> form.</p><p>See the <a href="../../#manifest">manifest reference</a> for SDK bounds, capabilities and account scope.</p>'],
      ['Build and install', '<p>To build without cloning BYgram, use Python 3.10 or later and save <a href="https://raw.githubusercontent.com/BYsquad/BYgram-Android/9f4695b9616b7dcfef1449dd5f697c10311b8027/Tools/package_plugin.py"><code>package_plugin.py</code></a> next to your <code>my-plugin</code> folder. Run the command from the directory containing the script.</p>' + example('Build the package', 'python ./package_plugin.py ./my-plugin ./example.byplugin') + '<p>Open the package through Settings → BYgram settings → Plugins → Install from file. Review the manifest, enable the engine and approve execution for the package. Grant only the accounts the plugin needs.</p>'],
    ]],
    ['ai-skills/', 'AI skills for BYgram plugins', 'Use a coding agent to write, port, package and test BYgram plugins.', [
      ['Install without cloning', '<p><a href="../../install_bygram_skills.py" download>Download the Python skill installer</a>. From your downloads folder, run it with Python 3.10 or later. The skill links to the online SDK documentation. The installer includes examples, the packager and the exteraGram converter.</p>' + example('Install all skills', 'python install_bygram_skills.py') + '<p>To install for one client, append <code>codex</code>, <code>claude</code>, <code>gemini</code>, <code>cursor</code>, <code>copilot</code> or <code>deepseek</code>. If <code>python</code> is unavailable, use <code>py</code> on Windows or <code>python3</code> on Linux and macOS.</p>'],
      ['Use the skill', '<p>Start a new agent session. Use <code>$bygram-plugin</code> in Codex or <code>/bygram-plugin</code> in Claude Code and Copilot in VS Code. In Gemini CLI or Cursor, ask the agent to use <code>bygram-plugin</code>. For DeepSeek, open the prompt at the path printed by the installer and paste it into chat. If the chat cannot open links, add the relevant excerpts from the documentation website.</p><p>Example request: “Add a chat menu item that shows a configurable tip. Build the .byplugin and list the checks you ran.”</p>'],
    ]],
    ['lifecycle/', 'Lifecycle and cleanup', 'Handle enablement, disablement and cooperative background shutdown.', [
      ['Lifecycle callbacks', '<p>The native class has <code>on_load</code>, <code>on_enable</code>, <code>on_disable</code> and <code>on_unload</code>. Compatibility plugins implement <code>on_plugin_load</code> and <code>on_plugin_unload</code> and can receive app events through <code>on_app_event</code>.</p>'],
      ['Cleanup and stop signal', '<p><code>register_cleanup(callback)</code> runs registered zero-argument callbacks in reverse order on unload, including when <code>on_plugin_unload</code> throws. <code>stop_event.is_set()</code> and <code>stop_event.wait(timeout)</code> let workers observe generation invalidation. Use finite waits and keep cleanup short.</p>' + example('Close a resource', 'self.register_cleanup(resource.close)\nif self.stop_event.is_set():\n    return')],
    ]],
  ]],
  ['Catalog', [
    ['catalog/paid/', 'Publish a paid plugin', 'Submit a signed BYgram plugin and connect your TON wallet.', [
      ['Package and sign', '<p>Choose a unique manifest ID and a version. Keep the same ID and signing key for updates. Store the RSA private key outside the source directory. Install OpenSSL and get the packager from the <a href="../../package/">package guide</a>.</p>' + example('Package for submission', 'python ./package_plugin.py ./my-plugin ./release.byplugin --signing-key ./author.pem')],
      ['Submit and connect a wallet', '<p>In the current BYgram beta, open BYgram Settings → Plugins → Official Catalog → Submit a plugin. Set the price and select the signed archive. To accept TON, connect an active V3R2, V4R2 or V5R1 wallet using the <a href="https://byxarek.site/TEST-PROJECT/BYgram/api/plugin-store-web.php?wallet=1">author wallet page</a> and the same Telegram account. Moderators review the exact SHA-256.</p>'],
      ['Payments and updates', '<p>TON goes directly to the author. A wallet change applies to new orders. Confirmed purchases are tied to the buyer’s Telegram account. The author handles mandatory TON refunds, and BYgram support can forward a request.</p>'],
    ]],
  ]],
  ['Elyx', [
    ['elyx/', 'Elyx overview', 'Import a structured exteraGram project into BYgram.', [
      ['Supported archives', '<p>Use <code>.elyx</code>, <code>.eaf</code> or their ZIP variants. BYgram reads metadata and converts the archive to <code>.byplugin</code> at import. Python 3.11 bytecode can be used when the plugin supplies separate metadata.</p>'],
      ['Where to start', '<ol><li><a href="project-structure/">Lay out the project</a></li><li><a href="metadata/">Describe it in metadata</a></li><li><a href="development/">Convert, build and debug it</a></li></ol>'],
    ]],
    ['elyx/project-structure/', 'Elyx project structure', 'Define the entry point and paths for the project files.', [
      ['Example layout', example('Project layout', 'refmap.json\nmetainfo.json\nsrc/main.py\nsrc/helpers.py\nassets/help.txt') + example('refmap.json', '{"main":"src/main.py","metainfo":"metainfo.json","assets":"assets"}')],
      ['Import modules', '<p>Keep module paths inside the archive. Python modules and Elyx data modules use the plugin environment. For dynamic imports use <code>elyx.import_module(name)</code>. Package-local resources remain separate from persistent plugin data.</p>'],
    ]],
    ['elyx/metadata/', 'Elyx metadata', 'Describe the project and understand how BYgram assigns an identity.', [
      ['Identity and version', example('metainfo.json', '{"id":"weather_demo","name":"Weather Demo","description":"Weather tools","author":"Example","version":"1.2"}') + '<p>BYgram derives a stable package ID from the original ID and normalizes <code>1.2</code> to <code>1.2.0</code>. Keep the original ID stable across updates.</p>'],
      ['Catalog metadata', '<p>Supply a literal name, author and description. Sticker references such as <code>exteraPlugins/1</code> are supported. BYgram has its own SDK and build checks for the converted manifest.</p>'],
    ]],
    ['elyx/assets/', 'Elyx assets and data imports', 'Read bundled assets and convert them for Android UI.', [
      ['Read files', '<p>Look up an asset through <code>elyx.assets</code> and use <code>content_bytes()</code>, <code>content_string()</code>, <code>content_json()</code> or <code>content_yaml()</code>. Wrappers can provide Android drawables or Telegram image locations.</p>' + example('Read text from a bundled asset', 'from elyx import assets\n\nhelp_text = assets.get("help.txt").content_string()')],
      ['Temporary assets', '<p><code>Asset.temp_asset_from_url(url, filename)</code> uses HTTPS and the network capability. Call it on a worker and treat the returned file as generation-scoped cache data.</p>'],
    ]],
    ['elyx/localization/', 'Elyx localization and settings', 'Keep translatable strings and persistent settings separate from source.', [
      ['Localized strings', '<p>Put language files at the localization path specified by the project. BYgram reads JSON, YAML and literal Python localization declarations. Use <code>elyx.strings.get(...)</code> or pass an explicit locale where needed.</p>'],
      ['Fallbacks', '<p>Strings use the app locale and can fall back to English. The converted catalog card has its own manifest metadata. Change string resources for runtime labels and regenerate the package when catalog metadata changes.</p>'],
    ]],
    ['elyx/settings/', 'Elyx settings storage', 'Read and update persistent configuration through the Elyx settings controller.', [
      ['Per-plugin values', '<p><code>elyx.settings</code> provides key reads and writes plus <code>get_settings()</code> and <code>clear_settings()</code>. Use <code>context.account_settings(account)</code> for native settings allocated to an authorized account.</p>'],
      ['Update the interface', example('Read and update a setting', 'from elyx import settings\n\nunits = settings.get_setting("units", "metric")\nsettings.set_setting("units", units, reload_settings=True)')],
    ]],
    ['elyx/runtime/', 'Elyx runtime API', 'Access the runtime environment of a converted project.', [
      ['Environment and modules', '<p><code>get_environment()</code> looks up the current project environment. Use <code>elyx.import_module(name)</code> for dynamic module lookup and <code>metainfo</code> or <code>refmap</code> to inspect project metadata.</p>'],
      ['Java adapters', '<p><code>gen</code> and <code>gen2</code> adapt callbacks to Java interfaces. <code>mvel_execute</code> evaluates code through the host MVEL bridge. Run Android UI work on the UI thread.</p>'],
    ]],
    ['elyx/dependencies/', 'Elyx dependencies', 'Declare dependencies on other plugins and on Python packages.', [
      ['Plugin dependencies', '<p>Use <code>requires</code> for named plugins and optional minimum versions. BYgram validates the dependency graph and enables dependencies before the consumer. A missing dependency may expose a URL for the user to open and confirm separately.</p>'],
      ['Python packages', '<p>Pure-Python wheels are resolved and bundled during conversion, with package-local imports. Binary third-party wheels inside plugin archives are rejected. The client bundles libraries including <code>requests</code>, Pillow, <code>lxml</code> and PyYAML.</p>'],
    ]],
    ['elyx/development/', 'Develop and debug Elyx', 'Convert an archive and test it against the Android host.', [
      ['Convert a package', example('Local conversion', 'python Tools/port_extera_plugin.py weather.elyx weather.byplugin --ui --resolve-pypi') + '<p>Grant accounts and capabilities needed by the example. Pass a hook manifest with exact signatures when using low-level ART hooks.</p>'],
      ['Iterate with the Dev Server', '<p>The BYgram debug server supports an ADB-forwarded connection, package deployment, watch and patch operations. Use the <a href="../../../#dev-server">BYgram Dev Server reference</a> for commands and IDE debugger setup.</p>'],
    ]],
  ]],
  ['API reference', [
    ['api/accounts/', 'AccountClient and account scope', 'Bind requests, controllers and message operations to one account.', [
      ['Choose an account', '<p><code>AccountClient(account)</code> binds to the authorized login generation. <code>get_client()</code> uses the callback account when present and otherwise uses the selected account. <code>account_scope(account)</code> temporarily changes the implicit account used by module helpers.</p>' + example('Read the account configuration', 'from client_utils import AccountClient, account_scope, get_user_config\n\nclient = AccountClient(account)\nconfig = client.get_user_config()\nwith account_scope(account):\n    assert get_user_config() == config')],
      ['Controller and message methods', '<p>The bound client exposes Telegram controllers, storage, file loader, notification center and user config. Use <code>send_request(request, fn)</code>, <code>send_message(params, parse_mode)</code>, <code>send_text</code>, <code>send_photo</code>, <code>send_document</code>, <code>send_video</code>, <code>send_audio</code> and <code>edit_message</code>. Java host methods recheck account identity before sensitive operations.</p>'],
    ]],
    ['api/file-hooks/', 'File utilities and extension hooks', 'Handle opening specific attachments from Telegram UI.', [
      ['Register and remove', example('Inside a compatibility plugin', 'from file_utils import FilesController\n\ninfo = FilesController.FileInfo(\n    "example", self.open_example,\n    whitelist_places=[FilesController.Place.ChatActivity],\n)\nsecret = self.add_file_hook(info)\n# Optional early removal\nself.remove_file_hook(info.ext, secret)') + '<p><code>FilesController.register(info)</code> and <code>unregister(ext, secret)</code> are the module-level equivalents. Duplicate registrations raise <code>ExtensionAlreadyRegistered</code>. The host removes registrations when the plugin stops.</p>'],
      ['Callback and access', '<p>The callback receives <code>OnClickArgs</code> with the opening place, Java File, file name, message, activity and parent fragment when available. <code>file_name</code> uses the original document name when Telegram provides it. Optional <code>get_icon</code> returns a Drawable used by the document list. The hook needs <code>ui</code>, <code>messages.observe</code> and an authorized account. Protected and secret attachments stay on the standard path.</p>'],
      ['File helpers', '<p><code>get_plugins_dir()</code> resolves the calling plugin’s data directory. Use <code>read_file</code>, <code>read_file_bytes</code>, <code>write_file</code>, <code>write_file_bytes</code> and <code>list_dir</code> for common storage operations.</p>'],
    ]],
    ['api/java-proxy/', 'Managed Java proxies', 'Create real Java subclasses and control their Python peers.', [
      ['Construct and bind', example('Managed proxy', 'from extera_utils.classes import Base, java_subclass, joverride\n\n@java_subclass("java.util.ArrayList")\nclass MyList(Base):\n    @joverride("size", [])\n    def size(self):\n        return super().size()\n\npeer = MyList.new_instance()\nraw = peer.java\nassert MyList.from_java(raw) is peer') + '<p>Use <code>java_class()</code> when Java needs the generated Class, <code>new_java_instance()</code> for a raw object, and <code>from_java(..., init_args=...)</code> to recover or attach a peer. <code>extends(...)</code> is a decorator entry point.</p>'],
      ['Lifecycle', '<p>Use <code>on_pre_init</code> and <code>jpreconstructor</code> for constructor arguments. <code>on_post_init</code> runs once the peer and its Java object are available. <code>jrelease()</code> calls <code>on_release()</code> and disconnects the Python overrides from the Java instance.</p>'],
      ['Decorator helpers', example('Declare a checked exception and Java visibility', 'from extera_utils.classes import Base, JHelper\n\n@Base.extends("java.lang.Object")\nclass Example(Base):\n    @JHelper.Method("describe", "java.lang.String", ["int"],\n                    modifiers="protected final", throws=["java.io.IOException"])\n    def describe(self, count):\n        return str(count)') + '<p>Use explicit argument types for ambiguous overloads. The generated Java method exposes its declared exceptions through reflection. New Python and MVEL methods, fields, and field accessors can use the <code>static</code> modifier. Declare a static Python method without <code>self</code>.</p>'],
      ['Java object access', '<p>Use <code>JA</code> and <code>JNA</code> to enable and disable non-public lookup. <code>JGS</code> and <code>JNGS</code> control JavaBean-style properties. <code>JIR</code> keeps the receiver in a method chain, and <code>JNIR</code> restores the method result. <code>JS</code> enables safe chaining with <code>java.JNone</code> and <code>JNS</code> disables it.</p>'],
    ]],
    ['api/intents/', 'Android Intent handlers', 'Observe Android intents and optionally handle them before the app.', [
      ['Registration', '<p>Use <code>IntentsManager.new_global_before_handler(callback, **filters)</code> or <code>new_global_after_handler</code>. The returned handle can be closed. A before-handler can return <code>True</code> to consume an intent.</p>'],
      ['Match the action', '<p>Use <code>scheme</code>, <code>host</code>, <code>path</code>, <code>action</code>, MIME type, categories and flag filters. Without an explicit action, the handler targets <code>ACTION_VIEW</code> links. BYgram dispatches callbacks after account access and lock-screen checks.</p>'],
    ]],
    ['api/settings/', 'Settings and custom rows', 'Build native plugin settings using the compatibility or native SDK.', [
      ['Standard settings', '<p>Use <code>ui.settings.Switch</code>, <code>Selector</code>, <code>Input</code>, <code>EditText</code>, <code>Text</code>, <code>Header</code> and <code>Divider</code>. Return rows from <code>create_settings()</code>. The host stores values and triggers callbacks.</p>'],
      ['Custom rows', '<p>Use <code>Custom</code> for an Android View or UItem. <code>SimpleSettingFactory</code> separates creation, binding and click behavior. Call <code>set_setting(..., reload_settings=True)</code> when an edit changes the structure of the settings page.</p>'],
    ]],
    ['api/ui/', 'Dialogs, bulletins and Android helpers', 'Show host UI and run work on the proper thread.', [
      ['Dialogs and feedback', '<p><code>ui.alert.AlertDialogBuilder</code> supports message dialogs, buttons, lists and custom views. <code>ui.bulletin.BulletinHelper</code> provides transient messages, buttons and undo actions. Use the current fragment while it is still attached.</p>'],
      ['UI and background work', '<p>Use <code>android_utils.run_on_ui_thread</code> for Android UI. Use <code>client_utils.run_on_queue</code> or <code>context.work.submit</code> for background work. For styled messages, <code>send_text(..., parse_mode="html")</code> and <code>parse_mode="markdown"</code> convert markup to Telegram entities.</p>'],
    ]],
    ['api/text-formatting/', 'Text formatting', 'Turn markup into Telegram message or caption entities.', [
      ['Send formatted messages', example('Account-bound markup', 'client.send_text(peer, "<b>Ready</b>", parse_mode="html")') + '<p>Use <code>parse_mode="markdown"</code> for Markdown. The formatted text and its entities are sent through the account-bound host API.</p>'],
      ['Work with entities directly', '<p><code>extera_utils.text_formatting.parse_text</code> returns the text and entities. For a caption, request caption output. Telegram entity offsets use UTF-16 code units, so preserve the returned offsets when writing a reply or update.</p>'],
    ]],
    ['api/reflection/', 'Java reflection and hooks', 'Resolve Telegram classes and exact signatures before registering a hook.', [
      ['Inspect host classes', '<p>Use <code>java.jclass</code> or the helpers in <code>hook_utils</code> to inspect classes and declared members. Telegram Java names and overloads can change between builds.</p>'],
      ['Register ART hooks', '<p>Declare <code>hooks.raw</code>, the relevant build ranges, and <code>hook_targets</code> in the BYgram manifest. The host verifies the declared signatures and probes the backend before activating the plugin. The generated Java proxies described in <a href="../java-proxy/">Managed Java proxies</a> use a separate DexMaker bridge.</p>'],
    ]],
  ]],
];
