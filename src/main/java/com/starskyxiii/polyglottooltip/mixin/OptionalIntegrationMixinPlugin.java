package com.starskyxiii.polyglottooltip.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

public class OptionalIntegrationMixinPlugin implements IMixinConfigPlugin {
    private static final String JEC_FAKE_TREE_MIXIN =
            "com.starskyxiii.polyglottooltip.mixin.jec.JecFakeTreeMixin";
    private static final String GTO_PREDICATE = "com.gtocore.integration.ae.MultiLangNameSearchPredicate";
    private final ClassMetadataReader metadataReader;

    public OptionalIntegrationMixinPlugin() {
        this(name -> readClassMetadata(name, MixinService.getService()
                .getResourceAsStream(name.replace('.', '/') + ".class")));
    }

    OptionalIntegrationMixinPlugin(ClassMetadataReader metadataReader) {
        this.metadataReader = metadataReader;
    }

    static ClassNode readClassMetadata(String name, InputStream classStream)
            throws ClassNotFoundException, IOException {
        try (InputStream stream = classStream) {
            if (stream == null) {
                throw new ClassNotFoundException(name);
            }
            ClassNode node = new ClassNode();
            int flags = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
            if (!name.equals(CarbonConfigMixinGuard.ARRAY) && !name.equals(CarbonConfigMixinGuard.VALUE)) {
                flags |= ClassReader.SKIP_CODE;
            }
            new ClassReader(stream).accept(node, flags);
            return node;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("com.starskyxiii.polyglottooltip.mixin.carbonconfig.")) {
            try {
                ClassNode array = metadataReader.read(CarbonConfigMixinGuard.ARRAY);
                if (mixinClassName.endsWith(".CarbonForgeArraySaveMixin")) {
                    return CarbonConfigMixinGuard.ARRAY.equals(targetClassName)
                            && CarbonConfigMixinGuard.needsSaveIndexFix(array);
                }
                if (mixinClassName.endsWith(".LegacyCarbonForgeArrayRemoveMixin")
                        || mixinClassName.endsWith(".LegacyCarbonForgeValueAccessor")) {
                    return ((mixinClassName.endsWith(".LegacyCarbonForgeArrayRemoveMixin")
                                && CarbonConfigMixinGuard.ARRAY.equals(targetClassName))
                            || (mixinClassName.endsWith(".LegacyCarbonForgeValueAccessor")
                                && CarbonConfigMixinGuard.VALUE.equals(targetClassName)))
                            && CarbonConfigMixinGuard.needsRemovalIndexFix(array,
                                    metadataReader.read(CarbonConfigMixinGuard.VALUE));
                }
                return false;
            } catch (ClassNotFoundException | IOException | IllegalArgumentException | IndexOutOfBoundsException ignored) {
                return false;
            }
        }
        if (mixinClassName.equals("com.starskyxiii.polyglottooltip.mixin.emi.GtoEmiSearchMixin")) {
            try {
                ClassNode gtoMixin = metadataReader.read("com.gtolib.mixin.emi.screen.EmiSearchMixin");
                return gtoMixin != null
                        && metadataReader.read("com.bawnorton.mixinsquared.TargetHandler") != null
                        && metadataReader.read("com.bawnorton.mixinsquared.selector.DynamicSelectorHandler") != null
                        && gtoMixin.methods.stream().filter(method -> method.name.equals("bake")).count() == 1
                        && gtoMixin.methods.stream().filter(method -> method.name.equals("bake")
                                && method.desc.equals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
                                && (method.access & Opcodes.ACC_STATIC) != 0).count() == 1;
            } catch (ClassNotFoundException | IOException ignored) {
                return false;
            }
        }
        if (mixinClassName.equals("com.starskyxiii.polyglottooltip.mixin.ae2.GtoAe2RepoSearchMixin")
                || mixinClassName.equals("com.starskyxiii.polyglottooltip.mixin.ldlib.GtoModularUiFluidTooltipMixin")) {
            try {
                return metadataReader.read(GTO_PREDICATE) != null;
            } catch (ClassNotFoundException | IOException ignored) {
                return false;
            }
        }
        if (!JEC_FAKE_TREE_MIXIN.equals(mixinClassName)) {
            return true;
        }
        try {
            ClassNode target = metadataReader.read(targetClassName);
            return target != null && target.superName != null
                    && metadataReader.read(target.superName.replace('/', '.')) != null;
        } catch (ClassNotFoundException | IOException ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }

    @FunctionalInterface
    interface ClassMetadataReader {
        ClassNode read(String name) throws ClassNotFoundException, IOException;
    }
}
