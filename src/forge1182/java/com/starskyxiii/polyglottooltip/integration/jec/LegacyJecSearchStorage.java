package com.starskyxiii.polyglottooltip.integration.jec;

import com.starskyxiii.polyglottooltip.search.ChineseScriptVariantIndexer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.function.Consumer;

public final class LegacyJecSearchStorage {
    private static final String LEGACY_SUFFIX_TREE = "mezz.jei.core.search.suffixtree.GeneralizedSuffixTree";
    private static final String NATIVE_MUTABLE_STORAGE = "mezz.jei.common.search.GeneralizedSuffixTreeSearchStorage";
    private static final String SEARCH_API = "mezz.jei.api.search.";
    private static final String JEC_PACKAGE = "me.towdium.jecharacters.";
    private static final ClassLoader LOADER = LegacyJecSearchStorage.class.getClassLoader();
    private static final TreeAccess JEC = findLegacyTree();

    private LegacyJecSearchStorage() {
    }

    public static <T> T replaceLegacyStorage(Class<T> storageType, T original) {
        if (JEC == null || !LEGACY_SUFFIX_TREE.equals(original.getClass().getName())) {
            return original;
        }
        try {
            checkStorageType(storageType);
            return storageType.cast(createJecStorage(storageType));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return original;
        }
    }

    public static boolean registerFallback(Object registration) {
        if (JEC == null) {
            return false;
        }
        try {
            Class<?> storage = load(SEARCH_API + "ISearchStorage");
            checkStorageType(storage);
            Class<?> factory = load(SEARCH_API + "ISearchStorageFactory");
            Constructor<?> fallback = load(NATIVE_MUTABLE_STORAGE).asSubclass(storage).getConstructor();
            Method replace = load("mezz.jei.api.registration.IAdvancedSearchRegistration")
                    .getMethod("replaceSearchStorage", factory);
            Object adapter = proxy(factory, (instance, method, args) ->
                    method.getDeclaringClass() == Object.class
                            ? handleObject(instance, method, args)
                            : createStorageOrFallback(storage, fallback));
            replace.invoke(registration, adapter);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static Object createStorageOrFallback(Class<?> storage, Constructor<?> fallback)
            throws ReflectiveOperationException {
        try {
            return createJecStorage(storage);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return fallback.newInstance();
        }
    }

    private static void checkStorageType(Class<?> storage) throws NoSuchMethodException {
        storage.getMethod("put", String.class, Object.class);
        storage.getMethod("getSearchResults", String.class, Consumer.class);
        storage.getMethod("getAllElements", Consumer.class);
        storage.getMethod("statistics");
    }

    private static Object createJecStorage(Class<?> storage) throws ReflectiveOperationException {
        Object tree = JEC.create().invoke(null);
        if (tree == null) {
            throw new IllegalStateException("JEC did not create a search tree");
        }
        return proxy(storage, (instance, method, args) -> switch (method.getName()) {
            case "put" -> {
                String key = (String) args[0];
                Object value = args[1];
                put(tree, key, value);
                ChineseScriptVariantIndexer.putVariants(key, value, (variant, entry) -> put(tree, variant, entry));
                yield null;
            }
            case "getSearchResults" -> {
                search(tree, (String) args[0], args[1]);
                yield null;
            }
            case "getAllElements" -> {
                search(tree, "", args[0]);
                yield null;
            }
            case "statistics" -> "JEC PinIn search storage";
            default -> handleObject(instance, method, args);
        });
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object handleObject(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "JEC PinIn search adapter";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }

    private static void put(Object tree, String key, Object value) {
        try {
            JEC.put().invoke(tree, key, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to index a JEI search term in JEC", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void search(Object tree, String query, Object output) throws ReflectiveOperationException {
        Consumer<Collection<?>> consumer = (Consumer<Collection<?>>) output;
        consumer.accept((Collection<?>) JEC.search().invoke(tree, query));
    }

    private static TreeAccess findLegacyTree() {
        if (LOADER.getResource("me/towdium/jecharacters/utils/FakeTree.class") == null
                || LOADER.getResource("me/towdium/jecharacters/JechJeiPlugin.class") != null) {
            return null;
        }
        try {
            Method create = load(JEC_PACKAGE + "utils.Match").getMethod("searcher");
            Class<?> tree = create.getReturnType();
            return new TreeAccess(create, tree.getMethod("put", String.class, Object.class),
                    tree.getMethod("search", String.class));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, LOADER);
    }

    private record TreeAccess(Method create, Method put, Method search) {
    }
}
