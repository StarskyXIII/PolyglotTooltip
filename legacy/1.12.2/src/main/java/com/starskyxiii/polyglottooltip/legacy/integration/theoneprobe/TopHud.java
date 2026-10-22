package com.starskyxiii.polyglottooltip.legacy.integration.theoneprobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.hud.HudLines;
import com.starskyxiii.polyglottooltip.legacy.integration.hud.HudNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

import mcjty.theoneprobe.api.IElement;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.apiimpl.elements.ElementText;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public final class TopHud {
    private TopHud() {}

    public static void render(ProbeInfo info, RayTraceResult hit, Runnable nativeRender) {
        IntegrationGuard guard = TopIntegration.active;
        List<IElement> added = guard == null
                ? Collections.<IElement>emptyList()
                : guard.call(() -> insert(info, hit), Collections.<IElement>emptyList());
        try {
            nativeRender.run();
        } finally {
            if (!added.isEmpty()) {
                guard.run(() -> remove(info.getElements(), added));
            }
        }
    }

    private static List<IElement> insert(ProbeInfo info, RayTraceResult hit) {
        List<IElement> elements = info == null ? null : info.getElements();
        if (elements == null || elements.isEmpty() || hit == null) {
            return Collections.emptyList();
        }
        List<String> lines = HudLines.compose(names(hit).names(), Config.settings().hudPrefix(),
                Collections.<String>emptyList());
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<IElement> added = new ArrayList<>(lines.size());
        for (String line : lines) {
            added.add(new ElementText(line));
        }
        elements.addAll(1, added);
        return added;
    }

    private static SecondaryNames names(RayTraceResult hit) {
        if (hit.field_72313_a == RayTraceResult.Type.ENTITY) {
            return HudNames.entity(hit.field_72308_g);
        }
        Minecraft minecraft = Minecraft.func_71410_x();
        World world = minecraft.field_71441_e;
        if (hit.field_72313_a != RayTraceResult.Type.BLOCK || world == null) {
            return SecondaryNames.EMPTY;
        }
        BlockPos pos = hit.func_178782_a();
        IBlockState state = world.func_180495_p(pos);
        return HudNames.block(state.func_177230_c(),
                () -> state.func_177230_c().getPickBlock(state, hit, world, pos, minecraft.field_71439_g));
    }

    private static void remove(List<IElement> elements, List<IElement> added) {
        for (IElement element : added) {
            for (Iterator<IElement> iterator = elements.iterator(); iterator.hasNext(); ) {
                if (iterator.next() == element) {
                    iterator.remove();
                    break;
                }
            }
        }
    }
}
