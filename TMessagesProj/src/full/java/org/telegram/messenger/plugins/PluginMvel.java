package org.telegram.messenger.plugins;

import com.chaquo.python.PyObject;
import org.mvel2.DataConversion;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.optimizers.OptimizerFactory;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public final class PluginMvel {
    static {
        // Android cannot load the JVM bytecode produced by the ASM optimizer.
        OptimizerFactory.setDefaultOptimizer(OptimizerFactory.SAFE_REFLECTIVE);
    }

    public static Serializable compile(String source) {
        ParserContext context = new ParserContext();
        context.getParserConfiguration().setClassLoader(PluginMvel.class.getClassLoader());
        return MVEL.compileExpression(source, context);
    }

    public static boolean matches(Serializable expression, Map<String, Object> variables) {
        return Boolean.TRUE.equals(MVEL.executeExpression(expression, variables, Boolean.class));
    }

    public static final class Body {
        final Serializable expression;
        final String[] names;

        public Body(String source, String[] names) {
            expression = compile(source);
            this.names = names.clone();
        }

        Object run(Object proxy, PyObject peer, Object[] args, Class<?> resultType) throws IllegalAccessException {
            Map<String, Object> variables = new HashMap<>();
            Map<String, java.lang.reflect.Field> fields = new HashMap<>();
            for (java.lang.reflect.Field field : proxy.getClass().getFields()) {
                int flags = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(flags) || java.lang.reflect.Modifier.isFinal(flags)) continue;
                fields.put(field.getName(), field);
                variables.put(field.getName(), field.get(proxy));
            }
            variables.put("java", proxy);
            variables.put("python", peer);
            variables.put("py", peer);
            variables.put("self", peer);
            variables.put("args", args);
            variables.put("argc", args.length);
            for (int i = 0; i < args.length; i++) {
                variables.put("arg" + i, args[i]);
                variables.put(names[i], args[i]);
            }
            Map<String, Object> original = new HashMap<>(variables);
            Object result = MVEL.executeExpression(expression, proxy, variables);
            for (Map.Entry<String, java.lang.reflect.Field> entry : fields.entrySet()) {
                String name = entry.getKey();
                if (!java.util.Objects.equals(original.get(name), variables.get(name)))
                    entry.getValue().set(proxy, DataConversion.convert(variables.get(name), entry.getValue().getType()));
            }
            return resultType == void.class ? null : DataConversion.convert(result, resultType);
        }
    }
}
