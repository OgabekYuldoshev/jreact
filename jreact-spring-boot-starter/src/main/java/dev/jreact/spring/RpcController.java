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

    @PostMapping(value = "/_rpc/{api}/{method}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> call(@PathVariable("api") String api, @PathVariable("method") String method, @RequestBody String body) {     // names spelled out: no -parameters needed
        Target target = targets.get(api);
        if (target == null) return error(404, "no such api: " + api);
        Object args;
        try {
            args = Json.parse(body);
        } catch (Json.ParseException e) {
            return error(400, "the body is not valid JSON: " + e.getMessage());
        }
        if (!(args instanceof List<?>)) return error(400, "the body must be a JSON array of the arguments");
        try {
            @SuppressWarnings("unchecked") Object result = target.dispatcher().invoke(target.bean(), method, (List<Object>) args);
            return respond(200, Json.stringify(result));
        } catch (RpcException e) {
            return error(e.status(), e.getMessage());
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode().value(), e.getReason() != null ? e.getReason() : HttpStatus.valueOf(e.getStatusCode().value()).getReasonPhrase());
        } catch (RuntimeException e) {
            HttpStatusCode security = securityStatus(e);
            if (security != null) return error(security.value(), security.value() == 401 ? "authentication required" : "access denied");
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

    private static ResponseEntity<String> error(int status, String message) {
        return respond(status, Json.stringify(Map.of("message", message == null ? "" : message)));
    }

    private static ResponseEntity<String> respond(int status, String json) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(json);
    }
}
