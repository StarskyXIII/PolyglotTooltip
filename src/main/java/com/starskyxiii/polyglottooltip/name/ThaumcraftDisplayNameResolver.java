package com.starskyxiii.polyglottooltip.name;

import com.starskyxiii.polyglottooltip.i18n.LanguageCache;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Resolves display names for Thaumcraft wands (ItemWandCasting) by reading the
 * cap and rod tags directly from the item's NBT compound and assembling the name
 * using the same key pattern as ItemWandCasting.getItemStackDisplayName():
 *
 * <pre>
 *   template : "item.Wand.name"   → "%CAP %ROD %OBJ"
 *   cap key  : "item.Wand." + capTag  + ".cap"   e.g. "item.Wand.void.cap"
 *   rod key  : "item.Wand." + rodTag  + ".rod"   e.g. "item.Wand.alchemical.rod"
 *   obj key  : "item.Wand.wand.obj" / "item.Wand.staff.obj" / "item.Wand.sceptre.obj"
 * </pre>
 *
 * <p>Detection: a stack is treated as a Thaumcraft wand if its item class name
 * contains {@code "ItemWandCasting"} in the {@code thaumcraft.} package.
 *
 * <p>NBT structure (from bytecode analysis of Thaumcraft-1.7.10-4.2.3.5.jar):
 * <ul>
 *   <li>NBT key {@code "cap"} — string, cap tag (e.g. {@code "void"}, {@code "alchemical"})</li>
 *   <li>NBT key {@code "rod"} — string, rod tag (e.g. {@code "alchemical"}, {@code "blood_staff"})</li>
 * </ul>
 * If no NBT is present the wand defaults to iron cap + wood rod.
 *
 * <p>Staff detection: the rod tag contains the substring {@code "_staff"}, which is
 * stripped before the rod lang-key lookup (same logic as TConstruct source line 347).
 * Sceptre detection requires instanceof StaffRod subclass checks not available here;
 * sceptre wands fall back to the wand object key — acceptable for now since sceptres
 * are rare and their names still assemble correctly with the wand object suffix.
 */
final class ThaumcraftDisplayNameResolver {

    private static final String CAP_NBT_KEY = "cap";
    private static final String ROD_NBT_KEY = "rod";

    private static final String DEFAULT_CAP_TAG = "iron";
    private static final String DEFAULT_ROD_TAG = "wood";

    private static final String STAFF_SUFFIX = "_staff";

    private ThaumcraftDisplayNameResolver() {}

    /**
     * Attempts to resolve the display name for the given Thaumcraft wand in the requested language.
     *
     * @return the localised name (e.g. {@code "Void Aspected Alchemical Wand"}),
     *         or {@code null} if the stack is not a Thaumcraft wand or resolution fails.
     */
    static String tryResolveDisplayName(ItemStack stack, String languageCode) {
        if (!isThaumcraftWand(stack)) {
            return null;
        }

        String capTag = DEFAULT_CAP_TAG;
        String rodTag = DEFAULT_ROD_TAG;

        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey(CAP_NBT_KEY)) {
                String v = nbt.getString(CAP_NBT_KEY);
                if (v != null && !v.isEmpty()) capTag = v;
            }
            if (nbt.hasKey(ROD_NBT_KEY)) {
                String v = nbt.getString(ROD_NBT_KEY);
                if (v != null && !v.isEmpty()) rodTag = v;
            }
        }

        // Staff rods have a "_staff" suffix in their tag; strip it for the lang-key lookup
        // and select the staff object key instead of the wand object key.
        String rodDisplayTag = rodTag;
        boolean isStaff = false;
        int staffIdx = rodTag.indexOf(STAFF_SUFFIX);
        if (staffIdx >= 0) {
            rodDisplayTag = rodTag.substring(0, staffIdx);
            isStaff = true;
        }

        String objKey = isStaff ? "item.Wand.staff.obj" : "item.Wand.wand.obj";

        String template = LanguageCache.translate(languageCode, "item.Wand.name");
        if (template == null || template.isEmpty()) {
            return null;
        }

        String capDisplay = LanguageCache.translate(languageCode, "item.Wand." + capTag + ".cap");
        if (capDisplay == null || capDisplay.isEmpty()) {
            return null;
        }

        String rodDisplay = LanguageCache.translate(languageCode, "item.Wand." + rodDisplayTag + ".rod");
        if (rodDisplay == null || rodDisplay.isEmpty()) {
            return null;
        }

        String objDisplay = LanguageCache.translate(languageCode, objKey);
        if (objDisplay == null || objDisplay.isEmpty()) {
            return null;
        }

        // Replace placeholders. The template may have no spaces between them (e.g. zh "%CAP%ROD%OBJ"),
        // which is correct for pure CJK names but leaves no separator when components fall back to
        // Latin text. Insert a space wherever two adjacent Latin (non-whitespace) characters meet
        // at a component boundary.
        String result = template
            .replace("%CAP", joinComponent(capDisplay))
            .replace("%ROD", joinComponent(rodDisplay))
            .replace("%OBJ", objDisplay);

        // Collapse any double spaces that may have been introduced and trim ends.
        result = result.replaceAll(" {2,}", " ").trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Wraps a component display string so that, when substituted into a template that has no
     * surrounding spaces (e.g. {@code "%CAP%ROD%OBJ"}), Latin words on adjacent boundaries get a
     * space separator. A trailing space is appended only when the string ends with a Latin
     * (non-whitespace, non-CJK) character; the leading case is handled symmetrically by the next
     * component's own wrapper or by {@code %OBJ} falling after a wrapped value.
     *
     * <p>Pure CJK values (e.g. {@code "能量"}) end with a CJK code point, so no trailing space is
     * added and Chinese names remain space-free. Latin values (e.g. {@code "Elementium Kissed"})
     * get a trailing space, giving {@code "Elementium Kissed Dreamwood"} after substitution.
     */
    private static String joinComponent(String s) {
        if (s == null || s.isEmpty()) return s;
        char last = s.charAt(s.length() - 1);
        // Append a trailing space only when the last char is a visible Latin/ASCII letter or digit
        // (not already a space, and not a CJK unified ideograph / punctuation range).
        if (last != ' ' && last < 0x2E80) {
            return s + ' ';
        }
        return s;
    }

    private static boolean isThaumcraftWand(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String className = stack.getItem().getClass().getName();
        return className.startsWith("thaumcraft.") && className.contains("ItemWandCasting");
    }
}
