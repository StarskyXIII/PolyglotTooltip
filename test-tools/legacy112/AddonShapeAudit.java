import com.starskyxiii.polyglottooltip.legacy.bootstrap.Ae2Compatibility;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.RecipeViewerCompatibility;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.AeAddonCompatibility;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.AeAddonClassShape;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.ClassShape;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class AddonShapeAudit {
    private static final List<JarFile> jars = new ArrayList<>();
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (int index = 1; index < args.length; index++) jars.add(new JarFile(args[index]));
        try {
            String profile = args[0];
            String gasGui = "com.mekeng.github.client.gui.";
            if (profile.equals("mekeng")) {
                require(AeAddonCompatibility.names("mekeng", AddonShapeAudit::shape), "gas dummy names");
                accepts("com.mekeng.github.common.me.client.GasRepo", "MekanismEnergisticsGasRepositoryMixin");
                accepts(gasGui + "GuiGasTerminal", "Ae2RepositoryScreenMixin");
                accepts(gasGui + "GuiGasTerminal", "MekanismEnergisticsGasScreenTooltipMixin");
                for (String mixin : new String[]{"MekanismEnergisticsGasPredicateMixin", "Ae2InterfaceRefreshMixin", "Ae2MachineLabelMixin"}) accepts(gasGui + "GuiGasInterfaceConfigurationTerminal", mixin);
                accepts("com.mekeng.github.common.me.client.ClientDCInternalGasInv", "Ae2LabelOriginMixin");
                accepts("com.mekeng.github.client.slots.SlotGas", "MekanismEnergisticsGasSlotTooltipMixin");
                accepts("com.mekeng.github.client.slots.SlotGasTank", "MekanismEnergisticsGasSlotTooltipMixin");
                missingRejects("com.mekeng.github.common.me.client.GasRepo", "MekanismEnergisticsGasRepositoryMixin", gasGui + "GuiGasTerminal");
                missingRejects(gasGui + "GuiGasInterfaceConfigurationTerminal", "MekanismEnergisticsGasPredicateMixin", "com.mekeng.github.common.me.client.ClientDCInternalGasInv");
            } else if (profile.equals("ec")) {
                ClassShape fluid = shape("extracells.item.ItemFluid");
                ClassShape pattern = shape("extracells.item.ItemFluidPattern");
                ClassShape gas = shape("extracells.item.ItemGas$");
                String display = "(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;";
                require(fluid.staticMethod("getFluidName", display), "EC fluid identifier");
                require(fluid.method("func_77653_i", display), "EC fluid display");
                require(fluid.calls("func_77653_i", display, "net/minecraftforge/fluids/FluidRegistry", "getFluid", "(Ljava/lang/String;)Lnet/minecraftforge/fluids/Fluid;") == 1, "EC fluid registry");
                require(pattern.staticMethod("getFluid", "(Lnet/minecraft/item/ItemStack;)Lnet/minecraftforge/fluids/Fluid;"), "EC pattern decoder");
                require(pattern.constants("func_77667_c", display, "extracells.item.fluid.pattern") == 1, "EC pattern key");
                require(pattern.constants("func_77653_i", display, ": ") == 1, "EC pattern separator");
                require(gas.method("isMekanismGasEnabled", "()Z") && gas.method("getGasName", display) && gas.method("getItemStackDisplayNameGas", display) && gas.method("func_77653_i", display), "EC gas methods");
                require(gas.calls("func_77653_i", display, "extracells/item/ItemGas$", "isMekanismGasEnabled", "()Z") == 1, "EC native gas gate");
                require(AeAddonCompatibility.names("extracells", AddonShapeAudit::shape), "ExtraCells wrappers");
                require(AeAddonCompatibility.names("extracells-gas", AddonShapeAudit::shape), "ExtraCells gas fluid");
                accepts("extracells.gui.GuiTerminal", "ExtraCellsTerminalMixin");
                accepts("extracells.gui.widget.WidgetFluidTank", "ExtraCellsTankTooltipMixin");
                accepts("extracells.gui.widget.fluid.WidgetFluidSlot", "ExtraCellsSlotTooltipMixin");
                accepts("extracells.gui.widget.fluid.WidgetFluidSelector", "ExtraCellsWidgetTooltipMixin");
                accepts("extracells.gui.widget.fluid.WidgetFluidRequest", "ExtraCellsWidgetTooltipMixin");
                missingRejects("extracells.gui.widget.fluid.WidgetFluidRequest", "ExtraCellsWidgetTooltipMixin", "extracells.gui.widget.fluid.AbstractFluidWidget");
            } else if (profile.equals("fc-old") || profile.equals("fc-modern")) {
                boolean old = profile.equals("fc-old");
                require(AeAddonCompatibility.names("ae2fc", AddonShapeAudit::shape), "Fluid Crafting wrappers");
                require(AeAddonClassShape.dropProfile(shape("com.glodblock.github.common.item.ItemFluidDrop"), false) == (old ? 1 : 2), "fluid drop profile");
                require(AeAddonClassShape.dropProfile(shape("com.glodblock.github.common.item.ItemGasDrop"), true) == (old ? 1 : 2), "gas drop profile");
                require(AeAddonCompatibility.names("ae2fc-packaged", AddonShapeAudit::shape), "PackagedAuto owner");
                accepts("com.glodblock.github.handler.TankMouseHandler", "Ae2FluidCraftingTankMixin");
                if (old) {
                    accepts("com.glodblock.github.client.GuiFluidPatternEncoder", "Ae2FluidCraftingEncoderMixin");
                    require(!AeAddonClassShape.accepts(shape("com.glodblock.github.util.UtilClient"), "Ae2FluidCraftingRendererMixin"), "legacy renderer absent");
                } else {
                    accepts("com.glodblock.github.util.UtilClient", "Ae2FluidCraftingRendererMixin");
                    require(AeAddonClassShape.accepts(shape("com.glodblock.github.util.UtilClient"), "Ae2FluidCraftingGasRendererMixin"), "native gas renderer");
                    ClassNode stripped = node("com.glodblock.github.util.UtilClient");
                    stripped.methods.removeIf(method -> method.name.equals("rendererGas"));
                    require(AeAddonClassShape.accepts(shape(stripped), "Ae2FluidCraftingRendererMixin"), "optional gas removal retains fluid renderer");
                    require(!AeAddonClassShape.accepts(shape(stripped), "Ae2FluidCraftingGasRendererMixin"), "optional gas removal rejects gas renderer");
                }
                require(!AeAddonCompatibility.names("ae2fc", name -> name.endsWith("FakeItemRegister") ? null : shape(name)), "missing decoder rejects names");
            } else if (profile.equals("the")) {
                accepts("thaumicenergistics.client.gui.helpers.MERepo", "ThaumicEnergisticsRepositoryMixin");
                accepts("thaumicenergistics.client.gui.part.GuiArcaneTerminal", "ThaumicEnergisticsScreenRefreshMixin");
                accepts("thaumicenergistics.client.gui.part.GuiEssentiaTerminal", "ThaumicEnergisticsScreenRefreshMixin");
                missingRejects("thaumicenergistics.client.gui.helpers.MERepo", "ThaumicEnergisticsRepositoryMixin", "thaumicenergistics.client.gui.part.GuiArcaneTerminal");
                missingRejects("thaumicenergistics.client.gui.part.GuiArcaneTerminal", "ThaumicEnergisticsScreenRefreshMixin", "thaumicenergistics.client.gui.helpers.MERepo");
            } else if (profile.equals("p2p")) {
                accepts("com.projecturanus.betterp2p.client.gui.Filter$5", "BetterP2PTypeSearchMixin");
                accepts("com.projecturanus.betterp2p.client.gui.InfoList", "BetterP2PListRefreshMixin");
                missingRejects("com.projecturanus.betterp2p.client.gui.Filter$5", "BetterP2PTypeSearchMixin", "com.projecturanus.betterp2p.client.gui.InfoList");
                missingRejects("com.projecturanus.betterp2p.client.gui.Filter$5", "BetterP2PTypeSearchMixin", "appeng.items.parts.ItemPart");
                missingRejects("com.projecturanus.betterp2p.client.gui.InfoList", "BetterP2PListRefreshMixin", "appeng.core.localization.GuiText");
                require(!AeAddonClassShape.accepts(shape("com.projecturanus.betterp2p.client.gui.Filter$6"), "BetterP2PTypeSearchMixin"), "user labels rejected");
                ClassNode itemPart = node("appeng.items.parts.ItemPart");
                for (MethodNode method : itemPart.methods) {
                    if (!method.name.equals("func_77653_i")) continue;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction instanceof LdcInsnNode && " - ".equals(((LdcInsnNode) instruction).cst)) ((LdcInsnNode) instruction).cst = "/";
                    }
                }
                for (String mixin : new String[]{"BetterP2PTypeSearchMixin", "BetterP2PListRefreshMixin"}) require(!AeAddonCompatibility.enabled("com.projecturanus.betterp2p.client.gui.Filter$5", mixin,
                        name -> name.equals("appeng.items.parts.ItemPart") ? shape(itemPart) : shape(name)), "changed AE part separator rejects " + mixin);
                for (String mutation : new String[]{"delimiter", "component", "defaults"}) {
                    ClassNode tunnel = node("com.projecturanus.betterp2p.util.p2p.TunnelInfo");
                    for (MethodNode method : tunnel.methods) {
                        if (!method.name.equals("<init>")) continue;
                        for (AbstractInsnNode instruction : method.instructions.toArray()) {
                            if (mutation.equals("delimiter") && instruction instanceof LdcInsnNode && " - ".equals(((LdcInsnNode) instruction).cst)) ((LdcInsnNode) instruction).cst = "-";
                            if (!(instruction instanceof MethodInsnNode)) continue;
                            MethodInsnNode call = (MethodInsnNode) instruction;
                            if (mutation.equals("component") && call.name.equals("getOrNull")) method.instructions.set(instruction.getPrevious(), new InsnNode(Opcodes.ICONST_2));
                            if (mutation.equals("defaults") && call.name.equals("split$default")) method.instructions.set(instruction.getPrevious().getPrevious(), new IntInsnNode(Opcodes.BIPUSH, 4));
                        }
                    }
                    for (String mixin : new String[]{"BetterP2PTypeSearchMixin", "BetterP2PListRefreshMixin"}) require(!AeAddonCompatibility.enabled("com.projecturanus.betterp2p.client.gui.Filter$5", mixin,
                            name -> name.equals("com.projecturanus.betterp2p.util.p2p.TunnelInfo") ? shape(tunnel) : shape(name)), "changed P2P " + mutation + " rejects " + mixin);
                }
            } else if (profile.equals("lazy")) accepts("io.github.phantamanta44.threng.inventory.ContainerBigAssembler$Page$SlotPage", "LazyAe2AssemblerSearchMixin");
            else if (profile.equals("nee")) {
                accepts("com.github.vfyjxf.nee.client.gui.IngredientSwitcherWidget", "NotEnoughEnergisticsIngredientSearchMixin");
                missingRejects("com.github.vfyjxf.nee.client.gui.IngredientSwitcherWidget", "NotEnoughEnergisticsIngredientSearchMixin", "com.github.vfyjxf.nee.client.gui.widgets.ItemWidget");
            } else if (profile.equals("wct")) accepts("p455w0rd.wct.client.gui.GuiWCT", "Ae2RepositoryScreenMixin");
            else if (profile.equals("exttable")) accepts("com._0xc4de.ae2exttable.client.gui.GuiMEMonitorableTwo", "Ae2RepositoryScreenMixin");
            else throw new IllegalArgumentException(profile);
            System.out.println("{\"passed\":" + checks + ",\"profile\":\"" + profile + "\"}");
        } finally { for (JarFile jar : jars) jar.close(); }
    }

    private static ClassShape shape(String name) {
        byte[] bytes = bytes(name);
        return bytes == null ? null : new ClassShape(bytes);
    }

    private static ClassNode node(String name) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes(name)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static ClassShape shape(ClassNode node) {
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        return new ClassShape(writer.toByteArray());
    }

    private static byte[] bytes(String name) {
        try {
            for (JarFile jar : jars) {
                java.util.jar.JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
                if (entry == null) continue;
                try (InputStream input = jar.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                    return output.toByteArray();
                }
            }
            return null;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static void accepts(String target, String mixin) {
        ClassShape shape = shape(target);
        require(shape != null && ((Ae2Compatibility.accepts(shape, mixin) || RecipeViewerCompatibility.accepts(shape, mixin)) || AeAddonClassShape.accepts(shape, mixin)), target + " / " + mixin);
        require(AeAddonCompatibility.enabled(target, mixin, AddonShapeAudit::shape), target + " / integration unit");
    }

    private static void missingRejects(String target, String mixin, String missing) {
        require(!AeAddonCompatibility.enabled(target, mixin, name -> name.equals(missing) ? null : shape(name)), "missing " + missing);
    }

    private static void require(boolean passed, String name) {
        if (!passed) throw new AssertionError(name);
        checks++;
    }
}
