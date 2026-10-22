import com.starskyxiii.polyglottooltip.legacy.bootstrap.NameMethodCode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import java.io.PrintWriter;

/** Read-only source receipts for the narrow scoped-name constructor/getter profile. */
public final class InspectScopedSources {
    public static void main(String[] args) throws Exception {
        for (String file : args) {
            byte[] bytes = Files.readAllBytes(Paths.get(file));
            Map<String, String> methods = NameMethodCode.methods(bytes);
            ClassNode type = new ClassNode(); new ClassReader(bytes).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            System.out.println("SOURCE " + file);
            for (MethodNode method : type.methods) {
                if (!(method.name.equals("<init>") && method.desc.equals("(Lnet/minecraft/item/Item;IILnet/minecraft/nbt/NBTTagCompound;)V"))
                        && !method.name.equals("forgeInit") && !method.name.equals("func_190923_F")
                        && !method.name.equals("getItemRaw") && !method.name.equals("func_77653_i")
                        && !method.name.equals("func_77657_g") && !method.name.equals("func_77667_c")
                        && !method.name.equals("get") && !method.name.equals("func_179543_a")) continue;
                String key = type.name.replace('/', '.') + '#' + method.name + method.desc;
                System.out.println(key + '=' + methods.get(key));
                Textifier text = new Textifier(); method.accept(new TraceMethodVisitor(text));
                PrintWriter writer = new PrintWriter(System.out); text.print(writer); writer.flush();
            }
        }
    }
}
