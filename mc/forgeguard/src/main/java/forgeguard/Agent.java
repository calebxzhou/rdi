package forgeguard;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.jar.JarFile;

public final class Agent {
    private Agent() {}

    public static void premain(String args, Instrumentation inst) {
        System.out.println("[Forgeguard] premain loaded.");
        appendSelfToBootstrap(inst);
        GuardTransformer transformer = new GuardTransformer(inst);
        inst.addTransformer(transformer, true);
        System.out.println("[Forgeguard] Transformer registered (canRetransform=true).");
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    private static void appendSelfToBootstrap(Instrumentation inst) {
        try {
            Path path = Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            inst.appendToBootstrapClassLoaderSearch(new JarFile(path.toFile()));
            System.out.println("[Forgeguard] Agent jar appended to bootstrap search: " + path);
        } catch (Throwable throwable) {
            System.out.println("[Forgeguard] Could not append agent jar to bootstrap search: " + throwable);
        }
    }
}
