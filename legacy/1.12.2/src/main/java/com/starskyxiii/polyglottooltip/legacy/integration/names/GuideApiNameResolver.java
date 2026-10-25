package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import amerifrance.guideapi.api.impl.Book;
import amerifrance.guideapi.item.ItemGuideBook;
import net.minecraft.item.ItemStack;

public final class GuideApiNameResolver implements SpecialItemNameResolver {
    static final String ITEM_CLASS = "amerifrance.guideapi.item.ItemGuideBook";

    private final IntegrationGuard guard;

    public GuideApiNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active() && ITEM_CLASS.equals(stack.func_77973_b().getClass().getName());
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        return guard.call(() -> {
            Book book = ((ItemGuideBook) stack.func_77973_b()).getBook(stack);
            return name(book == null ? null : book.getItemName(), result.primary(), NameParts.PRIMARY, dictionary::get);
        }, null);
    }

    static String name(String key, String primary, Function<String, String> primaryLang,
                       Function<String, String> secondaryLang) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return NameParts.checked(primaryLang.apply(key), primary, () -> secondaryLang.apply(key));
    }
}
