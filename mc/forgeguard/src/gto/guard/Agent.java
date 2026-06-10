package gto.guard;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.jar.JarFile;

public final class Agent {
    private Agent() {}

    public static void premain(String args, Instrumentation inst) {
        System.out.println("[GTO-Guard] premain loaded.");
        appendSelfToBootstrap(inst);
        GuardTransformer t = new GuardTransformer(inst);
        inst.addTransformer(t, true);
        System.out.println("[GTO-Guard] Transformer registered (canRetransform=true).");
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    private static void appendSelfToBootstrap(Instrumentation inst) {
        try {
            Path path = Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            inst.appendToBootstrapClassLoaderSearch(new JarFile(path.toFile()));
            System.out.println("[GTO-Guard] Agent jar appended to bootstrap search: " + path);
        } catch (Throwable t) {
            System.out.println("[GTO-Guard] Could not append agent jar to bootstrap search: " + t);
        }
    }
}
