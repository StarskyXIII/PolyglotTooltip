package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.function.Function;
import java.util.regex.Pattern;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class ReliquaryNameResolver implements SpecialItemNameResolver {
    static final String MODID = "xreliquary";
    private static final Pattern MACRO = Pattern.compile("\\{\\{![a-z_.]+}}", Pattern.CASE_INSENSITIVE);

    private final IntegrationGuard guard;

    public ReliquaryNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        ResourceLocation id = stack.func_77973_b().getRegistryName();
        return guard.active() && id != null && MODID.equals(id.func_110624_b());
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        return guard.call(() -> name(stack.func_77973_b().func_77667_c(stack), result.primary(), NameParts.PRIMARY,
                dictionary::get), null);
    }

    static String name(String unlocalized, String primary, Function<String, String> primaryLang,
                       Function<String, String> secondaryLang) {
        if (unlocalized == null) {
            return null;
        }
        String key = MODID + "." + unlocalized + ".name";
        return NameParts.checked(expand(primaryLang.apply(key)), primary, () -> expand(secondaryLang.apply(key)));
    }

    static String expand(String template) {
        return template == null ? null : MACRO.matcher(template).replaceAll("");
    }
}
