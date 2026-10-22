package com.starskyxiii.polyglottooltip.testbridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class NativeAccess {
    private NativeAccess() { }
    public static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }

    public static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    public static Object get(Object target, String name) throws Exception { return field(target, name).get(target instanceof Class ? null : target); }
    public static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target instanceof Class ? null : target, value); }

    public static Object create(String className, Object... arguments) throws Exception {
        Constructor<?> found = null;
        for (Constructor<?> constructor : type(className).getDeclaredConstructors()) {
            if (accepts(constructor.getParameterTypes(), arguments)) {
                if (found != null) throw new IllegalArgumentException("Ambiguous constructor " + className);
                found = constructor;
            }
        }
        if (found == null) throw new NoSuchMethodException(className + " constructor");
        found.setAccessible(true);
        return found.newInstance(arguments);
    }

    public static Object call(Object target, String name, Object... arguments) throws Exception {
        boolean isStatic = target instanceof Class;
        Class<?> owner = isStatic ? (Class<?>) target : target.getClass();
        Method publicMethod = null;
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.isBridge() || Modifier.isStatic(method.getModifiers()) != isStatic
                    || !accepts(method.getParameterTypes(), arguments)) continue;
            if (publicMethod != null) throw new IllegalArgumentException("Ambiguous public method " + name);
            publicMethod = method;
        }
        if (publicMethod != null) { publicMethod.setAccessible(true); return publicMethod.invoke(isStatic ? null : target, arguments); }
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            Method found = null;
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.isBridge() || Modifier.isStatic(method.getModifiers()) != isStatic
                        || !accepts(method.getParameterTypes(), arguments)) continue;
                if (found != null) throw new IllegalArgumentException("Ambiguous method " + name);
                found = method;
            }
            if (found != null) { found.setAccessible(true); return found.invoke(isStatic ? null : target, arguments); }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean accepts(Class<?>[] types, Object[] values) {
        if (types.length != values.length) return false;
        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            if (values[index] == null) { if (type.isPrimitive()) return false; else continue; }
            if (type == int.class) type = Integer.class;
            else if (type == long.class) type = Long.class;
            else if (type == boolean.class) type = Boolean.class;
            else if (type == float.class) type = Float.class;
            else if (type == double.class) type = Double.class;
            else if (type == short.class) type = Short.class;
            else if (type == byte.class) type = Byte.class;
            else if (type == char.class) type = Character.class;
            if (!type.isInstance(values[index])) return false;
        }
        return true;
    }
}
