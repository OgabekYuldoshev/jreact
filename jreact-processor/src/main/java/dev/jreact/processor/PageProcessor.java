package dev.jreact.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns {@code @Page} and {@code @Layout} classes into routes: generates {@code dev.jreact.generated.Pages} ({@code routes()}) and
 * {@code dev.jreact.generated.PagesApp} (a ready {@code ClientApp}). The tree comes from the URL paths: a page is nested in the layout
 * with the longest path that is a prefix of its own, so there is no routes file to maintain. Mistakes are compile errors on the class.
 */
@SupportedAnnotationTypes({"dev.jreact.api.Page", "dev.jreact.api.Layout"})
public final class PageProcessor extends AbstractProcessor {

    static final String PACKAGE = "dev.jreact.generated";

    /** One annotated class. {@code segments}: the URL path split ("books", ":id"); empty = the root. */
    private static final class Node {
        final boolean layout;
        final TypeElement type;
        final String path;
        final List<String> segments;
        final TypeElement error;
        final String title;
        final boolean loader, action;
        String id;
        Node parent;                                         // the enclosing layout (null: top level)
        final List<Node> children = new ArrayList<>();

        Node(boolean layout, TypeElement type, String path, List<String> segments, TypeElement error, String title, boolean loader, boolean action) {
            this.title = title; this.layout = layout; this.type = type; this.path = path; this.segments = segments; this.error = error; this.loader = loader; this.action = action;
        }
    }

    private boolean done;

    @Override
    public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (done || annotations.isEmpty()) return false;
        List<Node> nodes = new ArrayList<>();
        boolean failed = false;
        for (TypeElement annotation : annotations) {
            boolean layout = annotation.getQualifiedName().contentEquals("dev.jreact.api.Layout");
            for (Element e : round.getElementsAnnotatedWith(annotation)) {
                Node n = read(e, annotation, layout);
                if (n == null) failed = true; else nodes.add(n);
            }
        }
        done = true;
        if (failed || !check(nodes)) return true;
        try {
            write(nodes);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "cannot write the generated routes: " + e.getMessage());
        }
        return true;
    }

    // ---------------------------------------------------------------- reading

    private Node read(Element e, TypeElement annotation, boolean layout) {
        String what = layout ? "@Layout" : "@Page";
        if (e.getKind() != ElementKind.CLASS || !(e instanceof TypeElement type)) { error(e, what + " goes on a component class"); return null; }
        if (!type.getModifiers().contains(Modifier.PUBLIC)) { error(e, what + " class " + type.getSimpleName() + " must be public (the generated routes are in another package)"); return null; }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) { error(e, what + " class " + type.getSimpleName() + " must not be abstract"); return null; }
        if (type.getNestingKind().isNested() && !type.getModifiers().contains(Modifier.STATIC)) { error(e, what + " class " + type.getSimpleName() + " must be a top level or static nested class"); return null; }
        boolean hasPublicNoArg = ElementFilter.constructorsIn(type.getEnclosedElements()).stream().anyMatch(c -> c.getModifiers().contains(Modifier.PUBLIC) && c.getParameters().isEmpty());
        if (!hasPublicNoArg) { error(e, what + " class " + type.getSimpleName() + " needs a public constructor without parameters"); return null; }
        if (!isEmptyPropsComponent(type, e, what)) return null;

        String path = null;
        TypeElement error = null;
        String title = "";
        for (AnnotationMirror m : type.getAnnotationMirrors()) {
            if (!((TypeElement) m.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotation.getQualifiedName())) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> v : processingEnv.getElementUtils().getElementValuesWithDefaults(m).entrySet()) {
                String name = v.getKey().getSimpleName().toString();
                if (name.equals("value")) path = (String) v.getValue().getValue();
                if (name.equals("title")) title = (String) v.getValue().getValue();
                if (name.equals("error") && v.getValue().getValue() instanceof TypeMirror t && t.getKind() == TypeKind.DECLARED) {
                    TypeElement te = (TypeElement) ((DeclaredType) t).asElement();
                    if (!te.getQualifiedName().contentEquals("java.lang.Void")) error = te;
                }
            }
        }
        List<String> segments = segments(path, e, what);
        if (segments == null) return null;
        if (error != null && !isEmptyPropsComponent(error, e, "the error page of " + type.getSimpleName() + " (" + what + ")")) return null;
        boolean loader = hasStatic(type, "loader", e), action = hasStatic(type, "action", e);
        return new Node(layout, type, "/" + String.join("/", segments), segments, error, title, loader, action);
    }

    /** True when {@code type} has {@code public static X name(DataArgs)}; a method of that name with another shape is an error on the class (reported as an error, which fails the build). */
    private boolean hasStatic(TypeElement type, String name, Element at) {
        for (ExecutableElement m : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (!m.getSimpleName().contentEquals(name)) continue;
            boolean ok = m.getModifiers().contains(Modifier.STATIC) && m.getModifiers().contains(Modifier.PUBLIC)
                    && m.getParameters().size() == 1 && m.getParameters().get(0).asType().toString().equals("jreact.router.DataArgs");
            if (ok) return true;
            error(m, name + " of " + type.getSimpleName() + " must be `public static Object " + name + "(DataArgs a)` (it may return a Promise)");
        }
        return false;
    }

    /** The component's props type must be an empty record: pages and layouts get their data from loaders. */
    private boolean isEmptyPropsComponent(TypeElement type, Element at, String what) {
        TypeMirror sup = type.getSuperclass();
        while (sup.getKind() == TypeKind.DECLARED) {
            DeclaredType d = (DeclaredType) sup;
            TypeElement te = (TypeElement) d.asElement();
            if (te.getQualifiedName().contentEquals("jreact.Component")) {
                if (d.getTypeArguments().size() != 1 || d.getTypeArguments().get(0).getKind() != TypeKind.DECLARED) break;
                TypeElement props = (TypeElement) ((DeclaredType) d.getTypeArguments().get(0)).asElement();
                if (props.getKind() != ElementKind.RECORD || !props.getRecordComponents().isEmpty()) {
                    error(at, what + ": " + type.getSimpleName() + " must have an empty `record Props()` (a page gets its data from a loader, not from props)");
                    return false;
                }
                return true;
            }
            sup = te.getSuperclass();
        }
        error(at, what + ": " + type.getSimpleName() + " must extend jreact.Component<Props> with `public record Props() {}`");
        return false;
    }

    /** "/books/{id}" -> [books, :id]; null (and an error) for a bad path. */
    private List<String> segments(String path, Element at, String what) {
        if (path == null || !path.startsWith("/")) { error(at, what + " path must start with / (got \"" + path + "\")"); return null; }
        List<String> out = new ArrayList<>();
        String[] parts = path.substring(1).split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) {
                if (parts.length == 1) break;                                       // "/" is the root
                if (i == parts.length - 1) { error(at, what + " path \"" + path + "\" must not end with /"); return null; }
                error(at, what + " path \"" + path + "\" has an empty segment"); return null;
            }
            if (p.startsWith("{") && p.endsWith("}") && p.length() > 2) p = ":" + p.substring(1, p.length() - 1);
            if (p.equals("*")) {
                if (i != parts.length - 1) { error(at, what + " path \"" + path + "\": * (the rest of the URL) can only be the last segment"); return null; }
            } else if (p.startsWith(":")) {
                if (!p.substring(1).matches("[A-Za-z_][A-Za-z0-9_]*")) { error(at, what + " path \"" + path + "\": bad parameter name \"" + p + "\""); return null; }
            } else if (!p.matches("[A-Za-z0-9._~%@!$&'()+,;=-]+")) {
                error(at, what + " path \"" + path + "\": bad segment \"" + p + "\""); return null;
            }
            out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- checks and tree

    private boolean check(List<Node> nodes) {
        nodes.sort(Comparator.<Node, String>comparing(n -> n.path).thenComparing(n -> n.type.getQualifiedName().toString()));
        boolean ok = true;
        Map<String, Node> layouts = new HashMap<>(), pages = new HashMap<>(), ids = new HashMap<>();
        for (Node n : nodes) {
            Map<String, Node> same = n.layout ? layouts : pages;
            String shape = shape(n.segments);              // /books/:id and /books/:bookId are the same page
            Node other = same.put(shape, n);
            if (other != null) {
                error(n.type, (n.layout ? "two layouts" : "two pages") + " for " + n.path + ": " + other.type.getSimpleName() + " and " + n.type.getSimpleName() + (n.layout ? "" : " (the path may differ only in parameter names)"));
                ok = false;
            }
            n.id = n.type.getSimpleName().toString();
            Node same2 = ids.put(n.id, n);
            if (same2 != null) {
                error(n.type, "two classes are called " + n.id + " (" + same2.type.getQualifiedName() + " and " + n.type.getQualifiedName() + "): route ids come from the class name, rename one");
                ok = false;
            }
        }
        if (!ok) return false;
        // the enclosing layout: the longest layout path that is a prefix (a page may equal it: then it is that layout's index page)
        for (Node n : nodes) {
            Node best = null;
            for (Node l : nodes) {
                if (!l.layout || l == n) continue;
                if (l.segments.size() > n.segments.size() || (n.layout && l.segments.size() == n.segments.size())) continue;
                if (!n.segments.subList(0, l.segments.size()).equals(l.segments)) continue;
                if (best == null || l.segments.size() > best.segments.size()) best = l;
            }
            n.parent = best;
            if (best != null) best.children.add(n);
        }
        // a catch-all page needs nothing else; a layout's own path must not hold a wildcard (it could not have children)
        for (Node n : nodes) {
            if (n.layout && !n.segments.isEmpty() && n.segments.get(n.segments.size() - 1).equals("*")) {
                error(n.type, "a layout path cannot end with * (a layout needs children)");
                ok = false;
            }
        }
        return ok;
    }

    private static String shape(List<String> segments) {
        StringBuilder b = new StringBuilder();
        for (String s : segments) b.append('/').append(s.startsWith(":") ? ":" : s);
        return b.toString();
    }

    // ---------------------------------------------------------------- generation

    private void write(List<Node> nodes) throws IOException {
        List<Node> top = new ArrayList<>();
        for (Node n : nodes) if (n.parent == null) top.add(n);
        Node rootLayout = null;                      // @Layout("/") wraps everything, also pages that were found as top level
        for (Node n : top) if (n.layout && n.segments.isEmpty()) rootLayout = n;

        StringBuilder routes = new StringBuilder();
        List<String> items = new ArrayList<>();
        for (Node n : top) items.add(route(n, 0, 3));
        routes.append(String.join(",\n", items));

        List<Element> origin = new ArrayList<>();
        for (Node n : nodes) origin.add(n.type);
        JavaFileObject file = processingEnv.getFiler().createSourceFile(PACKAGE + ".Pages", origin.toArray(new Element[0]));
        try (Writer w = file.openWriter()) {
            w.write("package " + PACKAGE + ";\n\n"
                    + "import jreact.Jsx;\nimport jreact.router.RouteObject;\n\n"
                    + "/** Generated from the @Page and @Layout classes (jreact-processor): do not edit. */\n"
                    + "public final class Pages {\n    private Pages() {}\n\n"
                    + "    /** The routes of the app: pass them to the router, or use {@link PagesApp}. */\n"
                    + "    public static java.util.List<RouteObject> routes() {\n        return java.util.List.of(\n" + routes + ");\n    }\n}\n");
        }
        JavaFileObject app = processingEnv.getFiler().createSourceFile(PACKAGE + ".PagesApp", origin.toArray(new Element[0]));
        try (Writer w = app.openWriter()) {
            w.write("package " + PACKAGE + ";\n\n"
                    + "/** The app made of the @Page and @Layout classes, ready for {@code createBrowserRouter} and for {@code jreact.ssr.app}. Generated: do not edit. */\n"
                    + "public final class PagesApp implements dev.jreact.api.ClientApp {\n"
                    + "    @Override public java.util.List<jreact.router.RouteObject> routes() { return Pages.routes(); }\n}\n");
        }
    }

    /** The RouteObject expression of a node and, below it, its children. {@code depth}: how many path segments the parent layout covers. */
    private String route(Node n, int parentDepth, int indent) {
        String pad = "    ".repeat(indent);
        List<String> rel = n.segments.subList(parentDepth, n.segments.size());
        StringBuilder b = new StringBuilder(pad);
        if (!n.layout && rel.isEmpty()) b.append("RouteObject.indexRoute()");
        else if (n.layout && n.segments.isEmpty()) b.append("RouteObject.of(\"/\")");
        else b.append("RouteObject.of(\"").append(String.join("/", rel)).append("\")");
        b.append(".id(\"").append(n.id).append("\")");
        // a static title wraps the page in a HeadScope (the page's own useHead is deeper, so it wins)
        String element = component(n.type);
        if (n.title != null && !n.title.isEmpty()) {
            element = "Jsx.c(jreact.HeadScope.class, jreact.HeadScope::new, null, new jreact.HeadScope.Props(jreact.Head.title(\"" + javaString(n.title) + "\"), " + element + "))";
        }
        b.append(".element(").append(element).append(")");
        // a page's error replaces the page (the layouts around it stay); a layout's error is a boundary INSIDE the layout: its header stays, the children are replaced
        if (n.error != null && !n.layout) b.append(".errorElement(").append(component(n.error)).append(")");
        if (n.loader) b.append(".loader(").append(n.type.getQualifiedName()).append("::loader)");
        if (n.action) b.append(".action(").append(n.type.getQualifiedName()).append("::action)");
        if (!n.children.isEmpty()) {
            List<String> kids = new ArrayList<>();
            boolean boundary = n.layout && n.error != null;
            for (Node c : n.children) kids.add(route(c, n.segments.size(), indent + (boundary ? 2 : 1)));
            if (boundary) {
                String inner = "    ".repeat(indent + 1);
                b.append(".children(\n").append(inner).append("RouteObject.layout().errorElement(").append(component(n.error)).append(").children(\n")
                        .append(String.join(",\n", kids)).append("))");
            } else {
                b.append(".children(\n").append(String.join(",\n", kids)).append(")");
            }
        }
        return b.toString();
    }

    private static String javaString(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    private static String component(TypeElement type) {
        String name = type.getQualifiedName().toString();
        return "Jsx.c(" + name + ".class, " + name + "::new, null, new " + name + ".Props())";
    }

    private void error(Element at, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, at);
    }
}
