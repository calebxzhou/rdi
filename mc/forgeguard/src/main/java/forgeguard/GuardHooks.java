package forgeguard;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class GuardHooks {
    private static final boolean PROTECT = !"audit".equalsIgnoreCase(System.getProperty("forgeguard.mode", "protect"));
    private static final Set<String> CORE_TRANSFORMATION_SERVICES = Set.of(
        "mixin",
        "fml",
        "accesstransformer",
        "eventbus",
        "runtime_enum_extender",
        "capability_token_subclass",
        "object_holder_definalize",
        "slf4jfixer",
        "gto_native"
    );

    private static final Map<Map<?, ?>, LinkedHashMap<Object, Object>> PLUGIN_SNAPSHOTS =
        Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> MIXIN_CONFIGS = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final ThreadLocal<Boolean> RESTORING_MIXINS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private GuardHooks() {
    }

    public static Set<?> protectOtherServices(Object service, Set<?> otherServices) {
        if (!isGtoService(service) || otherServices == null) {
            return otherServices;
        }

        LinkedHashSet<Object> filtered = new LinkedHashSet<>();
        for (Object item : otherServices) {
            String name = String.valueOf(item);
            if (CORE_TRANSFORMATION_SERVICES.contains(name)) {
                filtered.add(item);
            }
        }

        log("GTO service sees transformation services " + filtered + " instead of " + otherServices);
        return PROTECT ? filtered : otherServices;
    }

    public static void snapshotLaunchPlugins(Map<?, ?> plugins) {
        if (plugins == null || PLUGIN_SNAPSHOTS.containsKey(plugins)) {
            return;
        }

        LinkedHashMap<Object, Object> snapshot = new LinkedHashMap<>();
        plugins.forEach(snapshot::put);
        PLUGIN_SNAPSHOTS.put(plugins, snapshot);
        log("Snapshot launch plugins " + snapshot.keySet());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void protectLaunchPlugins(Map plugins) {
        if (plugins == null) {
            return;
        }

        LinkedHashMap<Object, Object> snapshot = PLUGIN_SNAPSHOTS.get(plugins);
        if (snapshot == null) {
            snapshotLaunchPlugins(plugins);
            return;
        }

        for (Map.Entry<Object, Object> entry : snapshot.entrySet()) {
            Object key = entry.getKey();
            Object original = entry.getValue();
            Object current = plugins.get(key);
            if (current == original) {
                continue;
            }

            String currentName = current == null ? "<missing>" : current.getClass().getName();
            log("Launch plugin " + key + " was changed to " + currentName + "; restoring "
                + original.getClass().getName());
            if (PROTECT) {
                plugins.put(key, original);
            }
        }
    }

    public static void recordMixinConfig(String configFile) {
        if (configFile == null || RESTORING_MIXINS.get()) {
            return;
        }
        if (isGtoMixinConfig(configFile)) {
            return;
        }
        if (MIXIN_CONFIGS.add(configFile)) {
            log("Recorded external mixin config " + configFile);
        }
    }

    public static void blockDcSharpExit(int status) {
        log("Blocked DCSharp System.exit(" + status + ") after its jar verification failed");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Set<?> protectMixinConfigs(Set configs) {
        if (configs == null || !PROTECT || RESTORING_MIXINS.get() || MIXIN_CONFIGS.isEmpty()) {
            return configs;
        }

        try {
            RESTORING_MIXINS.set(Boolean.TRUE);
            Set<String> present = new LinkedHashSet<>();
            for (Object config : configs) {
                String name = configName(config);
                if (name != null) {
                    present.add(name);
                }
            }

            for (String configFile : MIXIN_CONFIGS) {
                if (present.contains(configFile)) {
                    continue;
                }
                Object config = createMixinConfig(configFile, configs);
                if (config != null && configs.add(config)) {
                    log("Restored mixin config " + configFile);
                }
            }
        } catch (Throwable t) {
            log("Mixin config protection failed: " + t);
        } finally {
            RESTORING_MIXINS.set(Boolean.FALSE);
        }

        return configs;
    }

    private static boolean isGtoService(Object service) {
        if (service == null) {
            return false;
        }
        String className = service.getClass().getName();
        if ("gto.native0.plugins.GTOServices".equals(className)) {
            return true;
        }
        try {
            Method name = service.getClass().getMethod("name");
            return "gto_native".equals(String.valueOf(name.invoke(service)));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isGtoMixinConfig(String configFile) {
        return configFile.startsWith("gtolib.")
            || configFile.startsWith("gtocore.")
            || configFile.contains("/gtolib")
            || configFile.contains("/gtocore");
    }

    private static String configName(Object config) {
        if (config == null) {
            return null;
        }
        try {
            Method getName = config.getClass().getMethod("getName");
            return String.valueOf(getName.invoke(config));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object createMixinConfig(String configFile, Set<?> existingConfigs) throws Exception {
        ClassLoader loader = findMixinClassLoader(existingConfigs);
        Class<?> environmentClass = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", false, loader);
        Object environment = environmentClass.getMethod("getDefaultEnvironment").invoke(null);
        Class<?> configClass = Class.forName("org.spongepowered.asm.mixin.transformer.Config", false, loader);
        Method create = configClass.getMethod("create", String.class, environmentClass);
        return create.invoke(null, configFile, environment);
    }

    private static ClassLoader findMixinClassLoader(Set<?> existingConfigs) {
        for (Object config : existingConfigs) {
            if (config != null && config.getClass().getClassLoader() != null) {
                return config.getClass().getClassLoader();
            }
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            return context;
        }
        return ClassLoader.getSystemClassLoader();
    }

    private static void log(String message) {
        System.out.println("[Forgeguard] " + message);
    }
}
