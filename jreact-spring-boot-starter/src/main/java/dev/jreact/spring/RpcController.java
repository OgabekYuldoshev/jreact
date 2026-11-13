package dev.jreact.spring;

import dev.jreact.api.RpcDispatcher;
import dev.jreact.api.RpcException;
import jreact.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code POST /_rpc/<Api>/<method>} with a JSON array of arguments: calls the Spring bean that implements the {@code @ServerApi} interface.
 * The generated dispatchers (listed in {@code META-INF/jreact/rpc-dispatchers}) do the JSON conversion and type checks; this class finds the
 * bean and turns exceptions into HTTP statuses. Only {@code application/json} is accepted, which a cross-site HTML form cannot send.
 * Security stays with Spring: Spring Security's filter chain, CSRF and {@code @PreAuthorize} on the bean all apply as for any controller.
 */
@RestController
class RpcController {

    private static final Logger log = LoggerFactory.getLogger(RpcController.class);

    /** Bean Validation (jakarta.validation) is optional: its classes are only touched when it is on the classpath. */
    private static final boolean BEAN_VALIDATION = org.springframework.util.ClassUtils.isPresent("jakarta.validation.ConstraintViolationException", RpcController.class.getClassLoader());

    private static final boolean SPRING_SECURITY = org.springframework.util.ClassUtils.isPresent("org.springframework.security.core.context.SecurityContextHolder", RpcController.class.getClassLoader());

    private record Target(RpcDispatcher dispatcher, Object bean) {}

    private final Map<String, Target> targets = new LinkedHashMap<>();

    RpcController(ApplicationContext context) throws IOException {
        ClassLoader loader = context.getClassLoader() != null ? context.getClassLoader() : RpcController.class.getClassLoader();
        for (Enumeration<URL> files = loader.getResources("META-INF/jreact/rpc-dispatchers"); files.hasMoreElements(); ) {
            URL file = files.nextElement();
            try (var in = file.openStream()) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    String className = line.strip();
                    if (className.isEmpty() || targets.values().stream().anyMatch(t -> t.dispatcher().getClass().getName().equals(className))) continue;
                    RpcDispatcher dispatcher = instantiate(loader, className);
                    Map<String, ?> beans = context.getBeansOfType(dispatcher.api());
                    if (beans.isEmpty()) throw new IllegalStateException("no Spring bean implements " + dispatcher.api().getName() + " (a @ServerApi interface): add a @Service that implements it, "
                            + "or remove the interface from the classpath of the server");
                    if (beans.size() > 1) throw new IllegalStateException("more than one Spring bean implements " + dispatcher.api().getName() + ": " + beans.keySet() + ". A @ServerApi needs exactly one");
                    Target previous = targets.put(dispatcher.name(), new Target(dispatcher, beans.values().iterator().next()));
                    if (previous != null) throw new IllegalStateException("two @ServerApi interfaces are called " + dispatcher.name() + ": the URL uses the simple name");
                }
            }
        }
        if (!targets.isEmpty()) log.info("jreact rpc: {}", targets.keySet().stream().map(n -> "/_rpc/" + n + "/*").toList());
    }

    private static RpcDispatcher instantiate(ClassLoader loader, String className) {
        try {
            return (RpcDispatcher) loader.loadClass(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot load the generated dispatcher " + className + ": " + e, e);
        }
    }

    /** The outcome of one call: an HTTP status and the JSON text of the answer (or {@code {"message": ...}} for an error). */
    record Result(int status, String json) {
        boolean ok() { return status == 200; }
    }

    @PostMapping(value = "/_rpc/{api}/{method}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> call(@PathVariable("api") String api, @PathVariable("method") String method, @RequestBody String body) {     // names spelled out: no -parameters needed
        Object args;
        try {
            args = Json.parse(body);
        } catch (Json.ParseException e) {
            return respond(error(400, "the body is not valid JSON: " + e.getMessage()));
        }
        if (!(args instanceof List<?>)) return respond(error(400, "the body must be a JSON array of the arguments"));
        @SuppressWarnings("unchecked") Result r = invoke(api, method, (List<Object>) args);
        return respond(r);
    }

    /** Calls the bean; also used by server side rendering, so a loader sees exactly what the browser would get over HTTP. */
    Result invoke(String api, String method, List<Object> args) {
        Target target = targets.get(api);
        if (target == null) return error(404, "no such api: " + api);
        try {
            Object result = target.dispatcher().invoke(target.bean(), method, args);
            return new Result(200, Json.stringify(result));
        } catch (RpcException e) {
            return error(e.status(), e.getMessage(), e.fields());
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode().value(), e.getReason() != null ? e.getReason() : HttpStatus.valueOf(e.getStatusCode().value()).getReasonPhrase());
        } catch (RuntimeException e) {
            if (BEAN_VALIDATION && ConstraintViolations.isViolation(e)) {
                RpcException refused = ConstraintViolations.toRpcException(e);
                return error(refused.status(), refused.getMessage(), refused.fields());
            }
            HttpStatusCode security = securityStatus(e);
            if (security != null) {
                // @PreAuthorize throws AccessDeniedException also for nobody-signed-in: that is a 401 (the page should ask for a login), not a 403
                int code = security.value() == 403 && SPRING_SECURITY && AuthStatus.deniedAnonymous(e) ? 401 : security.value();
                return error(code, code == 401 ? "authentication required" : "access denied");
            }
            log.error("jreact rpc: {}/{} failed", api, method, e);
            return error(500, "Internal error");       // details stay in the log
        }
    }

    /** Spring Security's exceptions by name, so that jreact-spring-boot-starter does not depend on it. */
    private static HttpStatusCode securityStatus(Throwable e) {
        for (Class<?> c = e.getClass(); c != null; c = c.getSuperclass()) {
            String n = c.getSimpleName();
            if (n.equals("AccessDeniedException")) return HttpStatus.FORBIDDEN;
            if (n.equals("AuthenticationException")) return HttpStatus.UNAUTHORIZED;
        }
        return null;
    }

    private static Result error(int status, String message) { return error(status, message, Map.of()); }

    private static Result error(int status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message == null ? "" : message);
        if (!fields.isEmpty()) body.put("fields", fields);
        return new Result(status, Json.stringify(body));
    }

    private static ResponseEntity<String> respond(Result r) {
        return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(r.json());
    }
}
