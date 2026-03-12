package com.starskyxiii.polyglottooltip.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared reflection utilities for optional mod integrations.
 *
 * <p>Caches resolved {@link Field} and {@link Method} objects by
 * {@code "className#memberName"} so that the cost of reflection lookup
 * is paid only once per JVM session.  Item / block instances are
 * registered singletons, so their structure never changes after load.
 */
public final class ReflectionHelper {

    private static final Map<String, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    private ReflectionHelper() {
    }

    /**
     * Reads an instance field from {@code target}, walking up the class hierarchy.
     * Returns empty if the field is absent, inaccessible, or its value is {@code null}.
     */
    public static Optional<Object> readField(Object target, String fieldName) {
        String cacheKey = target.getClass().getName() + "#" + fieldName;
        Optional<Field> field = FIELD_CACHE.computeIfAbsent(cacheKey,
                k -> findField(target.getClass(), fieldName));
        return field.flatMap(f -> {
            try {
                return Optional.ofNullable(f.get(target));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        });
    }

    /**
     * Typed variant of {@link #readField(Object, String)}.
     * Returns empty if the field value is not an instance of {@code fieldType}.
     */
    public static <T> Optional<T> readField(Object target, String fieldName, Class<T> fieldType) {
        return readField(target, fieldName)
                .filter(fieldType::isInstance)
                .map(fieldType::cast);
    }

    /**
     * Invokes a no-arg instance method on {@code target}, checking the public API
     * first and falling back to declared (non-public) methods by walking the class
     * hierarchy.  Returns empty if the method is absent, inaccessible, or returns
     * {@code null}.
     */
    public static Optional<Object> invokeMethod(Object target, String methodName) {
        String cacheKey = target.getClass().getName() + "#" + methodName;
        Optional<Method> method = METHOD_CACHE.computeIfAbsent(cacheKey,
                k -> findMethod(target.getClass(), methodName));
        return method.flatMap(m -> {
            try {
                return Optional.ofNullable(m.invoke(target));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        });
    }

    /** Walks the class hierarchy (including superclasses) to locate a declared field. */
    public static Optional<Field> findField(Class<?> startClass, String fieldName) {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Locates a no-arg method, checking public API first then declared
     * (non-public) methods by walking the class hierarchy.
     */
    public static Optional<Method> findMethod(Class<?> startClass, String methodName) {
        try {
            Method method = startClass.getMethod(methodName);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (NoSuchMethodException ignored) {
            // Fall through to declared-method lookup for non-public members.
        } catch (SecurityException ignored) {
            return Optional.empty();
        }

        Class<?> current = startClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
