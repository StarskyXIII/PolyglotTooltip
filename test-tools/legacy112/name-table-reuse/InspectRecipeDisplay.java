import com.starskyxiii.polyglottooltip.legacy.bootstrap.NameMethodCode;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.ClassShape;
import com.starskyxiii.polyglottooltip.legacy.bootstrap.RecipeViewerCompatibility;
import java.util.jar.JarFile;
import java.util.Map;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Read-only method receipts for the recipe display wrapper used by the core name table. */
public final class InspectRecipeDisplay {
    public static void main(String[] args) throws Exception {
        for (String file : args) {
            System.out.println(file);
            try (JarFile jar = new JarFile(file)) {
                Function<String, ClassShape> lookup = owner -> {
                    try {
                        java.util.jar.JarEntry entry = jar.getJarEntry(owner.replace('.', '/') + ".class");
                        return entry == null ? null : new ClassShape(jar.getInputStream(entry).readAllBytes());
                    } catch (Exception error) { throw new RuntimeException(error); }
                };
                RecipeViewerCompatibility.Recipe recipe = RecipeViewerCompatibility.recipe(lookup);
                boolean accepted = RecipeViewerCompatibility.rawDisplayNames();
                if (accepted != (recipe == RecipeViewerCompatibility.Recipe.HEI)) throw new AssertionError(file);
                System.out.println("raw-display=" + accepted + " recipe=" + recipe);
                for (String owner : new String[]{"mezz.jei.ingredients.IngredientListElement",
                        "mezz.jei.ingredients.IngredientInformation",
                        "mezz.jei.plugins.vanilla.ingredients.item.ItemStackHelper"}) {
                    byte[] bytes = jar.getInputStream(jar.getJarEntry(owner.replace('.', '/') + ".class")).readAllBytes();
                    for (Map.Entry<String, String> entry : NameMethodCode.methods(bytes).entrySet())
                        if (entry.getKey().contains("#getDisplayName(") || entry.getKey().contains("#removeChatFormatting(")
                                || entry.getKey().contains("#getIngredient(") || entry.getKey().contains("#getIngredientHelper("))
                            System.out.println(entry.getKey() + " " + entry.getValue());
                    if (accepted) {
                        ClassNode type = new ClassNode();
                        new ClassReader(bytes).accept(type, 0);
                        int checked = 0;
                        for (MethodNode method : type.methods) {
                            if (!method.name.equals("getDisplayName") && !method.name.equals("removeChatFormatting")
                                    && !method.name.equals("getIngredient") && !method.name.equals("getIngredientHelper")) continue;
                            InsnNode change = new InsnNode(Opcodes.NOP);
                            method.instructions.insert(change);
                            ClassWriter writer = new ClassWriter(0); type.accept(writer);
                            ClassShape changed = new ClassShape(writer.toByteArray());
                            RecipeViewerCompatibility.recipe(name -> name.equals(owner) ? changed : lookup.apply(name));
                            if (RecipeViewerCompatibility.rawDisplayNames()) throw new AssertionError("Changed wrapper accepted: " + owner + "#" + method.name);
                            method.instructions.remove(change); checked++;
                        }
                        System.out.println("changed-methods-rejected=" + checked);
                    }
                }
            }
        }
    }
}
