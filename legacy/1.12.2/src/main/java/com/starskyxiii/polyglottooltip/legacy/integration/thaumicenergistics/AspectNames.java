package com.starskyxiii.polyglottooltip.legacy.integration.thaumicenergistics;

import java.util.Collections;

import com.starskyxiii.polyglottooltip.legacy.minecraft.KeyedNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

import thaumcraft.api.aspects.Aspect;

final class AspectNames {
    static final String KEY_PREFIX = "tc.aspect.";

    private AspectNames() {}

    static SecondaryNames of(Aspect aspect) {
        if (aspect == null || aspect.getTag() == null) {
            return SecondaryNames.EMPTY;
        }
        return KeyedNames.of(aspect.getLocalizedDescription(), Collections.singletonList(KEY_PREFIX + aspect.getTag()));
    }
}
