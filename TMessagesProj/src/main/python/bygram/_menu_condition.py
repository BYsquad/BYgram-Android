"""MVEL conditions for ported plugin menus."""


def compile_condition(text):
    from java import jclass
    runtime = jclass("org.telegram.messenger.plugins.PluginMvel")
    expression = runtime.compile(text)

    def visible(values):
        variables = jclass("java.util.HashMap")()
        for key, value in values.items():
            variables.put(key, value)
        return runtime.matches(expression, variables)

    return visible
