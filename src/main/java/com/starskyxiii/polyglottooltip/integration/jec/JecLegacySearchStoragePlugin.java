package com.starskyxiii.polyglottooltip.integration.jec;
//~ minecraft_identifier minecraft_116_packages

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedSearchRegistration;
import mezz.jei.api.search.ISearchStorage;
import mezz.jei.api.search.ISearchStorageFactory;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;

@JeiPlugin
public final class JecLegacySearchStoragePlugin implements IModPlugin {
    private static final String FAKE_TREE = "me.towdium.jecharacters.utils.FakeTree";
    private static final String CURRENT_JEC_PLUGIN = "me.towdium.jecharacters.JechJeiPlugin";

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "jec_legacy_search");
    }

    @Override
    public void registerAdvancedSearch(IAdvancedSearchRegistration registration) {
        if (isClassPresent(CURRENT_JEC_PLUGIN)) {
            return;
        }
        Constructor<?> fakeTreeConstructor = findLegacyFakeTreeConstructor();
        if (fakeTreeConstructor == null) {
            return;
        }

        registration.replaceSearchStorage(new ISearchStorageFactory() {
            @Override
            public <T> ISearchStorage<T> createSearchStorage() {
                return instantiateFakeTree(fakeTreeConstructor);
            }
        });
        PolyglotTooltip.LOGGER.info("Enabled the legacy JEC search-storage compatibility bridge");
    }

    private static Constructor<?> findLegacyFakeTreeConstructor() {
        try {
            Constructor<?> constructor = Class.forName(
                    FAKE_TREE,
                    false,
                    JecLegacySearchStoragePlugin.class.getClassLoader()
            ).getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, JecLegacySearchStoragePlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ISearchStorage<T> instantiateFakeTree(Constructor<?> constructor) {
        try {
            return (ISearchStorage<T>) constructor.newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("Unable to create the legacy JEC FakeTree search storage", e);
        }
    }
}
