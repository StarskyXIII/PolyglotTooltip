package com.starskyxiii.polyglottooltip.testbridge;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import net.minecraft.launchwrapper.Launch;

final class NameAuditFinalObserver {
    private static Method getter;
    private static MethodHandle function;
    private static Object holder, hook;
    private static String state = "unavailable";

    private NameAuditFinalObserver() {}

    static boolean present() {
        if (Launch.classLoader == null) return false;
        for (Class<?> type = Launch.classLoader.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("top.outlands.foundation.boot.ActualClassLoader")) return true;
        }
        return false;
    }

    static synchronized void install() {
        if (!Boolean.getBoolean("clientdevbridge.enabled") || !Boolean.getBoolean("clientdevbridge.nameAudit")
                || !present() || !state.equals("unavailable")) return;
        if (Launch.classLoader.getResource("polyglottooltip.names.properties") != null) { state = "product"; return; }
        try {
            state = "class-shape";
            ClassLoader loader = Launch.classLoader.getClass().getClassLoader();
            Class<?> actual = checked(loader, "top.outlands.foundation.boot.ActualClassLoader", "9dbdf99c368feb399c9d78eef96098d400be228d183ca31dc05f82d0e54318f9");
            Class<?> holderType = checked(loader, "top.outlands.foundation.boot.TransformerHolder", "3b092876619d1d183db3a1098f3925372f8c6f127ceb04f6a4194bbda6349814");
            Class<?> contract = checked(loader, "top.outlands.foundation.function.ExplicitTransformerFunction", "2139b5531163176ad0bf15fe28359bfaa858cc0c3c436d9ecbdc576d8b7879b3");
            Class<?> launchType = checked(loader, "net.minecraft.launchwrapper.LaunchClassLoader", "f2e44bd1a624708a421d977d2aeaf51b4125edd149e43fa1af37c64481696eaa");
            if (Launch.classLoader.getClass() != launchType || launchType.getSuperclass() != actual) return;
            getter = actual.getMethod("getTransformerHolder");
            if (!Modifier.isStatic(getter.getModifiers()) || getter.getReturnType() != holderType) return;
            holder = getter.invoke(null);
            if (holder == null || holder.getClass() != holderType) return;
            function = MethodHandles.publicLookup().findGetter(holderType, "runExplicitTransformersFunction", contract);
            MethodHandle setter = MethodHandles.publicLookup().findSetter(holderType, "runExplicitTransformersFunction", contract);
            Method apply = contract.getMethod("apply", String.class, byte[].class);
            if (!contract.isInterface() || contract.getMethods().length != 1 || apply.getReturnType() != byte[].class) return;
            Object original = function.invoke(holder);
            if (!contract.isInstance(original)) return;
            hook = Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) {
                    if (method.getName().equals("equals")) return proxy == arguments[0];
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("toString")) return NameAuditFinalObserver.class.getName();
                }
                byte[] output;
                try { output = (byte[]) method.invoke(original, arguments); }
                catch (InvocationTargetException error) { throw error.getCause(); }
                String name = (String) arguments[0];
                if (output != null && current() && (name.equals("net.minecraft.item.ItemStack")
                        || name.equals("net.minecraft.item.ItemEnchantedBook")
                        || name.equals("austeretony.enchcontrol.common.core.ECHooks"))) {
                    output = NameAuditTransformer.observeEnchantments(output);
                }
                return output;
            });
            setter.invoke(holder, hook);
            state = "installed";
        } catch (Throwable error) { state += ":" + error.getClass().getName(); hook = null; }
    }

    private static boolean current() {
        try { return hook != null && getter.invoke(null) == holder && function.invoke(holder) == hook; }
        catch (Throwable error) { return false; }
    }

    static String state() { return state.equals("installed") && !current() ? "replaced" : state; }

    private static Class<?> checked(ClassLoader loader, String name, String expected) throws Exception {
        Class<?> type = Class.forName(name, false, loader);
        if (type.getClassLoader() != loader) throw new ClassNotFoundException(name);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = type.getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
            if (input == null) throw new ClassNotFoundException(name);
            byte[] buffer = new byte[8192];
            for (int size; (size = input.read(buffer)) >= 0;) digest.update(buffer, 0, size);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!expected.equals(hash.toString())) throw new ClassNotFoundException(name);
        return type;
    }
}
