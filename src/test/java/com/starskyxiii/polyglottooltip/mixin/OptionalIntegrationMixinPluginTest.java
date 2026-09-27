package com.starskyxiii.polyglottooltip.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalIntegrationMixinPluginTest {
    private static final String TARGET = "me.towdium.jecharacters.utils.FakeTree";
    private static final String MIXIN = "com.starskyxiii.polyglottooltip.mixin.jec.JecFakeTreeMixin";

    @Test
    void keepsLegacyParentsAcrossJeiPackageChanges() {
        for (String parent : Arrays.asList(
                "mezz.jei.search.suffixtree.GeneralizedSuffixTree",
                "mezz.jei.core.search.suffixtree.GeneralizedSuffixTree")) {
            Map<String, ClassNode> metadata = new HashMap<>();
            metadata.put(TARGET, node(parent));
            metadata.put(parent, node("java.lang.Object"));
            List<String> reads = new ArrayList<>();
            OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name -> {
                reads.add(name);
                return metadata.get(name);
            });

            assertTrue(plugin.shouldApplyMixin(TARGET, MIXIN));
            assertEquals(Arrays.asList(TARGET, parent), reads);
        }
    }

    @Test
    void keepsAnAvailableReplacementParent() {
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name -> {
            if (TARGET.equals(name)) {
                return node("example.search.SearchStorage");
            }
            assertEquals("example.search.SearchStorage", name);
            return node("java.lang.Object");
        });

        assertTrue(plugin.shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void skipsAnExistingTargetWhoseParentWasRemoved() {
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name -> {
            if (TARGET.equals(name)) {
                return node("mezz.jei.core.search.suffixtree.GeneralizedSuffixTree");
            }
            throw new ClassNotFoundException(name);
        });

        assertFalse(plugin.shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void skipsAnAbsentTarget() {
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name -> {
            throw new ClassNotFoundException(name);
        });

        assertFalse(plugin.shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void skipsNullMetadataAndParentlessTargets() {
        assertFalse(new OptionalIntegrationMixinPlugin(name -> null).shouldApplyMixin(TARGET, MIXIN));
        assertFalse(new OptionalIntegrationMixinPlugin(name -> node(null)).shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void skipsUnreadableParentMetadata() {
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name -> {
            if (TARGET.equals(name)) {
                return node("mezz.jei.core.search.suffixtree.GeneralizedSuffixTree");
            }
            throw new IOException("unreadable class metadata");
        });

        assertFalse(plugin.shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void leavesOtherMixinsUntouchedWithoutReadingClasses() {
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name -> {
            throw new AssertionError("Unexpected metadata read: " + name);
        });

        assertTrue(plugin.shouldApplyMixin("mezz.jei.gui.search.ElementSearch",
                "com.starskyxiii.polyglottooltip.mixin.jei.JeiElementSearchLowMemMixin"));
    }

    @Test
    void selectsGtoFallbacksOnlyWhenTheForkResourceExists() {
        for (String mixin : Arrays.asList(
                "com.starskyxiii.polyglottooltip.mixin.ae2.GtoAe2RepoSearchMixin",
                "com.starskyxiii.polyglottooltip.mixin.ldlib.GtoModularUiFluidTooltipMixin")) {
            OptionalIntegrationMixinPlugin present = new OptionalIntegrationMixinPlugin(name -> {
                assertEquals("com.gtocore.integration.ae.MultiLangNameSearchPredicate", name);
                return node("java.lang.Object");
            });
            OptionalIntegrationMixinPlugin absent = new OptionalIntegrationMixinPlugin(name -> {
                throw new ClassNotFoundException(name);
            });

            assertTrue(present.shouldApplyMixin("optional.Target", mixin));
            assertFalse(absent.shouldApplyMixin("optional.Target", mixin));
            assertFalse(new OptionalIntegrationMixinPlugin(name -> null).shouldApplyMixin("optional.Target", mixin));
        }
    }

    @Test
    void skipsGtoFallbacksWhenTheForkResourceIsUnreadable() {
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name -> {
            throw new IOException("unreadable fork resource");
        });

        assertFalse(plugin.shouldApplyMixin("optional.Target",
                "com.starskyxiii.polyglottooltip.mixin.ae2.GtoAe2RepoSearchMixin"));
        assertFalse(plugin.shouldApplyMixin("optional.Target",
                "com.starskyxiii.polyglottooltip.mixin.ldlib.GtoModularUiFluidTooltipMixin"));
    }

    @Test
    void selectsGtoEmiRefreshOnlyWithTheHandlerAndSelectorLibrary() {
        String mixin = "com.starskyxiii.polyglottooltip.mixin.emi.GtoEmiSearchMixin";
        ClassNode gto = node("java.lang.Object");
        gto.methods.add(new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bake",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null));
        Map<String, ClassNode> metadata = new HashMap<>();
        metadata.put("com.gtolib.mixin.emi.screen.EmiSearchMixin", gto);
        metadata.put("com.bawnorton.mixinsquared.TargetHandler", node("java.lang.Object"));
        metadata.put("com.bawnorton.mixinsquared.selector.DynamicSelectorHandler", node("java.lang.Object"));
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(metadata::get);
        assertTrue(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));

        metadata.remove("com.bawnorton.mixinsquared.selector.DynamicSelectorHandler");
        assertFalse(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
        metadata.put("com.bawnorton.mixinsquared.selector.DynamicSelectorHandler", node("java.lang.Object"));
        metadata.remove("com.bawnorton.mixinsquared.TargetHandler");
        assertFalse(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
        metadata.clear();
        assertFalse(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
    }

    @Test
    void skipsChangedGtoEmiHandlerContracts() {
        String mixin = "com.starskyxiii.polyglottooltip.mixin.emi.GtoEmiSearchMixin";
        ClassNode gto = node("java.lang.Object");
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name ->
                name.equals("com.gtolib.mixin.emi.screen.EmiSearchMixin") ? gto : node("java.lang.Object"));
        assertFalse(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
        gto.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "bake",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null));
        assertFalse(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
        gto.methods.clear();
        gto.methods.add(new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bake", "()V", null, null));
        assertFalse(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
        gto.methods.add(new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bake",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null));
        assertFalse(plugin.shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
    }

    @Test
    void skipsUnreadableGtoEmiResources() {
        String mixin = "com.starskyxiii.polyglottooltip.mixin.emi.GtoEmiSearchMixin";
        assertFalse(new OptionalIntegrationMixinPlugin(name -> { throw new IOException("unreadable"); })
                .shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
        assertFalse(new OptionalIntegrationMixinPlugin(name -> { throw new ClassNotFoundException(name); })
                .shouldApplyMixin("dev.emi.emi.search.EmiSearch", mixin));
    }

    @Test
    void readsRawParentMetadataAndClosesTheResource() throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/Child", null, "example/Parent", null);
        writer.visitEnd();
        AtomicBoolean closed = new AtomicBoolean();
        InputStream stream = new ByteArrayInputStream(writer.toByteArray()) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };

        ClassNode metadata = OptionalIntegrationMixinPlugin.readClassMetadata("example.Child", stream);

        assertEquals("example/Child", metadata.name);
        assertEquals("example/Parent", metadata.superName);
        assertTrue(closed.get());
    }

    @Test
    void reportsAnAbsentClassResource() {
        assertThrows(ClassNotFoundException.class,
                () -> OptionalIntegrationMixinPlugin.readClassMetadata(TARGET, null));
    }

    @Test
    void closesAnUnreadableClassResource() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream stream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("unreadable class resource");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        assertThrows(IOException.class,
                () -> OptionalIntegrationMixinPlugin.readClassMetadata(TARGET, stream));
        assertTrue(closed.get());
    }

    private static ClassNode node(String parent) {
        ClassNode node = new ClassNode();
        node.superName = parent == null ? null : parent.replace('.', '/');
        return node;
    }
}
