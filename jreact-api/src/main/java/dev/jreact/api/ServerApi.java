package dev.jreact.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface whose methods the browser can call on the server. Put it in the shared module:
 * <pre>{@code
 * @ServerApi
 * public interface TodoApi {
 *     List<Todo> list();
 *     Todo add(String title);
 *     void remove(long id);
 * }
 * }</pre>
 * At compile time {@code jreact-processor} generates {@code TodoApiClient} (static methods returning {@code Promise}, for the browser)
 * and {@code TodoApiDispatcher} (for the server). On the server, any Spring bean that implements the interface is exposed at
 * {@code POST /_rpc/TodoApi/<method>} by jreact-spring-boot-starter. Call it in the browser: {@code TodoApiClient.add("milk")}.
 * <p>
 * Allowed types in parameters and results: boolean, int, long, double (and their boxes), String, enums, {@code List<T>},
 * {@code Map<String,T>} and records of these. Overloaded methods are not allowed. A parameter of a type that is not allowed is a compile error.
 * Every method is a public HTTP endpoint: check permissions on the server (for example {@code @PreAuthorize}) and validate the arguments.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ServerApi {
}
