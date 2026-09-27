package com.starskyxiii.polyglottooltip.integration.emi;
//~ minecraft_116_packages minecraft_legacy_components minecraft_118_factories

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;

//? fabric {
/*import dev.emi.emi.runtime.EmiTagKey;
*///?} else {
import java.lang.reflect.Method;
//?}

public final class EmiTagNameHelper {

    //? !fabric {
    private static volatile Method tagKeyOfMethod;
    private static volatile Method tagKeyGetNameMethod;
    private static volatile Method getTagNameMethod;
    //?}

    private EmiTagNameHelper() {
    }

    public static Component getTagName(TagKey<?> key) {
        //? fabric {
        /*return EmiTagKey.of(key).getTagName();
        *///?} else {
        try {
            Method method = tagKeyGetNameMethod;
            if (method == null) {
                synchronized (EmiTagNameHelper.class) {
                    method = tagKeyGetNameMethod;
                    if (method == null) {
                        Class<?> tagKeyClass = Class.forName("dev.emi.emi.runtime.EmiTagKey");
                        Method factory = tagKeyClass.getMethod("of", TagKey.class);
                        method = tagKeyClass.getMethod("getTagName");
                        tagKeyOfMethod = factory;
                        tagKeyGetNameMethod = method;
                    }
                }
            }
            Object result = method.invoke(tagKeyOfMethod.invoke(null, key));
            if (result instanceof Component component) {
                return component;
            }
        } catch (ReflectiveOperationException ignored) {
        }
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
        return ComponentFactory.literal("#" + key.location());
        //?}
    }
}
