package com.starskyxiii.polyglottooltip.mixin.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.integration.jec.LegacyJecSearchStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginLoader", remap = false)
public abstract class LegacyJei118SearchRegistrationMixin {
    @WrapOperation(
            method = "createSearchStorageFactory(Ljava/util/List;)Lmezz/jei/api/search/ISearchStorageBuilderFactory;",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/library/load/registration/AdvancedSearchRegistration;getSearchStorageBuilderFactoryOverride()Ljava/util/Optional;"
            ),
            require = 0,
            remap = false
    )
    private static Optional<?> polyglot$registerLegacyJec(@Coerce Object registration, Operation<Optional<?>> original) {
        Optional<?> override = original.call(registration);
        if (override.isEmpty() && LegacyJecSearchStorage.registerFallback(registration)) {
            return original.call(registration);
        }
        return override;
    }
}
