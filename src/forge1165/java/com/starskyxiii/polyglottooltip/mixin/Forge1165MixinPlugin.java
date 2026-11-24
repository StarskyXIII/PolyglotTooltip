package com.starskyxiii.polyglottooltip.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;

public final class Forge1165MixinPlugin extends LegacyMixinExtrasBootstrapPlugin {
    private static final String PACKAGE = "com.starskyxiii.polyglottooltip.mixin.";
    private static final String CONTROLLING_MIXIN = PACKAGE + "controlling.LegacyControlling7ScreenMixin";
    private static final String CONTROLLING_PRE29_MIXIN = PACKAGE + "controlling.LegacyControlling7Pre29ScreenMixin";
    private static final String JEI_MIXIN = PACKAGE + "jei.JeiGeneralizedSuffixTreeMixin";
    private static final String JEI_INTEGER_MIXIN = PACKAGE + "jei.LegacyJeiIntegerSuffixTreeMixin";
    private static final String FILTER_DESCRIPTOR = "(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            if (CONTROLLING_MIXIN.equals(mixinClassName) || CONTROLLING_PRE29_MIXIN.equals(mixinClassName)) {
                ClassNode target = readTarget(targetClassName);
                boolean pre29 = hasFilterMethods(target, 9);
                if (pre29 == hasFilterMethods(target, 7)) {
                    return false;
                }
                return pre29 ? CONTROLLING_PRE29_MIXIN.equals(mixinClassName) : CONTROLLING_MIXIN.equals(mixinClassName);
            }
            if (JEI_MIXIN.equals(mixinClassName) || JEI_INTEGER_MIXIN.equals(mixinClassName)) {
                ClassNode target = readTarget(targetClassName);
                if (hasMethod(target, "put", "(Ljava/lang/String;Ljava/lang/Object;)V")) {
                    return JEI_MIXIN.equals(mixinClassName);
                }
                return JEI_INTEGER_MIXIN.equals(mixinClassName) && hasMethod(target, "put", "(Ljava/lang/String;I)V");
            }
        } catch (ClassNotFoundException | IOException ignored) {
            return false;
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }

    private static ClassNode readTarget(String name) throws ClassNotFoundException, IOException {
        return readClassMetadata(name, MixinService.getService()
                .getResourceAsStream(name.replace('.', '/') + ".class"));
    }

    private static boolean hasFilterMethods(ClassNode target, int first) {
        return hasMethod(target, "lambda$filterKeys$" + first, FILTER_DESCRIPTOR)
                && hasMethod(target, "lambda$filterKeys$" + (first + 1), FILTER_DESCRIPTOR)
                && hasMethod(target, "lambda$filterKeys$" + (first + 2), FILTER_DESCRIPTOR);
    }

    private static boolean hasMethod(ClassNode target, String name, String descriptor) {
        return target.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }
}
