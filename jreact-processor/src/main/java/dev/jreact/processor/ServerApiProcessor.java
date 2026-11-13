package dev.jreact.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * For every {@code @ServerApi} interface {@code N} generates, next to it:
 * <ul>
 *   <li>{@code NClient}: static methods that return {@code Promise<T>} and call the server (browser side)</li>
 *   <li>{@code NDispatcher}: turns a JSON call into a call of the real method (server side)</li>
 *   <li>{@code NWire}: the JSON conversion of every type that appears in the methods (records, lists, maps, enums, ...)</li>
 * </ul>
 * plus {@code META-INF/jreact/rpc-dispatchers}, the list the Spring Boot starter reads. No reflection anywhere: the generated code runs in the browser too.
 */
@SupportedAnnotationTypes("dev.jreact.api.ServerApi")
public final class ServerApiProcessor extends AbstractProcessor {

    private static final String LIST = "java.util.List";
    private static final String MAP = "java.util.Map";
    private static final String OPTIONAL = "java.util.Optional";

    private final Set<String> dispatchers = new TreeSet<>();
    private final Map<String, String> apiNames = new HashMap<>();   // simple name -> qualified name (they must be unique: the URL uses the simple name)

    @Override
    public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }

    @Override
    public boolean process(java.util.Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (TypeElement annotation : annotations) {
            for (Element e : round.getElementsAnnotatedWith(annotation)) {
                if (e.getKind() != ElementKind.INTERFACE) { error(e, "@ServerApi goes on an interface"); continue; }
                try {
                    generate((TypeElement) e);
                } catch (Unsupported u) {
                    error(u.at != null ? u.at : e, u.getMessage());
                } catch (IOException io) {
                    error(e, "cannot write generated code: " + io.getMessage());
                }
            }
        }
        if (round.processingOver() && !dispatchers.isEmpty()) writeServiceFile();
        return true;
    }

    private void error(Element at, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, at);
    }

    private void writeServiceFile() {
        try {
            FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/jreact/rpc-dispatchers");
            try (Writer w = f.openWriter()) {
                for (String d : dispatchers) w.write(d + "\n");
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "cannot write META-INF/jreact/rpc-dispatchers: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- one interface

    private void generate(TypeElement api) throws IOException {
        String name = api.getSimpleName().toString();
        String qualified = api.getQualifiedName().toString();
        String pkg = processingEnv.getElementUtils().getPackageOf(api).getQualifiedName().toString();
        if (!api.getModifiers().contains(Modifier.PUBLIC)) throw new Unsupported(api, "the @ServerApi interface " + name + " must be public");
        if (!api.getTypeParameters().isEmpty()) throw new Unsupported(api, "a @ServerApi interface cannot have type parameters");
        String previous = apiNames.put(name, qualified);
        if (previous != null && !previous.equals(qualified)) throw new Unsupported(api, "two @ServerApi interfaces are called " + name + " (" + previous + ", " + qualified + "): the URL uses the simple name, rename one");

        List<ExecutableElement> methods = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ExecutableElement m : ElementFilter.methodsIn(api.getEnclosedElements())) {
            if (m.getModifiers().contains(Modifier.STATIC) || m.getModifiers().contains(Modifier.DEFAULT) || m.getModifiers().contains(Modifier.PRIVATE)) continue;
            if (!m.getTypeParameters().isEmpty()) throw new Unsupported(m, "generic methods cannot be called remotely");
            if (!seen.add(m.getSimpleName().toString())) throw new Unsupported(m, "overloaded method " + m.getSimpleName() + ": remote methods need distinct names (the URL is made of the name)");
            methods.add(m);
        }

        Wire wire = new Wire();
        StringBuilder client = new StringBuilder();
        StringBuilder dispatch = new StringBuilder();
        for (ExecutableElement m : methods) {
            String method = m.getSimpleName().toString();
            TypeMirror ret = m.getReturnType();
            boolean isVoid = ret.getKind() == TypeKind.VOID;
            String retType = isVoid ? "Void" : boxed(ret);
            String retDec = isVoid ? null : wire.dec(ret, m);

            // client
            List<String> params = new ArrayList<>();
            StringBuilder body = new StringBuilder();
            for (VariableElement p : m.getParameters()) {
                params.add(p.asType() + " " + p.getSimpleName());
                body.append("        __args.add(").append(api.getSimpleName()).append("Wire.").append(wire.enc(p.asType(), p)).append("(").append(p.getSimpleName()).append("));\n");
            }
            client.append("    public static jreact.router.Promise<").append(retType).append("> ").append(method).append("(").append(String.join(", ", params)).append(") {\n")
                    .append("        java.util.List<Object> __args = dev.jreact.api.Wire.newArray();\n").append(body)
                    .append("        return dev.jreact.api.Rpc.call(\"").append(name).append("\", \"").append(method).append("\", __args).then(__r -> ")
                    .append(isVoid ? "(Void) null" : name + "Wire." + retDec + "(__r, \"result\")").append(");\n    }\n\n");

            // dispatcher
            StringBuilder call = new StringBuilder();
            int i = 0;
            for (VariableElement p : m.getParameters()) {
                if (i > 0) call.append(", ");
                call.append(name).append("Wire.").append(wire.dec(p.asType(), p)).append("(args.get(").append(i).append("), \"").append(p.getSimpleName()).append("\")");
                i++;
            }
            dispatch.append("            case \"").append(method).append("\": {\n")
                    .append("                dev.jreact.api.Wire.args(args, ").append(m.getParameters().size()).append(");\n");
            if (isVoid) dispatch.append("                api.").append(method).append("(").append(call).append(");\n                return null;\n");
            else dispatch.append("                return ").append(name).append("Wire.").append(wire.enc(ret, m)).append("(api.").append(method).append("(").append(call).append("));\n");
            dispatch.append("            }\n");
        }

        String header = "// Generated by jreact-processor from " + qualified + ". Do not edit.\n" + (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n");
        write(pkg, name + "Wire", api, header + "final class " + name + "Wire {\n    private " + name + "Wire() {}\n\n" + wire.code + "}\n");
        write(pkg, name + "Client", api, header
                + "/** Calls {@link " + qualified + "} on the server. Every method returns a Promise; failures arrive as jreact.router.ErrorResponse. */\n"
                + "public final class " + name + "Client {\n    private " + name + "Client() {}\n\n" + client + "}\n");
        write(pkg, name + "Dispatcher", api, header
                + "/** Calls the methods of {@link " + qualified + "} for requests that arrive as JSON. Used by jreact-spring-boot-starter. */\n"
                + "public final class " + name + "Dispatcher implements dev.jreact.api.RpcDispatcher {\n"
                + "    @Override public String name() { return \"" + name + "\"; }\n"
                + "    @Override public Class<?> api() { return " + qualified + ".class; }\n\n"
                + "    @Override\n    public Object invoke(Object impl, String method, java.util.List<Object> args) {\n"
                + "        " + qualified + " api = (" + qualified + ") impl;\n"
                + "        switch (method) {\n" + dispatch
                + "            default: throw new dev.jreact.api.RpcException(404, \"no method \" + method + \" in " + name + "\");\n        }\n    }\n}\n");
        dispatchers.add((pkg.isEmpty() ? "" : pkg + ".") + name + "Dispatcher");
    }

    private void write(String pkg, String simple, Element origin, String source) throws IOException {
        JavaFileObject f = processingEnv.getFiler().createSourceFile((pkg.isEmpty() ? "" : pkg + ".") + simple, origin);
        try (Writer w = f.openWriter()) { w.write(source); }
    }

    private static String boxed(TypeMirror t) {
        return switch (t.getKind()) {
            case INT -> "Integer";
            case LONG -> "Long";
            case DOUBLE -> "Double";
            case FLOAT -> "Float";
            case SHORT -> "Short";
            case BYTE -> "Byte";
            case BOOLEAN -> "Boolean";
            default -> t.toString();
        };
    }

    static final class Unsupported extends RuntimeException {
        final Element at;
        Unsupported(Element at, String message) { super(message); this.at = at; }
    }

    // ---------------------------------------------------------------- JSON conversion of one type, as static helper methods

    /** Collects {@code enc_N} / {@code dec_N} helpers; the same type always gets the same helper (and recursive records terminate). */
    private final class Wire {
        final StringBuilder code = new StringBuilder();
        private final Map<String, String> encoders = new LinkedHashMap<>();
        private final Map<String, String> decoders = new LinkedHashMap<>();

        String enc(TypeMirror t, Element at) {
            String key = t.toString();
            String known = encoders.get(key);
            if (known != null) return known;
            String name = "enc" + encoders.size();
            encoders.put(key, name);
            String type = typeName(t);
            StringBuilder b = new StringBuilder("    static Object " + name + "(" + type + " v) {\n");
            switch (t.getKind()) {
                case INT, LONG, DOUBLE, FLOAT, SHORT, BYTE, BOOLEAN -> b.append("        return v;\n");
                case DECLARED -> {
                    DeclaredType d = (DeclaredType) t;
                    TypeElement el = (TypeElement) d.asElement();
                    String q = el.getQualifiedName().toString();
                    if (isBox(q) || q.equals("java.lang.String")) b.append("        return v;\n");
                    else if (el.getKind() == ElementKind.ENUM) b.append("        return v == null ? null : v.name();\n");
                    else if (isTime(q)) b.append("        return v == null ? null : v.toString();\n");        // ISO-8601
                    else if (q.equals(OPTIONAL)) {
                        String item = enc(arg(d, 0, at), at);
                        b.append("        if (v == null || v.isEmpty()) return null;\n        return ").append(item).append("(v.get());\n");
                    } else if (q.equals(LIST)) {
                        String item = enc(arg(d, 0, at), at);
                        b.append("        if (v == null) return null;\n        java.util.List<Object> out = dev.jreact.api.Wire.newArray();\n")
                                .append("        for (").append(typeName(arg(d, 0, at))).append(" e : v) out.add(").append(item).append("(e));\n        return out;\n");
                    } else if (q.equals(MAP)) {
                        stringKey(d, at);
                        String item = enc(arg(d, 1, at), at);
                        b.append("        if (v == null) return null;\n        java.util.Map<String, Object> out = dev.jreact.api.Wire.newObject();\n")
                                .append("        for (java.util.Map.Entry<String, ").append(typeName(arg(d, 1, at))).append("> e : v.entrySet()) out.put(e.getKey(), ").append(item).append("(e.getValue()));\n        return out;\n");
                    } else if (el.getKind() == ElementKind.RECORD) {
                        checkRecord(el, at);
                        b.append("        if (v == null) return null;\n        java.util.Map<String, Object> out = dev.jreact.api.Wire.newObject();\n");
                        for (RecordComponentElement c : el.getRecordComponents()) {
                            b.append("        out.put(\"").append(c.getSimpleName()).append("\", ").append(enc(c.getAccessor().getReturnType(), at)).append("(v.").append(c.getSimpleName()).append("()));\n");
                        }
                        b.append("        return out;\n");
                    } else throw unsupported(t, at);
                }
                default -> throw unsupported(t, at);
            }
            b.append("    }\n\n");
            code.append(b);
            return name;
        }

        String dec(TypeMirror t, Element at) {
            String key = t.toString();
            String known = decoders.get(key);
            if (known != null) return known;
            String name = "dec" + decoders.size();
            decoders.put(key, name);
            String type = typeName(t);
            StringBuilder b = new StringBuilder("    static " + type + " " + name + "(Object j, String path) {\n");
            switch (t.getKind()) {
                case INT -> b.append("        return dev.jreact.api.Wire.intv(j, path);\n");
                case LONG -> b.append("        return dev.jreact.api.Wire.longv(j, path);\n");
                case DOUBLE -> b.append("        return dev.jreact.api.Wire.doublev(j, path);\n");
                case FLOAT -> b.append("        return (float) dev.jreact.api.Wire.doublev(j, path);\n");
                case SHORT -> b.append("        return (short) dev.jreact.api.Wire.intv(j, path);\n");
                case BYTE -> b.append("        return (byte) dev.jreact.api.Wire.intv(j, path);\n");
                case BOOLEAN -> b.append("        return dev.jreact.api.Wire.bool(j, path);\n");
                case DECLARED -> {
                    DeclaredType d = (DeclaredType) t;
                    TypeElement el = (TypeElement) d.asElement();
                    String q = el.getQualifiedName().toString();
                    switch (q) {
                        case "java.lang.String" -> b.append("        return dev.jreact.api.Wire.str(j, path);\n");
                        case "java.lang.Integer" -> b.append("        return dev.jreact.api.Wire.intOrNull(j, path);\n");
                        case "java.lang.Long" -> b.append("        return dev.jreact.api.Wire.longOrNull(j, path);\n");
                        case "java.lang.Double" -> b.append("        return dev.jreact.api.Wire.doubleOrNull(j, path);\n");
                        case "java.lang.Float" -> b.append("        Double d = dev.jreact.api.Wire.doubleOrNull(j, path);\n        return d == null ? null : (Float) (float) (double) d;\n");
                        case "java.lang.Short" -> b.append("        Integer i = dev.jreact.api.Wire.intOrNull(j, path);\n        return i == null ? null : (Short) (short) (int) i;\n");
                        case "java.lang.Byte" -> b.append("        Integer i = dev.jreact.api.Wire.intOrNull(j, path);\n        return i == null ? null : (Byte) (byte) (int) i;\n");
                        case "java.lang.Boolean" -> b.append("        return dev.jreact.api.Wire.boolOrNull(j, path);\n");
                        case "java.time.LocalDate" -> b.append("        return dev.jreact.api.Wire.localDate(j, path);\n");
                        case "java.time.LocalDateTime" -> b.append("        return dev.jreact.api.Wire.localDateTime(j, path);\n");
                        case "java.time.LocalTime" -> b.append("        return dev.jreact.api.Wire.localTime(j, path);\n");
                        case "java.time.Instant" -> b.append("        return dev.jreact.api.Wire.instant(j, path);\n");
                        case OPTIONAL -> {
                            String item = dec(arg(d, 0, at), at);
                            b.append("        if (j == null) return java.util.Optional.empty();\n        return java.util.Optional.ofNullable(").append(item).append("(j, path));\n");
                        }
                        case LIST -> {
                            String item = dec(arg(d, 0, at), at);
                            b.append("        java.util.List<Object> in = dev.jreact.api.Wire.list(j, path);\n        if (in == null) return null;\n")
                                    .append("        ").append(type).append(" out = new java.util.ArrayList<>();\n")
                                    .append("        for (int i = 0; i < in.size(); i++) out.add(").append(item).append("(in.get(i), path + \"[\" + i + \"]\"));\n        return out;\n");
                        }
                        case MAP -> {
                            stringKey(d, at);
                            String item = dec(arg(d, 1, at), at);
                            b.append("        java.util.Map<String, Object> in = dev.jreact.api.Wire.map(j, path);\n        if (in == null) return null;\n")
                                    .append("        ").append(type).append(" out = new java.util.LinkedHashMap<>();\n")
                                    .append("        for (java.util.Map.Entry<String, Object> e : in.entrySet()) out.put(e.getKey(), ").append(item).append("(e.getValue(), path + \".\" + e.getKey()));\n        return out;\n");
                        }
                        default -> {
                            if (el.getKind() == ElementKind.ENUM) b.append("        return dev.jreact.api.Wire.enumv(").append(q).append(".values(), j, path);\n");
                            else if (el.getKind() == ElementKind.RECORD) {
                                checkRecord(el, at);
                                b.append("        java.util.Map<String, Object> in = dev.jreact.api.Wire.map(j, path);\n        if (in == null) return null;\n        return new ").append(q).append("(");
                                boolean first = true;
                                for (RecordComponentElement c : el.getRecordComponents()) {
                                    if (!first) b.append(", ");
                                    first = false;
                                    b.append(dec(c.getAccessor().getReturnType(), at)).append("(in.get(\"").append(c.getSimpleName()).append("\"), path + \".").append(c.getSimpleName()).append("\")");
                                }
                                b.append(");\n");
                            } else throw unsupported(t, at);
                        }
                    }
                }
                default -> throw unsupported(t, at);
            }
            b.append("    }\n\n");
            code.append(b);
            return name;
        }

        private void checkRecord(TypeElement el, Element at) {
            if (!el.getTypeParameters().isEmpty()) throw new Unsupported(at, "generic records are not supported (" + el.getQualifiedName() + ")");
            if (!el.getModifiers().contains(Modifier.PUBLIC)) throw new Unsupported(at, "the record " + el.getQualifiedName() + " must be public so the generated code can use it");
        }

        private TypeMirror arg(DeclaredType d, int index, Element at) {
            if (d.getTypeArguments().size() <= index) throw new Unsupported(at, "write the type argument of " + d + " (a raw type has no JSON shape)");
            TypeMirror a = d.getTypeArguments().get(index);
            if (a.getKind() == TypeKind.WILDCARD || a.getKind() == TypeKind.TYPEVAR) throw new Unsupported(at, "a wildcard or type variable in " + d + " has no JSON shape: use a concrete type");
            return a;
        }

        private void stringKey(DeclaredType d, Element at) {
            TypeMirror k = arg(d, 0, at);
            if (!k.toString().equals("java.lang.String")) throw new Unsupported(at, "a Map in a @ServerApi needs String keys (JSON objects), found " + d);
        }

        private Unsupported unsupported(TypeMirror t, Element at) {
            return new Unsupported(at, "the type " + t + " cannot be sent over a @ServerApi. Allowed: boolean, int, long, double (and boxes), String, enums, Optional<T>, LocalDate, LocalDateTime, LocalTime, Instant, List<T>, Map<String,T>, records of these");
        }

        private boolean isTime(String q) {
            return q.equals("java.time.LocalDate") || q.equals("java.time.LocalDateTime") || q.equals("java.time.LocalTime") || q.equals("java.time.Instant");
        }

        private boolean isBox(String q) {
            return q.equals("java.lang.Integer") || q.equals("java.lang.Long") || q.equals("java.lang.Double") || q.equals("java.lang.Float")
                    || q.equals("java.lang.Short") || q.equals("java.lang.Byte") || q.equals("java.lang.Boolean");
        }

        private String typeName(TypeMirror t) { return t.toString(); }
    }
}
