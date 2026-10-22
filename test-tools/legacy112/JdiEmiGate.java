import com.google.gson.Gson;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class JdiEmiGate {
    private static final Gson gson = new Gson();
    private static void emit(String event, Object... fields) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("event", event); value.put("time", System.currentTimeMillis());
        for (int index = 0; index < fields.length; index += 2) value.put((String) fields[index], fields[index + 1]);
        System.out.println(gson.toJson(value)); System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        VirtualMachine vm = null; EventSet held = null; BreakpointRequest breakpoint = null;
        ThreadDeathRequest death = null; ModificationWatchpointRequest publication = null; ThreadReference worker = null;
        try {
            AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                    .filter(value -> value.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
            Map<String, Connector.Argument> connection = connector.defaultArguments();
            connection.get("hostname").setValue("127.0.0.1"); connection.get("port").setValue(args[0]); connection.get("timeout").setValue("10000");
            vm = connector.attach(connection);
            boolean product = Boolean.parseBoolean(args[3]); Path directory = Paths.get(args[1]);
            ReferenceType queryType = type(vm, "dev.emi.emi.search.EmiSearch$CompiledQuery"), searchType = type(vm, "dev.emi.emi.search.EmiSearch");
            Method constructor = queryType.methodsByName("<init>", "(Ljava/lang/String;)V").get(0);
            List<Map<String, Object>> bytes = new ArrayList<>(); bytes.add(verify(constructor, directory));
            Location location = constructor.location(); JdiGate.require(location.codeIndex() == 0, "Unexpected constructor entry");
            breakpoint = vm.eventRequestManager().createBreakpointRequest(location);
            breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); breakpoint.enable();
            emit("armed", "location", location.toString(), "codeIndex", location.codeIndex(), "classes", bytes);
            BlockingQueue<String> commands = new LinkedBlockingQueue<>();
            Thread input = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                    String command; while ((command = reader.readLine()) != null) commands.offer(command);
                } catch (IOException error) { commands.offer("input-error"); }
                finally { commands.offer("eof"); }
            }, "jdi-emi-input"); input.setDaemon(true); input.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            boolean released = false, died = false, terminated = false, finished = false;
            long queryIdentity = -1;
            while (System.nanoTime() < deadline) {
                String command = commands.poll();
                if (command != null) {
                    if (command.equals("finish")) {
                        JdiGate.require(released && died && terminated, "Cannot finish before original worker termination");
                        finished = true; emit("finished"); break;
                    }
                    JdiGate.require(held != null && (command.equals("release") || command.equals("check")), "Unexpected controller command " + command);
                    JdiGate.require(worker.isSuspended() && worker.frame(0).location().equals(location), "Constructor no longer held");
                    boolean owns = product && ownsGate(vm, worker);
                    JdiGate.require(!product || owns, "Held constructor lost gate ownership");
                    emit("stillHeld", "thread", worker.uniqueID(), "codeIndex", worker.frame(0).location().codeIndex(), "gateOwned", owns);
                    if (command.equals("release")) { held.resume(); held = null; released = true; emit("released", "thread", worker.uniqueID()); }
                }
                if (released && died && !terminated && worker.status() == ThreadReference.THREAD_STATUS_ZOMBIE) {
                    terminated = true; emit("terminated", "thread", worker.uniqueID(), "status", worker.status());
                }
                EventSet events = vm.eventQueue().remove(100); if (events == null) continue;
                boolean keep = false;
                try {
                    for (Event event : events) {
                        if (event instanceof ModificationWatchpointEvent) {
                            ModificationWatchpointEvent write = (ModificationWatchpointEvent) event;
                            JdiGate.require(events.suspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD, "Unexpected publication suspension policy");
                            Value value = write.valueToBe(); long identity = value == null ? 0 : ((ObjectReference) value).uniqueID();
                            emit("publication", "thread", write.thread().uniqueID(), "query", identity, "retiredConstructor", identity == queryIdentity);
                        } else if (event instanceof ThreadDeathEvent) {
                            JdiGate.require(((ThreadDeathEvent) event).thread().equals(worker), "Unexpected thread death");
                            died = true; emit("death", "thread", worker.uniqueID());
                        } else if (event instanceof BreakpointEvent) {
                            BreakpointEvent point = (BreakpointEvent) event;
                            JdiGate.require(events.suspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD, "Unexpected event suspension policy");
                            ThreadReference candidate = point.thread(); StackFrame top = candidate.frame(0);
                            String text = ((StringReference) top.getArgumentValues().get(0)).value();
                            if (!text.equals(args[2]) || worker != null || !point.location().equals(location)
                                    || !candidate.equals(searchType.getValue(searchType.fieldByName("searchThread")))) {
                                emit("skipped", "thread", candidate.uniqueID(), "query", text); continue;
                            }
                            List<String> frames = new ArrayList<>(); boolean nativeRun = false, compile = false;
                            List<Method> nativeMethods = new ArrayList<>();
                            for (StackFrame frame : candidate.frames()) {
                                Method method = frame.location().method(); String owner = method.declaringType().name();
                                frames.add(owner + "." + method.name() + method.signature() + ":" + frame.location().codeIndex());
                                if (owner.equals("dev.emi.emi.search.EmiSearch$SearchWorker") && method.name().equals("run")) { nativeRun = true; nativeMethods.add(method); }
                                if (owner.equals("com.starskyxiii.polyglottooltip.legacy.integration.emi.EmiSearchCoordinator") && method.name().equals("compile")) { compile = true; nativeMethods.add(method); }
                            }
                            if (!nativeRun || compile != product) { emit("skipped", "thread", candidate.uniqueID(), "query", text, "frames", frames); continue; }
                            for (Method method : nativeMethods) bytes.add(verify(method, directory));
                            worker = candidate; worker.disableCollection();
                            queryIdentity = top.thisObject().uniqueID();
                            boolean owns = product && ownsGate(vm, worker); JdiGate.require(!product || owns, "Constructor does not own compile gate");
                            JdiGate.require(vm.canWatchFieldModification(), "Publication watchpoints unavailable");
                            publication = vm.eventRequestManager().createModificationWatchpointRequest(searchType.fieldByName("compiledQuery"));
                            publication.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); publication.enable();
                            death = vm.eventRequestManager().createThreadDeathRequest(); death.addThreadFilter(worker);
                            death.setSuspendPolicy(EventRequest.SUSPEND_NONE); death.enable();
                            breakpoint.disable(); vm.eventRequestManager().deleteEventRequest(breakpoint); breakpoint = null;
                            held = events; keep = true;
                            emit("held", "thread", worker.uniqueID(), "name", worker.name(), "query", text, "queryIdentity", queryIdentity, "codeIndex", location.codeIndex(),
                                    "suspendPolicy", events.suspendPolicy(), "suspendCount", worker.suspendCount(), "gateOwned", owns,
                                    "ownedMonitorCount", vm.canGetOwnedMonitorInfo() ? worker.ownedMonitors().size() : -1, "frames", frames, "classes", bytes);
                        }
                    }
                } finally { if (!keep) events.resume(); }
            }
            JdiGate.require(released && died && terminated && finished, "Constructor scheduling timeout");
        } catch (Throwable error) { emit("error", "type", error.getClass().getName(), "message", error.getMessage()); throw error; }
        finally {
            if (vm != null) {
                try { if (breakpoint != null) vm.eventRequestManager().deleteEventRequest(breakpoint); } catch (RuntimeException ignored) { }
                try { if (death != null) vm.eventRequestManager().deleteEventRequest(death); } catch (RuntimeException ignored) { }
                try { if (publication != null) vm.eventRequestManager().deleteEventRequest(publication); } catch (RuntimeException ignored) { }
                try { if (held != null) held.resume(); } catch (RuntimeException ignored) { }
                try { if (worker != null) worker.enableCollection(); } catch (RuntimeException ignored) { }
                try { vm.dispose(); } catch (RuntimeException ignored) { }
            }
        }
    }

    private static ReferenceType type(VirtualMachine vm, String name) {
        List<ReferenceType> values = vm.classesByName(name); JdiGate.require(values.size() == 1 && values.get(0).isPrepared(), "Missing or ambiguous type " + name); return values.get(0);
    }
    private static boolean ownsGate(VirtualMachine vm, ThreadReference worker) {
        ReferenceType bridge = type(vm, "com.starskyxiii.polyglottooltip.legacy.integration.emi.EmiSearchCoordinator");
        ObjectReference gate = (ObjectReference) bridge.getValue(bridge.fieldByName("indexGate"));
        ObjectReference sync = (ObjectReference) gate.getValue(gate.referenceType().fieldByName("sync"));
        return worker.equals(sync.getValue(sync.referenceType().fieldByName("exclusiveOwnerThread")))
                && ((IntegerValue) sync.getValue(sync.referenceType().fieldByName("state"))).value() > 0;
    }
    private static Map<String, Object> verify(Method method, Path directory) throws Exception {
        Path file = directory.resolve(method.declaringType().name().replace('.', '/') + ".class"); byte[] saved = Files.readAllBytes(file), live = method.bytecodes();
        JdiGate.require(!method.isObsolete() && Arrays.equals(live, JdiGate.code(saved, method.name(), method.signature())), "Native method bytes changed " + method);
        Map<String, Object> value = new LinkedHashMap<>(); value.put("owner", method.declaringType().name()); value.put("method", method.name());
        value.put("descriptor", method.signature()); value.put("classSha256", JdiGate.hash(saved)); value.put("codeSha256", JdiGate.hash(live));
        value.put("loader", method.declaringType().classLoader().uniqueID()); value.put("loaderType", method.declaringType().classLoader().referenceType().name()); return value;
    }
}
