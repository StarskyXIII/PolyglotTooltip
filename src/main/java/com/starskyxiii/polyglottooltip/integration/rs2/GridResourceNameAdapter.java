package com.starskyxiii.polyglottooltip.integration.rs2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

final class GridResourceNameAdapter {
    private static final ClassValue<PrimaryNamesAccessor> ACCESSORS = new ClassValue<>() {
        @Override
        protected PrimaryNamesAccessor computeValue(Class<?> type) {
            Method searchableNames = findPublicMethod(type, "getSearchableNames", Iterable.class);
            if (searchableNames != null) {
                return resource -> castIterable(invoke(searchableNames, resource));
            }

            Method name = findPublicMethod(type, "getName", String.class);
            if (name != null) {
                return resource -> Collections.singletonList((String) invoke(name, resource));
            }
            return resource -> List.of();
        }
    };

    private GridResourceNameAdapter() {
    }

    static Iterable<String> getPrimaryNames(Object resource) {
        return resource == null ? List.of() : ACCESSORS.get(resource.getClass()).get(resource);
    }

    private static Method findPublicMethod(Class<?> type, String name, Class<?> returnType) {
        try {
            Method method = type.getMethod(name);
            if (!returnType.isAssignableFrom(method.getReturnType())) {
                return null;
            }
            method.trySetAccessible();
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Iterable<String> castIterable(Object value) {
        return value == null ? null : (Iterable<String>) value;
    }

    @FunctionalInterface
    private interface PrimaryNamesAccessor {
        Iterable<String> get(Object resource);
    }
}
