package gto.guard;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardTransformer implements ClassFileTransformer {
    private static final String HOOKS = "gto/guard/GuardHooks";
    private static final String TRANSFORMATION_DECORATOR = "cpw/mods/modlauncher/TransformationServiceDecorator";
    private static final String LAUNCH_PLUGIN_HANDLER = "cpw/mods/modlauncher/LaunchPluginHandler";
    private static final String MIXINS = "org/spongepowered/asm/mixin/Mixins";
    private static final String MIXIN_PLUGIN = "com/gtolib/MixinConfigPlugin";
    private static final String ABSTRACT_MIXIN = "com/gtolib/api/misc/AbstractMixinConfigPlugin";
    private static final String ERR_CLASS = "java/lang/UnsatisfiedLinkError";

    private static final boolean PATCH_GTO_MIXIN_PLUGIN =
        Boolean.getBoolean("gto.guard.patchGtoMixinPlugin");
    private static final Set<String> LEGACY_TARGETS = Set.of(MIXIN_PLUGIN, ABSTRACT_MIXIN);

    private final Instrumentation inst;
    private final Set<String> scheduled = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public GuardTransformer(Instrumentation inst) {
        this.inst = inst;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain pd, byte[] buf) {
        try {
            if (TRANSFORMATION_DECORATOR.equals(className)) {
                System.out.println("[GTO-Guard] Patching TransformationServiceDecorator.onLoad");
                return patchTransformationServiceDecorator(buf);
            }
            if (LAUNCH_PLUGIN_HANDLER.equals(className)) {
                System.out.println("[GTO-Guard] Patching LaunchPluginHandler plugin map guards");
                return patchLaunchPluginHandler(buf);
            }
            if (MIXINS.equals(className)) {
                System.out.println("[GTO-Guard] Patching Mixins config queue guards");
                return patchMixins(buf);
            }
            if (PATCH_GTO_MIXIN_PLUGIN && LEGACY_TARGETS.contains(className)) {
                return patchLegacyGtoMixinPlugin(className, classBeingRedefined, buf);
            }
        } catch (Throwable t) {
            System.out.println("[GTO-Guard] Failed to patch " + className + ": " + t);
        }
        return null;
    }

    private byte[] patchTransformationServiceDecorator(byte[] buf) {
        ClassReader cr = new ClassReader(buf);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exns) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exns);
                if ("onLoad".equals(name) && desc.endsWith("Ljava/util/Set;)V")) {
                    return new AdviceAdapter(Opcodes.ASM9, mv, access, name, desc) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
                            if (opcode == Opcodes.INVOKEINTERFACE
                                && "cpw/mods/modlauncher/api/ITransformationService".equals(owner)
                                && "onLoad".equals(methodName)
                                && methodDesc.endsWith("Ljava/util/Set;)V")) {
                                int servicesLocal = newLocal(Type.getType("Ljava/util/Set;"));
                                int envLocal = newLocal(Type.getType("Lcpw/mods/modlauncher/api/IEnvironment;"));
                                int serviceLocal = newLocal(Type.getType("Lcpw/mods/modlauncher/api/ITransformationService;"));
                                super.visitVarInsn(Opcodes.ASTORE, servicesLocal);
                                super.visitVarInsn(Opcodes.ASTORE, envLocal);
                                super.visitVarInsn(Opcodes.ASTORE, serviceLocal);
                                super.visitVarInsn(Opcodes.ALOAD, serviceLocal);
                                super.visitVarInsn(Opcodes.ALOAD, envLocal);
                                super.visitVarInsn(Opcodes.ALOAD, serviceLocal);
                                super.visitVarInsn(Opcodes.ALOAD, servicesLocal);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    HOOKS,
                                    "protectOtherServices",
                                    "(Ljava/lang/Object;Ljava/util/Set;)Ljava/util/Set;",
                                    false
                                );
                            }
                            super.visitMethodInsn(opcode, owner, methodName, methodDesc, itf);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    private byte[] patchLaunchPluginHandler(byte[] buf) {
        ClassReader cr = new ClassReader(buf);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exns) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exns);
                if ("<init>".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                loadPluginsField();
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    HOOKS,
                                    "snapshotLaunchPlugins",
                                    "(Ljava/util/Map;)V",
                                    false
                                );
                            }
                            super.visitInsn(opcode);
                        }

                        private void loadPluginsField() {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(
                                Opcodes.GETFIELD,
                                LAUNCH_PLUGIN_HANDLER,
                                "plugins",
                                "Ljava/util/Map;"
                            );
                        }
                    };
                }
                if (needsPluginProtection(name)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(
                                Opcodes.GETFIELD,
                                LAUNCH_PLUGIN_HANDLER,
                                "plugins",
                                "Ljava/util/Map;"
                            );
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                HOOKS,
                                "protectLaunchPlugins",
                                "(Ljava/util/Map;)V",
                                false
                            );
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    private static boolean needsPluginProtection(String name) {
        return "get".equals(name)
            || "computeLaunchPluginTransformerSet".equals(name)
            || "offerScanResultsToPlugins".equals(name)
            || "offerClassNodeToPlugins".equals(name)
            || "announceLaunch".equals(name);
    }

    private byte[] patchMixins(byte[] buf) {
        ClassReader cr = new ClassReader(buf);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exns) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exns);
                if ("createConfiguration".equals(name) && desc.startsWith("(Ljava/lang/String;")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                HOOKS,
                                "recordMixinConfig",
                                "(Ljava/lang/String;)V",
                                false
                            );
                        }
                    };
                }
                if ("getConfigs".equals(name) && "()Ljava/util/Set;".equals(desc)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ARETURN) {
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    HOOKS,
                                    "protectMixinConfigs",
                                    "(Ljava/util/Set;)Ljava/util/Set;",
                                    false
                                );
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    private byte[] patchLegacyGtoMixinPlugin(String className, Class<?> classBeingRedefined, byte[] buf) {
        if (classBeingRedefined != null) {
            System.out.println("[GTO-Guard] Legacy Phase 2: rewriting " + className.replace('/', '.'));
            return rewriteLegacyMixinMethods(buf);
        }

        System.out.println("[GTO-Guard] Legacy Phase 1: " + className.replace('/', '.'));
        boolean needsFix = hasThrowingInit(buf);
        byte[] result = needsFix ? unstick(buf) : null;
        if (scheduled.add(className)) {
            scheduleRetransform(className);
        }
        return result;
    }

    private void scheduleRetransform(String className) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            for (Class<?> cls : inst.getAllLoadedClasses()) {
                if (cls.getName().equals(className.replace('/', '.'))) {
                    try {
                        inst.retransformClasses(cls);
                    } catch (Throwable e) {
                        System.out.println("[GTO-Guard] Retransform failed: " + e);
                    }
                    return;
                }
            }
        }, "GTO-Guard-phase2");
        t.setDaemon(true);
        t.start();
    }

    private byte[] unstick(byte[] buf) {
        ClassReader cr = new ClassReader(buf);
        String superName = cr.getSuperName();
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exns) {
                if ("<init>".equals(name)) {
                    return new SuppressBody(super.visitMethod(access, name, desc, sig, exns)) {
                        @Override
                        protected void emit() {
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
                            mv.visitInsn(Opcodes.RETURN);
                            mv.visitMaxs(1, 1);
                        }
                    };
                }
                if ("<clinit>".equals(name)) {
                    return new SuppressBody(super.visitMethod(access, name, desc, sig, exns)) {
                        @Override
                        protected void emit() {
                            mv.visitInsn(Opcodes.RETURN);
                            mv.visitMaxs(0, 0);
                        }
                    };
                }
                return super.visitMethod(access, name, desc, sig, exns);
            }
        }, 0);
        return cw.toByteArray();
    }

    private static boolean hasThrowingInit(byte[] buf) {
        ClassReader cr = new ClassReader(buf);
        boolean[] found = {false};
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exns) {
                if ("<init>".equals(name) || "<clinit>".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && ERR_CLASS.equals(type)) {
                                found[0] = true;
                            }
                        }
                    };
                }
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private byte[] rewriteLegacyMixinMethods(byte[] buf) {
        ClassReader cr = new ClassReader(buf);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exns) {
                if ("shouldApplyMixin".equals(name) && (access & Opcodes.ACC_NATIVE) != 0) {
                    MethodVisitor mv = super.visitMethod(access & ~Opcodes.ACC_NATIVE, name, desc, sig, exns);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitInsn(Opcodes.IRETURN);
                    mv.visitMaxs(1, 2);
                    mv.visitEnd();
                    return null;
                }
                if ("acceptTargets".equals(name) && (access & Opcodes.ACC_NATIVE) != 0) {
                    MethodVisitor mv = super.visitMethod(access & ~Opcodes.ACC_NATIVE, name, desc, sig, exns);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 2);
                    mv.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, desc, sig, exns);
            }
        }, 0);
        return cw.toByteArray();
    }

    private abstract static class SuppressBody extends MethodVisitor {
        boolean done;

        SuppressBody(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        protected abstract void emit();

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (!done && opcode == Opcodes.NEW && ERR_CLASS.equals(type)) {
                done = true;
                emit();
                return;
            }
            if (!done) {
                super.visitTypeInsn(opcode, type);
            }
        }

        @Override public void visitCode() { if (!done) super.visitCode(); }
        @Override public void visitInsn(int op) { if (!done) super.visitInsn(op); }
        @Override public void visitVarInsn(int o, int v) { if (!done) super.visitVarInsn(o, v); }
        @Override public void visitFieldInsn(int o, String ow, String n, String d) { if (!done) super.visitFieldInsn(o, ow, n, d); }
        @Override public void visitMethodInsn(int o, String ow, String n, String d, boolean i) { if (!done) super.visitMethodInsn(o, ow, n, d, i); }
        @Override public void visitJumpInsn(int o, Label l) { if (!done) super.visitJumpInsn(o, l); }
        @Override public void visitLdcInsn(Object c) { if (!done) super.visitLdcInsn(c); }
        @Override public void visitIntInsn(int o, int v) { if (!done) super.visitIntInsn(o, v); }
        @Override public void visitIincInsn(int v, int i) { if (!done) super.visitIincInsn(v, i); }
        @Override public void visitTableSwitchInsn(int min, int max, Label d, Label... ls) { if (!done) super.visitTableSwitchInsn(min, max, d, ls); }
        @Override public void visitLookupSwitchInsn(Label d, int[] ks, Label[] ls) { if (!done) super.visitLookupSwitchInsn(d, ks, ls); }
        @Override public void visitMultiANewArrayInsn(String d, int dims) { if (!done) super.visitMultiANewArrayInsn(d, dims); }
        @Override public void visitTryCatchBlock(Label s, Label e, Label h, String t) { if (!done) super.visitTryCatchBlock(s, e, h, t); }
        @Override public void visitLabel(Label l) { if (!done) super.visitLabel(l); }
        @Override public void visitFrame(int t, int nl, Object[] l, int ns, Object[] s) { if (!done) super.visitFrame(t, nl, l, ns, s); }
        @Override public void visitLineNumber(int ln, Label st) { if (!done) super.visitLineNumber(ln, st); }
        @Override public void visitLocalVariable(String n, String d, String s, Label st, Label e, int i) { if (!done) super.visitLocalVariable(n, d, s, st, e, i); }
        @Override public void visitMaxs(int ms, int ml) { if (!done) super.visitMaxs(ms, ml); }
    }
}
