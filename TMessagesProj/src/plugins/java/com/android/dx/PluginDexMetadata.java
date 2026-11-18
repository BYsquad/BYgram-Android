package com.android.dx;

import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.rop.annotation.Annotation;
import com.android.dx.rop.annotation.Annotations;
import com.android.dx.rop.annotation.AnnotationVisibility;
import com.android.dx.rop.annotation.NameValuePair;
import com.android.dx.rop.cst.CstArray;
import com.android.dx.rop.cst.CstString;
import java.lang.reflect.Field;
import java.util.Map;

/** DexMaker 2.28.6 has no public method-annotation API. */
public final class PluginDexMetadata {
    private PluginDexMetadata() {}

    public static void addThrows(DexMaker dex, TypeId<?> type, Map<MethodId<?, ?>, Class<?>[]> methods)
            throws ReflectiveOperationException {
        if (methods.isEmpty()) return;
        Field types = DexMaker.class.getDeclaredField("types");
        types.setAccessible(true);
        Map<?, ?> declarations = (Map<?, ?>) types.get(dex);
        ClassDefItem definition = ((DexMaker.TypeDeclaration) declarations.get(type)).toClassDefItem();
        for (Map.Entry<MethodId<?, ?>, Class<?>[]> entry : methods.entrySet()) {
            Class<?>[] exceptions = entry.getValue();
            CstArray.List values = new CstArray.List(exceptions.length);
            for (int i = 0; i < exceptions.length; i++) {
                if (!Throwable.class.isAssignableFrom(exceptions[i]))
                    throw new IllegalArgumentException("throws requires Java Throwable classes");
                values.set(i, TypeId.get(exceptions[i]).constant);
            }
            values.setImmutable();
            Annotation annotation = new Annotation(TypeId.get("Ldalvik/annotation/Throws;").constant,
                    AnnotationVisibility.SYSTEM);
            annotation.put(new NameValuePair(new CstString("value"), new CstArray(values)));
            annotation.setImmutable();
            Annotations annotations = new Annotations();
            annotations.add(annotation);
            annotations.setImmutable();
            definition.addMethodAnnotations(entry.getKey().constant, annotations, dex.getDexFile());
        }
    }
}
