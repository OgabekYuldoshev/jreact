package dev.jreact.spring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Makes Spring Security's CSRF protection work with a JReact page, in one line of your {@code SecurityFilterChain}:
 * <pre>{@code
 * @Bean
 * SecurityFilterChain chain(HttpSecurity http) throws Exception {
 *     return http
 *         .with(JReactSecurityConfigurer.jreact(), c -> {})
 *         .authorizeHttpRequests(a -> a.requestMatchers("/assets/**", "/login").permitAll().anyRequest().authenticated())
 *         .formLogin(Customizer.withDefaults())
 *         .build();
 * }
 * }</pre>
 * The page's JavaScript ({@code Http}, the generated RPC clients, {@code <Form>}) copies the {@code XSRF-TOKEN} cookie into the
 * {@code X-XSRF-TOKEN} header of every request that changes data. Spring Security does not write that cookie by itself, so this does what the
 * Spring Security reference calls the single page application setup: a cookie the script can read, a token handler that accepts the
 * plain token from the header, and a filter that makes sure the cookie is sent on the first response (also for the {@code index.html} the server renders).
 * <p>Without it, with CSRF on, every call of the browser answers 403.
 */
public final class JReactSecurityConfigurer extends AbstractHttpConfigurer<JReactSecurityConfigurer, HttpSecurity> {

    public static JReactSecurityConfigurer jreact() { return new JReactSecurityConfigurer(); }

    @Override
    public void init(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new SpaTokenHandler()));
    }

    @Override
    public void configure(HttpSecurity http) {
        http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
    }

    /** The token in the header is the plain cookie value; a token in a form field is the BREACH-protected (XOR) one. */
    static final class SpaTokenHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            return StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
                    ? plain.resolveCsrfTokenValue(request, csrfToken)
                    : xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /** Spring Security loads the token lazily; reading it makes it write the cookie. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
            if (token != null) token.getToken();
            chain.doFilter(request, response);
        }
    }
}
