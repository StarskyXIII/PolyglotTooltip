package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolvers;

public final class NamesIntegration implements Integration {
    public static final String FORESTRY_ID = "names-forestry";
    public static final String FORESTRY_MODID = "forestry";
    public static final String IC2_ID = "names-ic2";
    public static final String IC2_MODID = "ic2";
    public static final String MICROBLOCK_ID = "names-microblock";
    public static final String MICROBLOCK_MODID = "microblockcbe";
    public static final String EXTRACELLS_ID = "names-extracells";
    public static final String EXTRACELLS_MODID = "extracells";
    public static final String POTION_ID = "names-potion";

    private final String id;
    private final String modid;
    private final List<SpecialItemNameResolver> resolvers;

    private NamesIntegration(String id, String modid, SpecialItemNameResolver... resolvers) {
        this.id = id;
        this.modid = modid;
        this.resolvers = Collections.unmodifiableList(Arrays.asList(resolvers));
    }

    public static NamesIntegration forestry(IntegrationGuard guard) {
        return new NamesIntegration(FORESTRY_ID, FORESTRY_MODID, new ForestryContainerNameResolver(guard),
                new ForestryWoodNameResolver(guard), new ForestryGeneticNameResolver(guard));
    }

    public static NamesIntegration ic2(IntegrationGuard guard) {
        return new NamesIntegration(IC2_ID, IC2_MODID, new Ic2NameResolver(guard));
    }

    public static NamesIntegration microblock(IntegrationGuard guard) {
        try {
            return new NamesIntegration(MICROBLOCK_ID, MICROBLOCK_MODID, new MicroblockNameResolver(guard));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ForgeMultipart microblock API not found", error);
        }
    }

    public static NamesIntegration extracells(IntegrationGuard guard) {
        return new NamesIntegration(EXTRACELLS_ID, EXTRACELLS_MODID, new ExtraCellsNameResolver(guard));
    }

    public static NamesIntegration potion(IntegrationGuard guard) {
        return new NamesIntegration(POTION_ID, null, new PotionNameResolver(guard));
    }

    List<SpecialItemNameResolver> resolvers() {
        return resolvers;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String modid() {
        return modid;
    }

    @Override
    public void start() {
        for (SpecialItemNameResolver resolver : resolvers) {
            SpecialItemNameResolvers.register(resolver);
        }
    }

    @Override
    public void invalidate() {
        if (IC2_ID.equals(id)) {
            Ic2NameResolver.invalidate();
        }
    }
}
