package com.starskyxiii.polyglottooltip.legacy.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;

import net.minecraftforge.fml.common.Loader;

public abstract class LoadedModMixinPlugin implements IMixinConfigPlugin {
    private final String modid;
    private final Set<String> missing = ConcurrentHashMap.newKeySet();

    protected LoadedModMixinPlugin(String modid) {
        this.modid = modid;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!Loader.isModLoaded(modid)) {
            return false;
        }
        if (getClass().getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") != null) {
            return true;
        }
        if (missing.add(targetClassName)) {
            LogManager.getLogger(PolyglotTooltipMod.MOD_ID).debug("Skipped {} because {} is not present", mixinClassName, targetClassName);
        }
        return false;
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
