package com.starskyxiii.polyglottooltip.legacy.mixin.jei;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.common.Loader;

public final class JeiMixinPlugin implements IMixinConfigPlugin {
    private static final String HEI_MARKER = "mezz/jei/search/ElementSearch.class";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!Loader.isModLoaded("jei")) {
            return false;
        }
        if (mixinClassName.endsWith(".JeiTooltipStringsMixin") || mixinClassName.endsWith(".JeiFluidTooltipMixin")) {
            return true;
        }
        boolean hei = JeiMixinPlugin.class.getClassLoader().getResource(HEI_MARKER) != null;
        return mixinClassName.endsWith(".JeiFilterMixin") != hei;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
