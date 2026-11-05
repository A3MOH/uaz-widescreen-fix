import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class Patcher {
    static final String H = "UIAspect";
    static final String RAIN = "RainFX";
    static final String LOOK = "CockpitLook";
    static final Set<String> optHelpers = new HashSet<>();
    static final Set<String> SCALE_CLASSES = new HashSet<>(Arrays.asList(
        "UIButtonWithBackground", "UIStatic", "UITexture", "UICursor",
        "UIOptionsControlEdit", "UIOptionsControlEditSelKey", "UIOptionsControlSlider",
        "UIOptionsControlSelect", "RallyUserInterface"));

    public static void main(String[] a) throws Exception {
        File inPak = new File(a[0]), outPak = new File(a[1]), helper = new File(a[2]);
        File inInf = new File(a[3]), outInf = new File(a[4]);
        List<File> extra = new ArrayList<>();
        for (int i = 5; i < a.length; i++) {
            File f = new File(a[i]);
            if (f.exists()) {
                extra.add(f);
                optHelpers.add(f.getName().replace(".class", ""));
            }
        }
        System.out.println("optional helpers: " + optHelpers);
        ZipFile zf = new ZipFile(inPak);
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(outPak), Charset.forName("Cp437"));
        int patched = 0;
        for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements();) {
            ZipEntry e = en.nextElement();
            byte[] data = read(zf.getInputStream(e));
            String n = e.getName();
            if (n.endsWith(".class")) {
                String cls = n.substring(0, n.length() - 6);
                byte[] p = patch(cls, data);
                if (p != null) { data = p; patched++; }
            }
            ZipEntry ne = new ZipEntry(n);
            ne.setTime(e.getTime());
            zo.putNextEntry(ne);
            zo.write(data);
            zo.closeEntry();
        }
        zf.close();
        ZipEntry he = new ZipEntry(H + ".class");
        zo.putNextEntry(he);
        zo.write(read(new FileInputStream(helper)));
        zo.closeEntry();
        for (File f : extra) {
            zo.putNextEntry(new ZipEntry(f.getName()));
            zo.write(read(new FileInputStream(f)));
            zo.closeEntry();
        }
        zo.close();

        byte[] inf = read(new FileInputStream(inInf));
        String s = new String(inf, "ISO-8859-1");
        String key = "UIWindowMovieLoop.class\r\n";
        int i = s.indexOf(key);
        if (i < 0) throw new RuntimeException("inf key missing");
        List<String> names = new ArrayList<>();
        names.add(H);
        names.addAll(optHelpers);
        for (String n : names) {
            if (s.indexOf(n + ".class\r\n") < 0)
                s = s.substring(0, i + key.length()) + n + ".class\r\n" + s.substring(i + key.length());
        }
        FileOutputStream fo = new FileOutputStream(outInf);
        fo.write(s.getBytes("ISO-8859-1"));
        fo.close();
        System.out.println("patched classes: " + patched);
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
        in.close();
        return b.toByteArray();
    }

    static byte[] patch(String cls, byte[] data) {
        boolean ui = cls.equals("UIWindow"), dummy = cls.equals("UIDummy"),
                btn = cls.equals("UIButtonWithBackground"), scale = SCALE_CLASSES.contains(cls),
                video = cls.equals("MenuOptionsVideo"), fonts = cls.equals("XpandRallyFonts");
        boolean fov = hasAscii(data, "SetFOV") && !cls.equals("Camera");
        if (!ui && !dummy && !btn && !scale && !video && !fonts && !fov && !cls.equals("TrackModule")
                && !(optHelpers.contains(RAIN) && (cls.equals("CarFX") || cls.equals("Weather")))
                && !(optHelpers.contains(LOOK) && cls.equals("CarCamera$InsideState"))) return null;
        ClassNode cn = new ClassNode();
        new ClassReader(data).accept(cn, 0);
        int changes = 0;
        for (Object mo : cn.methods) {
            MethodNode m = (MethodNode) mo;
            String sig = m.name + m.desc;
            if (ui) {
                switch (sig) {
                    case "OnCreate()V": delegate(m, "OnCreate"); changes++; break;
                    case "CalculatePos()LVector;": delegate(m, "CalculatePos"); changes++; break;
                    case "CalculateSize()LVector;": delegate(m, "CalculateSize"); changes++; break;
                    case "PosRealToLogic(LVector;)LVector;": delegate(m, "PosRealToLogic"); changes++; break;
                    case "GetRealX()F": delegate(m, "GetRealX"); changes++; break;
                    case "UpdateLogicSizeAndPos(LVector;LVector;)V": delegate(m, "UpdateLogicSizeAndPos"); changes++; break;
                    case "GetResCoeffX()F": delegate(m, "GetResCoeffX"); changes++; break;
                    case "GetResCoeffY()F": delegate(m, "GetResCoeffY"); changes++; break;
                    case "SetRealSize(LVector;)V": changes += dimX(m, Opcodes.FDIV, 1, "RealToLogicSizeX"); break;
                    case "GetRealSize()LVector;": changes += dimX(m, Opcodes.FMUL, 1, "LogicToRealSizeX"); break;
                }
            }
            if (dummy && sig.equals("SetRealSize(LVector;)V"))
                changes += dimX(m, Opcodes.FDIV, 1, "RealToLogicSizeX");
            if (btn && sig.equals("OnSizeAndPos()V")) {
                changes += dimX(m, Opcodes.FMUL, 2, "LogicToRealPosX", "LogicToRealSizeX");
                changes += redirectWidth(m, false);
            }
            if (scale) changes += redirectWidth(m, true);
            if (fonts) changes += redirectWidth(m, false);
            if (fov) changes += redirectFov(m);
            if (cls.equals("TrackModule") && sig.equals("UpdateViewRanges()V")) {
                int v = redirectView(m);
                if (v != 3) throw new RuntimeException("UpdateViewRanges: expected 3 setters, found " + v);
                changes += v;
            }
            if (optHelpers.contains(RAIN) && cls.equals("CarFX") && sig.equals("UpdateOnFrontGlassFXParams()V")) {
                int v = redirectGlassVel(m);
                if (v != 1) throw new RuntimeException("UpdateOnFrontGlassFXParams: expected 1 velocity call, found " + v);
                changes += v;
            }
            if (optHelpers.contains(LOOK) && cls.equals("CarCamera$InsideState") && sig.equals("OnPostUpdate()V")) {
                int v = 0;
                for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (n.getOpcode() != Opcodes.GETFIELD) continue;
                    FieldInsnNode f = (FieldInsnNode) n;
                    if (!f.owner.equals("CarCamera") || !f.name.equals("m_fAngleVCabinV")) continue;
                    InsnList il = new InsnList();
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new FieldInsnNode(Opcodes.GETFIELD, cls, "this$0", "LCarCamera;"));
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LOOK, "CabinLookYaw",
                        "(FLCarCamera$InsideState;LCarCamera;)F", false));
                    m.instructions.insert(n, il);
                    v++;
                }
                if (v != 1) throw new RuntimeException("InsideState.OnPostUpdate: expected 1 yaw read, found " + v);
                changes += v;
            }
            if (optHelpers.contains(RAIN) && cls.equals("Weather") && sig.equals("OnFrame()V")) {
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RAIN, "WeatherFrame", "(LWeather;)V", false));
                m.instructions.insert(il);
                changes++;
            }
            if (video && sig.equals("IsModeAvailable(Ljava/lang/String;)Z")) {
                delegate(m, "IsModeAvailable");
                changes++;
            }
            if (video && sig.equals("FillResolution()V")) {
                delegate(m, "FillResolution");
                changes++;
            }
            if (video && DUMP_VIDEO && (sig.equals("Show()V") || sig.equals("UpdateButtons()V"))) {
                for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (n.getOpcode() != Opcodes.RETURN) continue;
                    m.instructions.insertBefore(n, new VarInsnNode(Opcodes.ALOAD, 0));
                    m.instructions.insertBefore(n, new LdcInsnNode(cls + "." + m.name));
                    m.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, H, "DumpTreeOnce",
                        "(LUIWindow;Ljava/lang/String;)V", false));
                }
                changes++;
            }
            if (video && GUARD_VIDEO && m.desc.endsWith(")V") && !m.name.startsWith("<")
                    && (m.access & Opcodes.ACC_ABSTRACT) == 0 && (m.access & Opcodes.ACC_NATIVE) == 0) {
                guard(m, cls + "." + m.name);
                changes++;
            }
        }
        if (changes == 0) return null;
        System.out.println(cls + ": " + changes + " change(s)");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    static void delegate(MethodNode m, String helperName) {
        Type[] args = Type.getArgumentTypes(m.desc);
        Type ret = Type.getReturnType(m.desc);
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        StringBuilder d = new StringBuilder("(LUIWindow;");
        int slot = 1;
        for (Type t : args) {
            il.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), slot));
            slot += t.getSize();
            d.append(t.getDescriptor());
        }
        d.append(')').append(ret.getDescriptor());
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, helperName, d.toString(), false));
        il.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
        m.instructions.clear();
        m.tryCatchBlocks.clear();
        if (m.localVariables != null) m.localVariables.clear();
        m.instructions.add(il);
    }

    static final boolean GUARD_VIDEO = false;
    static final boolean DUMP_VIDEO = false;

    static void guard(MethodNode m, String where) {
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        m.instructions.insert(start);
        m.instructions.add(end);
        m.instructions.add(handler);
        m.instructions.add(new LdcInsnNode(where));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "LogError",
            "(Ljava/lang/Throwable;Ljava/lang/String;)V", false));
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
    }

    static AbstractInsnNode nextReal(AbstractInsnNode n) {
        n = n.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }

    static AbstractInsnNode prevReal(AbstractInsnNode n) {
        n = n.getPrevious();
        while (n != null && n.getOpcode() < 0) n = n.getPrevious();
        return n;
    }

    static int dimX(MethodNode m, int op, int expected, String... helpers) {
        List<AbstractInsnNode> hits = new ArrayList<>();
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n.getOpcode() == Opcodes.GETSTATIC) {
                FieldInsnNode f = (FieldInsnNode) n;
                if (f.name.equals("m_fDimX") && f.desc.equals("F")) {
                    AbstractInsnNode nx = nextReal(n);
                    if (nx != null && nx.getOpcode() == op) hits.add(n);
                }
            }
        }
        if (hits.size() != expected)
            throw new RuntimeException(m.name + ": expected " + expected + " m_fDimX ops, found " + hits.size());
        for (int i = 0; i < hits.size(); i++) {
            AbstractInsnNode g = hits.get(i), o = nextReal(g);
            m.instructions.insertBefore(g, new VarInsnNode(Opcodes.ALOAD, 0));
            m.instructions.insertBefore(g, new MethodInsnNode(Opcodes.INVOKESTATIC, H,
                helpers[Math.min(i, helpers.length - 1)], "(FLUIWindow;)F", false));
            m.instructions.remove(o);
            m.instructions.remove(g);
        }
        return hits.size();
    }

    static final Set<String> CAMERA_CLASSES = new HashSet<>(Arrays.asList(
        "Camera", "RallyGameCamera", "CarCamera", "MenuCamera", "FreeCamera"));

    static boolean hasAscii(byte[] data, String s) {
        byte[] p = s.getBytes();
        outer:
        for (int i = 0; i + p.length <= data.length; i++) {
            for (int j = 0; j < p.length; j++)
                if (data[i + j] != p[j]) continue outer;
            return true;
        }
        return false;
    }

    static int redirectFov(MethodNode m) {
        int c = 0;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
            MethodInsnNode mi = (MethodInsnNode) n;
            if (!mi.name.equals("SetFOV") || !mi.desc.equals("(F)V") || !CAMERA_CLASSES.contains(mi.owner)) continue;
            m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, H, "SetFOV", "(LCamera;F)V", false));
            c++;
        }
        return c;
    }

    static int redirectView(MethodNode m) {
        int c = 0;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
            MethodInsnNode mi = (MethodInsnNode) n;
            String d;
            if (mi.name.equals("SetObjectsVisibilityRange") && mi.desc.equals("(FF)V")) d = "(LGameObject;FF)V";
            else if (mi.name.equals("SetTreesVisibilityRange") && mi.desc.equals("(FF)V")) d = "(LModule;FF)V";
            else if (mi.name.equals("SetGrassVisMul") && mi.desc.equals("(F)V")) d = "(LModule;F)V";
            else continue;
            m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, H, mi.name, d, false));
            c++;
        }
        return c;
    }

    static int redirectGlassVel(MethodNode m) {
        int c = 0;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
            MethodInsnNode mi = (MethodInsnNode) n;
            if (!mi.name.equals("FXGlassMarksVelocity") || !mi.desc.equals("(IF)Z")) continue;
            m.instructions.set(mi, new MethodInsnNode(Opcodes.INVOKESTATIC, RAIN, "GlassMarksVelocity", "(LCarFX;IF)Z", false));
            c++;
        }
        return c;
    }

    static boolean is800(AbstractInsnNode n) {
        return n instanceof LdcInsnNode && Float.valueOf(800.0f).equals(((LdcInsnNode) n).cst);
    }

    static int redirectWidth(MethodNode m, boolean need800) {
        int c = 0;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n.getOpcode() != Opcodes.INVOKESTATIC) continue;
            MethodInsnNode mi = (MethodInsnNode) n;
            if (!mi.name.equals("GetScreenWidth") || !mi.desc.equals("()I")) continue;
            if (need800) {
                boolean near = false;
                AbstractInsnNode p = n, q = n;
                for (int k = 0; k < 4 && !near; k++) {
                    p = p == null ? null : prevReal(p);
                    q = q == null ? null : nextReal(q);
                    near = (p != null && is800(p)) || (q != null && is800(q));
                }
                if (!near) continue;
            }
            mi.owner = H;
            mi.name = "GetUIWidth";
            c++;
        }
        return c;
    }
}
