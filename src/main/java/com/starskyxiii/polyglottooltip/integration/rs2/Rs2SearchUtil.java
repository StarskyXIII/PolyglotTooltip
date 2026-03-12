package com.starskyxiii.polyglottooltip.integration.rs2;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryFilter;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.api.grid.GridResourceAttributeKeys;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import com.starskyxiii.polyglottooltip.search.WrappedSearchMatcher;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Rs2SearchUtil {

    private Rs2SearchUtil() {
    }

    // Cache for autocrafter group names (user-defined strings, not language-dependent).
    // The SearchTextCollector result for a given name is deterministic and never changes.
    private static final Map<String, String> nameSearchTextCache = new HashMap<>();

    public static ResourceRepositoryFilter<GridResource> wrapLiteralFilter(String query,
                                                                          ResourceRepositoryFilter<GridResource> delegate) {
        return (repository, resource) -> WrappedSearchMatcher.matches(
                query,
                resource.getName(),
                () -> delegate.test(repository, resource),
                () -> resolveSecondaryNames(resource)
        );
    }

    public static ResourceRepositoryFilter<GridResource> wrapTooltipFilter(String query,
                                                                          ResourceRepositoryFilter<GridResource> delegate) {
        return (repository, resource) -> delegate.test(repository, resource)
                || ChineseScriptSearchMatcher.containsMatch(
                query,
                resource.getAttribute(GridResourceAttributeKeys.TOOLTIP)
        );
    }

    public static boolean matchesAutocrafterName(String normalizedQuery, String name) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }

        String searchText = nameSearchTextCache.computeIfAbsent(
                name, n -> new SearchTextCollector().addText(n).build());
        return searchText.contains(normalizedQuery);
    }

    public static boolean matchesAutocrafterResource(String normalizedQuery, ResourceKey key) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        if (key == null) {
            return false;
        }

        Component displayName = RefinedStorageClientApi.INSTANCE.getResourceRendering(key.getClass()).getDisplayName(key);
        return new SearchTextCollector()
                .addComponent(displayName)
                .build()
                .contains(normalizedQuery);
    }

    private static List<String> resolveSecondaryNames(GridResource resource) {
        Object platformResource = resource.getResourceForRecipeMods();
        if (platformResource instanceof ItemResource itemResource) {
            return LanguageCache.getInstance().resolveDisplayNamesForAll(itemResource.toItemStack());
        }
        if (platformResource instanceof FluidResource fluidResource) {
            // Use Platform.getFluidRenderer() to match the tooltip mixin paths
            // (Rs2FluidGridResourceMixin, Rs2FluidResourceRenderingMixin).
            Component displayName = Platform.INSTANCE.getFluidRenderer().getDisplayName(fluidResource);
            return LanguageCache.getInstance().resolveComponentsForAll(displayName);
        }
        return resolveChemicalSecondaryNames(platformResource);
    }

    public static Optional<Component> getRs2MekanismChemicalDisplayName(Object chemicalResource) {
        if (chemicalResource == null) {
            return Optional.empty();
        }
        if (!"com.refinedmods.refinedstorage.mekanism.ChemicalResource".equals(chemicalResource.getClass().getName())) {
            return Optional.empty();
        }

        try {
            Method chemicalMethod = chemicalResource.getClass().getMethod("chemical");
            Object chemical = chemicalMethod.invoke(chemicalResource);
            if (chemical == null) {
                return Optional.empty();
            }

            Method textComponentMethod = chemical.getClass().getMethod("getTextComponent");
            Object component = textComponentMethod.invoke(chemical);
            return component instanceof Component c ? Optional.of(c) : Optional.empty();
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    private static List<String> resolveChemicalSecondaryNames(Object platformResource) {
        return getRs2MekanismChemicalDisplayName(platformResource)
                .map(component -> LanguageCache.getInstance().resolveComponentsForAll(component))
                .orElse(List.of());
    }
}
