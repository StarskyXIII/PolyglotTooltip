package com.starskyxiii.polyglottooltip.mixin.rs2;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryFilter;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.query.parser.node.LiteralNode;
import com.refinedmods.refinedstorage.query.parser.node.UnaryOpNode;
import com.starskyxiii.polyglottooltip.integration.rs2.Rs2SearchUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.query.GridQueryParser", remap = false)
public class Rs2GridQueryParserMixin {

    @ModifyReturnValue(method = "parseLiteral", at = @At("RETURN"))
    private static ResourceRepositoryFilter<GridResource> wrapLiteralFilter(ResourceRepositoryFilter<GridResource> original,
                                                                           LiteralNode node) {
        return Rs2SearchUtil.wrapLiteralFilter(node.token().content(), original);
    }

    @ModifyReturnValue(method = "parseUnaryOp", at = @At("RETURN"))
    private ResourceRepositoryFilter<GridResource> wrapTooltipFilter(ResourceRepositoryFilter<GridResource> original,
                                                                     UnaryOpNode node) {
        if (!"$".equals(node.operator().content()) || !(node.node() instanceof LiteralNode literal)) {
            return original;
        }
        return Rs2SearchUtil.wrapTooltipFilter(literal.token().content(), original);
    }
}
