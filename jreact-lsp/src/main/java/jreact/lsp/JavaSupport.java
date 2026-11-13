package jreact.lsp;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import jreact.compiler.JsxTranspiler;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Completion and hover for the Java inside a .jrx file ({@code {expressions}} and plain code), by asking javac about
 * the generated Java. {@link JsxTranspiler.Mapped} translates the cursor into the generated source; the answers are
 * names, so they need no translation back.
 */
final class JavaSupport {

    private static final String PLACEHOLDER = "__jrx__";

    private JavaSupport() {}

    private record Attributed(JavacTask task, Trees trees, CompilationUnitTree unit, SourcePositions pos, String java) {}

    /** Parses and attributes {@code java} (the generated source of the file) as the file in {@code in}. */
    private static Attributed attribute(Analyzer.Input in, String text, String java, Path dir, StandardJavaFileManager[] fmOut) throws Exception {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) return null;
        List<String> opts = new ArrayList<>(Analyzer.prepare(in, dir));
        opts.add("-XDshould-stop.ifError=FLOW");
        opts.add("-XDshould-stop.at=FLOW");
        JavaFileObject src = Analyzer.source(in, text, java);
        StandardJavaFileManager fm = jc.getStandardFileManager(new DiagnosticCollector<>(), Locale.ENGLISH, StandardCharsets.UTF_8);
        fmOut[0] = fm;
        JavacTask task = (JavacTask) jc.getTask(null, fm, new DiagnosticCollector<>(), opts, null, List.of(src));
        Iterable<? extends CompilationUnitTree> units = task.parse();
        task.analyze();
        CompilationUnitTree unit = units.iterator().next();
        return new Attributed(task, Trees.instance(task), unit, Trees.instance(task).getSourcePositions(), java);
    }

    /** The deepest tree that contains {@code offset}. */
    private static TreePath pathAt(Attributed a, int offset) {
        TreePath[] best = new TreePath[1];
        new TreePathScanner<Void, Void>() {
            @Override public Void scan(Tree tree, Void v) {
                if (tree == null) return null;
                super.scan(tree, v);
                if (best[0] == null) {
                    long s = a.pos.getStartPosition(a.unit, tree), e = a.pos.getEndPosition(a.unit, tree);
                    if (s >= 0 && s <= offset && offset <= e) {
                        best[0] = tree instanceof CompilationUnitTree cu ? new TreePath(cu) : new TreePath(getCurrentPath(), tree);
                    }
                }
                return null;
            }
        }.scan(a.unit, null);
        return best[0];
    }

    // ------------------------------------------------------------ completion

    /** LSP completion items for the Java at {@code srcOffset}, or null when the cursor is not in Java code. */
    static List<Object> complete(Analyzer.Input in, String text, int srcOffset, Path dir) {
        StandardJavaFileManager[] fm = new StandardJavaFileManager[1];
        try {
            JsxTranspiler.Mapped m = new JsxTranspiler(Analyzer.name(in.uri()), text, in.index()).transpileMapped();
            int g = m.toGenerated(srcOffset);
            if (g < 0) return null;
            String java = m.java();
            int p = g;
            while (p > 0 && Character.isJavaIdentifierPart(java.charAt(p - 1))) p--;
            String prefix = java.substring(p, g);
            int dot = p - 1;
            while (dot >= 0 && Character.isWhitespace(java.charAt(dot))) dot--;
            boolean member = dot >= 0 && java.charAt(dot) == '.';
            int q = g;
            while (q < java.length() && Character.isJavaIdentifierPart(java.charAt(q))) q++;
            // the identifier under the cursor, or a placeholder when there is none, so the tree is a complete expression
            String patched = java.substring(0, p) + (prefix.isEmpty() && q == g ? PLACEHOLDER : java.substring(p, q)) + java.substring(q);
            Attributed a = attribute(in, text, patched, dir, fm);
            if (a == null) return null;
            Elements elements = a.task.getElements();
            List<Object> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (member) {
                TreePath sel = null;
                MemberSelectTreeFinder f = new MemberSelectTreeFinder(a, dot);
                f.scan(a.unit, null);
                sel = f.found;
                if (sel == null) return List.of();
                MemberSelectTree mst = (MemberSelectTree) sel.getLeaf();
                TreePath exprPath = new TreePath(sel, mst.getExpression());
                TypeMirror type = a.trees.getTypeMirror(exprPath);
                Element target = a.trees.getElement(exprPath);
                boolean staticOnly = target != null && (target.getKind().isClass() || target.getKind().isInterface()) && !(target.getKind() == ElementKind.ENUM_CONSTANT);
                var scope = a.trees.getScope(sel);
                if (type == null) return List.of();
                if (type.getKind() == TypeKind.ARRAY && !staticOnly) items.add(item("length", 5, "int", "0length", "length"));
                if (type instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
                    for (Element e : elements.getAllMembers(te)) {
                        if (e.getKind() == ElementKind.CONSTRUCTOR || e.getKind() == ElementKind.STATIC_INIT || e.getKind() == ElementKind.INSTANCE_INIT) continue;
                        if (e.getSimpleName().contentEquals(PLACEHOLDER)) continue;
                        boolean isStatic = e.getModifiers().contains(Modifier.STATIC);
                        if (staticOnly && !isStatic && !e.getKind().isClass() && !e.getKind().isInterface()) continue;
                        if (!staticOnly && isStatic && (e.getKind().isClass() || e.getKind().isInterface())) continue;
                        if (!a.trees.isAccessible(scope, e, dt)) continue;
                        add(items, seen, e, prefix, elements);
                    }
                }
                return items;
            }
            TreePath here = pathAt(a, p);
            if (here == null) return null;
            var scope = a.trees.getScope(here);
            for (var s = scope; s != null; s = s.getEnclosingScope()) {
                for (Element e : s.getLocalElements()) add(items, seen, e, prefix, elements);
                TypeElement cls = s.getEnclosingClass();
                if (cls != null && s.getEnclosingScope() != null && s.getEnclosingScope().getEnclosingClass() != cls) {
                    for (Element e : elements.getAllMembers(cls)) {
                        if (e.getKind() == ElementKind.CONSTRUCTOR || e.getKind() == ElementKind.STATIC_INIT || e.getKind() == ElementKind.INSTANCE_INIT) continue;
                        add(items, seen, e, prefix, elements);
                    }
                }
            }
            return items;
        } catch (Exception | LinkageError e) {
            return null;
        } finally {
            try { if (fm[0] != null) fm[0].close(); } catch (Exception ignored) {}
        }
    }

    /** The {@code a.b} whose dot is at {@code dotOffset} in the generated source. */
    private static final class MemberSelectTreeFinder extends TreePathScanner<Void, Void> {
        final Attributed a;
        final int dot;
        TreePath found;

        MemberSelectTreeFinder(Attributed a, int dot) { this.a = a; this.dot = dot; }

        @Override public Void visitMemberSelect(MemberSelectTree t, Void v) {
            if (found == null) {
                long end = a.pos.getEndPosition(a.unit, t.getExpression());
                int k = (int) end;
                while (k < a.java.length() && Character.isWhitespace(a.java.charAt(k))) k++;
                if (k == dot) found = getCurrentPath();
            }
            return super.visitMemberSelect(t, v);
        }
    }

    private static void add(List<Object> items, Set<String> seen, Element e, String prefix, Elements elements) {
        String name = e.getSimpleName().toString();
        if (name.isEmpty() || name.equals(PLACEHOLDER) || name.startsWith("<") || name.contains("$")) return;
        if (!prefix.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) return;
        int kind;
        String detail;
        switch (e.getKind()) {
            case METHOD -> {
                ExecutableElement x = (ExecutableElement) e;
                kind = 2;
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < x.getParameters().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(x.getParameters().get(i).asType()).append(' ').append(x.getParameters().get(i).getSimpleName());
                }
                detail = sb.append(") -> ").append(x.getReturnType()).toString();
            }
            case FIELD -> { kind = 5; detail = String.valueOf(e.asType()); }
            case ENUM_CONSTANT -> { kind = 20; detail = String.valueOf(e.asType()); }
            case CLASS, RECORD -> { kind = 7; detail = "class"; }
            case INTERFACE, ANNOTATION_TYPE -> { kind = 8; detail = "interface"; }
            case ENUM -> { kind = 13; detail = "enum"; }
            case LOCAL_VARIABLE, PARAMETER, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> { kind = 6; detail = String.valueOf(e.asType()); }
            default -> { return; }
        }
        if (!seen.add(e.getKind() + name + detail)) return;
        boolean local = kind == 6;
        items.add(item(name, kind, detail, (local ? "0" : e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.FIELD ? "1" : "2") + name,
                kind == 2 ? name + "($0)" : name));
    }

    private static Map<String, Object> item(String label, int kind, String detail, String sort, String insert) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("kind", kind);
        m.put("detail", detail);
        m.put("sortText", sort);
        if (!insert.equals(label)) {
            m.put("insertText", insert);
            m.put("insertTextFormat", 2);
        }
        return m;
    }

    // ------------------------------------------------------------ hover

    /** Markdown for the Java element or expression at {@code srcOffset}, or null. */
    static String hover(Analyzer.Input in, String text, int srcOffset, Path dir) {
        StandardJavaFileManager[] fm = new StandardJavaFileManager[1];
        try {
            JsxTranspiler.Mapped m = new JsxTranspiler(Analyzer.name(in.uri()), text, in.index()).transpileMapped();
            int g = m.toGenerated(srcOffset);
            if (g < 0) return null;
            Attributed a = attribute(in, text, m.java(), dir, fm);
            if (a == null) return null;
            TreePath path = pathAt(a, g);
            if (path == null) return null;
            Element e = a.trees.getElement(path);
            TypeMirror type = a.trees.getTypeMirror(path);
            if (e == null) return type == null || type.getKind() == TypeKind.NONE || type.getKind() == TypeKind.ERROR ? null : "```java\n" + type + "\n```";
            String sig = switch (e.getKind()) {
                case METHOD -> {
                    ExecutableElement x = (ExecutableElement) e;
                    StringBuilder sb = new StringBuilder(x.getReturnType() + " " + x.getSimpleName() + "(");
                    for (int i = 0; i < x.getParameters().size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(x.getParameters().get(i).asType()).append(' ').append(x.getParameters().get(i).getSimpleName());
                    }
                    yield sb.append(")").toString();
                }
                case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> e.getKind().toString().toLowerCase(Locale.ROOT).replace("annotation_type", "@interface") + " " + e;
                default -> e.asType() + " " + e.getSimpleName();
            };
            return "```java\n" + sig + "\n```";
        } catch (Exception | LinkageError e) {
            return null;
        } finally {
            try { if (fm[0] != null) fm[0].close(); } catch (Exception ignored) {}
        }
    }
}
