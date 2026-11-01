"""Controlled debug fixture, invoked only by the disabled-in-release diagnostic Activity."""
def _stage(name):
    from java import jclass
    jclass("android.util.Log").i("BYgramHookTest", name)


def original_error(plugin):
    _stage("original_error")
    from java import jclass
    target = "org.telegram.messenger.plugins.HookBridge$Probe"
    probe = jclass(target)()
    error_type = jclass("java.lang.IllegalStateException")
    handle = plugin.context.hooks.replace(target, "throwsOriginal", lambda call: call.call_original(), [])
    try:
        for _ in range(3):
            try: probe.throwsOriginal()
            except error_type as error: assert str(error.getMessage()) == "fixture"
            else: raise AssertionError("original error lost")
        assert plugin.context._bridge.active(), "original Java exceptions quarantined replacement"
    finally: handle.close()


def two_plugins(first, second):
    _stage("two_plugins")
    from java import jclass
    probe = jclass("org.telegram.messenger.plugins.HookBridge$Probe")
    target = "org.telegram.messenger.plugins.HookBridge$Probe"
    visits = []
    def before(name, increment):
        def callback(call):
            visits.append(name)
            call.set_arg(0, int(call.args[0]) + increment)
        return callback
    one = first.context.hooks.method(target, "value", ["int"], before("one", 1),
        lambda call: visits.append("after-one"), priority=2)
    two = second.context.hooks.method(target, "value", ["int"], before("two", 2),
        lambda call: visits.append("after-two"), priority=1)
    try:
        assert probe.value(2) == 10
        assert visits == ["one", "two", "after-two", "after-one"]
        one.close()
        assert probe.value(2) == 8, "closing one plugin removed another owner's hook"
    finally:
        one.close(); two.close()
    replacement = first.context.hooks.replace(target, "value", lambda call: 42, ["int"])
    try:
        try: second.context.hooks.replace(target, "value", lambda call: 99, ["int"])
        except Exception: pass
        else: raise AssertionError("two plugins own one replacement")
        assert probe.value(2) == 42
    finally: replacement.close()
    assert probe.value(2) == 4


def repeat(plugin):
    from java import jclass
    target = "org.telegram.messenger.plugins.HookBridge$Probe"
    probe = jclass(target)
    for _ in range(100):
        handle = plugin.context.hooks.method(target, "value", ["int"], before=lambda call: call.set_arg(0, int(call.args[0]) + 1))
        assert probe.value(2) == 6
        handle.close()
        assert probe.value(2) == 4


def hooks(plugin):
    _stage("hooks.warmup")
    from java import jclass
    probe = jclass("org.telegram.messenger.plugins.HookBridge$Probe")
    target = "org.telegram.messenger.plugins.HookBridge$Probe"
    ctx = plugin.context
    for _ in range(100): assert probe.value(2) == 4
    visits = []
    _stage("hooks.constructor")
    constructor = ctx.hooks.constructor(target, before=lambda call: visits.append("before"), after=lambda call: visits.append("after"))
    instance = probe()
    assert visits == ["before", "after"], "constructor callbacks"
    constructor.close()
    instance = probe()
    assert len(visits) == 2, "constructor unhook"
    def label_before(call):
        assert call.args[0] is None
        call.set_arg(0, "changed")
    _stage("hooks.private_overload")
    private = ctx.hooks.method(target, "label", ["java.lang.String"], label_before, lambda call: call.set_result("after"))
    overload = ctx.hooks.method(target, "label", ["int"], after=lambda call: call.set_result("overloaded"))
    assert instance.privateLabel(None) == "after", "private method/null conversion"
    assert instance.label(3) == "overloaded", "overload selection"
    private.close(); overload.close()
    assert instance.privateLabel(None) is None and instance.label(3) == "int:3"
    seen = []
    _stage("hooks.original_throwable")
    original_error = ctx.hooks.method(target, "throwsOriginal", after=lambda call: seen.append(call.exception is not None))
    try: instance.throwsOriginal()
    except Exception: pass
    else: raise AssertionError("original throwable swallowed")
    assert seen == [True]
    original_error.close()
    def before1(call): call.set_arg(0, int(call.args[0]) + 1)
    def before2(call): call.set_arg(0, int(call.args[0]) + 2)
    def after1(call): call.set_result(int(call.result) + 1)
    def after2(call): call.set_result(int(call.result) + 2)
    _stage("hooks.chain")
    one = ctx.hooks.method(target, "value", ["int"], before1, after1, priority=2)
    two = ctx.hooks.method(target, "value", ["int"], before2, after2, priority=1)
    assert probe.value(2) == 13, "before/after ordering or primitive conversion"
    one.close()
    assert probe.value(2) == 10, "unhook one owner"
    two.close()
    assert probe.value(2) == 4, "unhook all"
    def reentrant(call): assert probe.value(1) == 2
    _stage("hooks.reentrant")
    recursive = ctx.hooks.method(target, "value", ["int"], before=reentrant)
    assert probe.value(2) == 4, "reentrancy guard"
    recursive.close()
    _stage("hooks.repeat100")
    for _ in range(100):
        handle = ctx.hooks.method(target, "value", ["int"], before1, after1)
        assert probe.value(2) == 7
        handle.close()
        assert probe.value(2) == 4, "enable/disable cleanup"
    def replacement(call):
        result = call.call_original()
        assert call.call_original() == result
        raise RuntimeError("after original")
    _stage("hooks.replacement")
    handle = ctx.hooks.replace(target, "value", replacement, ["int"])
    try:
        ctx.hooks.replace(target, "value", lambda call: 0, ["int"])
    except Exception: pass
    else: raise AssertionError("replacement conflict accepted")
    previous = probe.calls
    assert probe.value(2) == 4
    assert probe.calls == previous + 1, "original called twice"
    handle.close()
    _stage("hooks.bad_values")
    bad = ctx.hooks.method(target, "value", ["int"], before=lambda call: call.set_arg(0, "wrong type"))
    assert probe.value(2) == 4, "invalid argument reached original"
    bad.close()
    bad = ctx.hooks.replace(target, "value", lambda call: None, ["int"])
    assert probe.value(2) == 4, "invalid result reached caller"
    bad.close()
    assert not ctx._bridge.active(), "three callback errors did not quarantine scope"
