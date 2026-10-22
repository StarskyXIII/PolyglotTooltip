package com.starskyxiii.polyglottooltip.legacy.integration.generic;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolvers;

public final class GenericIntegration implements Integration {
    public static final String FORMAT_ID = "generic-format";
    public static final String COMPOSITE_ID = "generic-composite";

    private final String id;
    private final SpecialItemNameResolver resolver;

    private GenericIntegration(String id, SpecialItemNameResolver resolver) {
        this.id = id;
        this.resolver = resolver;
    }

    public static GenericIntegration format(IntegrationGuard guard) {
        return new GenericIntegration(FORMAT_ID, new FormatNameResolver(guard));
    }

    public static GenericIntegration composite(IntegrationGuard guard) {
        return new GenericIntegration(COMPOSITE_ID, new CompositeNameResolver(guard));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String modid() {
        return null;
    }

    @Override
    public void start() {
        SpecialItemNameResolvers.register(resolver);
    }

    @Override
    public void invalidate() {
        ReverseIndex.invalidate();
    }
}
