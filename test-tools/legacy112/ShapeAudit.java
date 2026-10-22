import com.starskyxiii.polyglottooltip.legacy.bootstrap.Ae2Compatibility;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.RecipeViewerCompatibility;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.ClassShape;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.jar.JarFile;

public final class ShapeAudit {
    private static JarFile jar;
    private static int checks;

    public static void main(String[] args) throws Exception {
        String profile = args[0];
        try (JarFile archive = new JarFile(args[1])) {
            jar = archive;
            if (profile.equals("jei") || profile.equals("hei")) {
                require(RecipeViewerCompatibility.recipe(ShapeAudit::shape).name().equals(profile.toUpperCase()), "recipe profile");
                accepts("mezz.jei.plugins.vanilla.ingredients.fluid.FluidStackRenderer", "FluidTooltipMixin");
                require(RecipeViewerCompatibility.recipe(name -> name.endsWith("IngredientListOverlay") ? null : shape(name)) == RecipeViewerCompatibility.Recipe.NONE, "missing overlay rejects profile");
                require(RecipeViewerCompatibility.recipe(name -> name.equals("mezz.jei.Internal") ? null : shape(name)) == RecipeViewerCompatibility.Recipe.NONE, "missing registration rejects profile");
                if (profile.equals("hei")) require(RecipeViewerCompatibility.recipe(name -> name.equals("mezz.jei.util.Translator") ? null : shape(name)) == RecipeViewerCompatibility.Recipe.NONE, "missing locale refresh rejects profile");
            } else if (profile.equals("ae-rv6") || profile.equals("ae-uel")) {
                boolean tokens = profile.equals("ae-uel");
                accepts("appeng.client.me.ItemRepo", tokens ? "Ae2ItemRepositoryTokensMixin" : "Ae2ItemRepositoryRegexMixin");
                require(!Ae2Compatibility.accepts(shape("appeng.client.me.ItemRepo"), tokens ? "Ae2ItemRepositoryRegexMixin" : "Ae2ItemRepositoryTokensMixin"), "opposite repository rejected");
                accepts("appeng.client.me.FluidRepo", "Ae2FluidRepositoryRegexMixin");
                accepts("appeng.util.item.AEItemStack", "Ae2ItemNameCacheMixin");
                accepts("appeng.client.gui.implementations.GuiMEMonitorable", "Ae2RepositoryScreenMixin");
                accepts("appeng.fluids.client.gui.GuiFluidTerminal", "Ae2RepositoryScreenMixin");
                accepts("appeng.fluids.client.gui.GuiFluidTerminal", "Ae2FluidScreenTooltipMixin");
                accepts("appeng.fluids.client.gui.widgets.GuiFluidTank", "Ae2FluidTankTooltipMixin");
                accepts("appeng.fluids.client.gui.widgets.GuiFluidSlot", "Ae2FluidSlotTooltipMixin");
                accepts("appeng.client.gui.implementations.GuiInterfaceTerminal", "Ae2ItemPredicateMixin");
                accepts("appeng.client.gui.implementations.GuiInterfaceTerminal", "Ae2InterfaceRefreshMixin");
                accepts("appeng.client.me.ClientDCInternalInv", "Ae2LabelOriginMixin");
                accepts("appeng.client.gui.implementations.GuiInterfaceTerminal", "Ae2MachineLabelMixin");
                require(Ae2Compatibility.accepts(shape("appeng.client.gui.implementations.GuiInterfaceTerminal"), "Ae2DualMachineLabelMixin") == Boolean.parseBoolean(args[2]), "dual label classifier isolation");
                if (tokens) {
                    accepts("appeng.fluids.client.gui.GuiMEPortableFluidCell", "Ae2RepositoryScreenMixin");
                    accepts("appeng.fluids.client.gui.GuiMEPortableFluidCell", "Ae2FluidScreenTooltipMixin");
                    accepts("appeng.client.gui.implementations.GuiInterfaceConfigurationTerminal", "Ae2ItemPredicateMixin");
                    accepts("appeng.client.gui.implementations.GuiInterfaceConfigurationTerminal", "Ae2InterfaceRefreshMixin");
                    accepts("appeng.client.gui.implementations.GuiFluidInterfaceConfigurationTerminal", "Ae2FluidPredicateMixin");
                    accepts("appeng.client.gui.implementations.GuiFluidInterfaceConfigurationTerminal", "Ae2InterfaceRefreshMixin");
                    accepts("appeng.client.me.ClientDCInternalFluidInv", "Ae2LabelOriginMixin");
                    accepts("appeng.client.gui.implementations.GuiInterfaceConfigurationTerminal", "Ae2MachineLabelMixin");
                    accepts("appeng.client.gui.implementations.GuiFluidInterfaceConfigurationTerminal", "Ae2MachineLabelMixin");
                }
            } else if (profile.equals("mek")) accepts("mekanism.client.jei.gas.GasStackRenderer", "MekanismJeiGasTooltipMixin");
            else throw new IllegalArgumentException("Unknown profile " + profile);
        }
        System.out.println("{\"passed\":" + checks + ",\"profile\":\"" + profile + "\"}");
    }

    private static ClassShape shape(String name) {
        try {
            java.util.jar.JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
            if (entry == null) return null;
            try (InputStream input = jar.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                return new ClassShape(output.toByteArray());
            }
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static void accepts(String name, String mixin) {
        ClassShape shape = shape(name);
        require(shape != null && (Ae2Compatibility.accepts(shape, mixin) || RecipeViewerCompatibility.accepts(shape, mixin)), name + " / " + mixin);
    }

    private static void require(boolean passed, String name) {
        if (!passed) throw new AssertionError(name);
        checks++;
    }
}
