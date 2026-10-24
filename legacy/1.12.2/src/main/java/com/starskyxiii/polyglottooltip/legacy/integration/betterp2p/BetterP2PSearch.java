package com.starskyxiii.polyglottooltip.legacy.integration.betterp2p;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.KeyedNames;

import appeng.core.localization.GuiText;
import appeng.items.parts.ItemPart;
import appeng.items.parts.PartType;
import net.minecraft.item.ItemStack;

public final class BetterP2PSearch {
    static final String SEPARATOR = " - ";

    private static volatile MethodHandle extraName;

    private BetterP2PSearch() {}

    public static String searchText(ItemStack stack, String primary) {
        IntegrationGuard guard = BetterP2PIntegration.active;
        if (guard == null || primary == null || stack == null) {
            return primary;
        }
        return guard.call(() -> text(primary, names(stack, primary), Config.settings().chineseScriptMatching()), primary);
    }

    private static List<String> names(ItemStack stack, String primary) {
        String key = typeKey(stack);
        if (key != null) {
            List<String> names = KeyedNames.of(primary, Collections.singletonList(key)).names();
            if (!names.isEmpty()) {
                return names;
            }
        }
        return types(ItemNames.of(stack).names());
    }

    private static String typeKey(ItemStack stack) {
        if (!(stack.func_77973_b() instanceof ItemPart)) {
            return null;
        }
        PartType type = ((ItemPart) stack.func_77973_b()).getTypeByStack(stack);
        if (type == null) {
            return null;
        }
        try {
            GuiText text = (GuiText) extraName().invoke(type);
            return text == null ? null : text.getUnlocalized();
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException(error);
        }
    }

    private static MethodHandle extraName() throws ReflectiveOperationException {
        MethodHandle handle = extraName;
        if (handle == null) {
            Method method = PartType.class.getDeclaredMethod("getExtraName");
            method.setAccessible(true);
            handle = MethodHandles.lookup().unreflect(method);
            extraName = handle;
        }
        return handle;
    }

    static String text(String primary, List<String> types, boolean chineseScriptMatching) {
        return types.isEmpty() ? primary : StorageSearchText.compose(primary, types, chineseScriptMatching);
    }

    static List<String> types(List<String> names) {
        List<String> types = new ArrayList<>();
        for (String name : names) {
            String[] parts = name.split(SEPARATOR, -1);
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                types.add(parts[1].trim());
            }
        }
        return types;
    }
}
