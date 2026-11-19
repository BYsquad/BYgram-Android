"""Java reflection helpers for exteraGram plugins."""


def find_class(class_name):
    from java import jclass
    try:
        return jclass(class_name).getClass()
    except Exception:
        return None


def _field(clazz, name):
    from java import jclass
    if isinstance(clazz, type):
        clazz = clazz.getClass()
    missing = jclass("java.lang.NoSuchFieldException")
    while clazz is not None:
        try:
            field = clazz.getDeclaredField(name)
            field.setAccessible(True)
            return field
        except missing:
            clazz = clazz.getSuperclass()
    return None


def _get(clazz, obj, name):
    try:
        field = _field(clazz, name)
        return None if field is None else field.get(obj)
    except Exception:
        return None


def _set(clazz, obj, name, value):
    from java import jclass
    try:
        field = _field(clazz, name)
        if field is None:
            return False
        jclass("org.telegram.messenger.plugins.PluginContext").setReflectedField(field, obj, value)
        return True
    except Exception:
        return False


def get_private_field(obj, field_name):
    return _get(obj.getClass(), obj, field_name)


def set_private_field(obj, field_name, new_value):
    return _set(obj.getClass(), obj, field_name, new_value)


def get_static_private_field(clazz, field_name):
    return _get(clazz, None, field_name)


def set_static_private_field(clazz, field_name, new_value):
    return _set(clazz, None, field_name, new_value)
