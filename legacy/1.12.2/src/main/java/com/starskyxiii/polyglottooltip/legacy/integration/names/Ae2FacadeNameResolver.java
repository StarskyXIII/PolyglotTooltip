package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import appeng.items.parts.ItemFacade;

import net.minecraft.item.ItemStack;

public final class Ae2FacadeNameResolver implements SpecialItemNameResolver {
    static final String SEPARATOR = " - ";

    private final IntegrationGuard guard;

    public Ae2FacadeNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active() && stack.func_77973_b() instanceof ItemFacade;
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        ResolvedName name = resolveName(stack, result, languageCode, dictionary);
        return name == null ? null : name.name();
    }

    @Override
    public ResolvedName resolveName(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                                    SecondaryDictionary dictionary) {
        return guard.call(() -> {
            ItemStack block = ((ItemFacade) stack.func_77973_b()).getTextureItem(stack);
            if (block == null || block.func_190926_b() || result.key() == null) {
                return null;
            }
            return name(result.expected(), dictionary.get(result.key()), block.func_82833_r(),
                    NameParts.itemName(block, languageCode, dictionary), result.primary(),
                    NameParts.reverse(dictionary));
        }, null);
    }

    static ResolvedName name(String base, String secondaryBase, String block, ResolvedName secondaryBlock,
                             String shown, Function<String, String> reverse) {
        return new NameParts.Joined(reverse).part(base, ResolvedName.of(secondaryBase)).text(SEPARATOR)
                .part(block, secondaryBlock).checked(shown);
    }
}
