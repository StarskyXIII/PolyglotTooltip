package com.starskyxiii.polyglottooltip.integration.emi;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;

import java.lang.reflect.Method;

public final class EmiTagNameHelper {

    private static volatile Method getTagNameMethod;

    private EmiTagNameHelper() {
    }

    public static Component getTagName(TagKey<?> key) {
        try {
            Method method = getTagNameMethod;
            if (method == null) {
                synchronized (EmiTagNameHelper.class) {
                    method = getTagNameMethod;
                    if (method == null) {
                        Class<?> emiTagsClass = Class.forName("dev.emi.emi.registry.EmiTags");
                        method = emiTagsClass.getMethod("getTagName", TagKey.class);
                        getTagNameMethod = method;
                    }
                }
            }
            Object result = method.invoke(null, key);
            if (result instanceof Component component) {
                return component;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return Component.literal("#" + key.location());
    }
}
