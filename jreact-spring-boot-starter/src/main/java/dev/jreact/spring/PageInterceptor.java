package dev.jreact.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.ParameterizableViewController;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.nio.charset.StandardCharsets;

/**
 * Answers page requests itself when the page needs more than the file: server side rendered HTML ({@code jreact.ssr.app}), and
 * index.html adapted to a servlet context path. Only requests that would get index.html anyway are touched (the handler is the resource
 * handler or the view controller of {@code /}), so controllers and static files are not affected. Without SSR and without a context path
 * the interceptor does nothing and the resource handler serves the file (with its pre-compressed copy). When a page cannot be rendered
 * on the server the request goes on and gets index.html.
 */
final class PageInterceptor implements HandlerInterceptor {

    private final SsrRenderer renderer;                 // null: no SSR (not configured, or dev mode)
    private final IndexHtml index;
    private final boolean dev;
    private final SpaResourceResolver pages;
    private final String loginUrl;

    PageInterceptor(SsrRenderer renderer, IndexHtml index, JReactProperties props) {
        this.renderer = renderer;
        this.index = index;
        this.dev = props.isDev();
        this.loginUrl = props.getLoginUrl() == null || props.getLoginUrl().isBlank() ? null : props.getLoginUrl();
        this.pages = new SpaResourceResolver(props.getReservedPrefixes());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String contextPath = request.getContextPath();
        if (renderer == null && contextPath.isEmpty()) return true;
        if (!"GET".equals(request.getMethod())) return true;
        if (!(handler instanceof ResourceHttpRequestHandler || handler instanceof ParameterizableViewController)) return true;
        String path = request.getRequestURI().substring(contextPath.length());
        boolean indexFile = path.equals("/index.html");
        if (!indexFile && !pages.isPageRequest(request, path)) return true;

        SsrRenderer.Page page = renderer == null || indexFile ? null : renderer.render(request.getQueryString() == null ? path : path + "?" + request.getQueryString(), contextPath);
        response.setHeader("Cache-Control", CacheControl.noCache().cachePrivate().getHeaderValue());
        if (page != null && page.redirect() != null) {
            response.setStatus(302);
            response.setHeader("Location", contextPath + page.redirect());
            return false;
        }
        if (page != null && page.status() == 401 && loginUrl != null && !path.equals(loginUrl)) {      // a loader needs a signed in user: ask for a login, then come back
            String back = request.getQueryString() == null ? path : path + "?" + request.getQueryString();
            response.setStatus(302);
            response.setHeader("Location", contextPath + loginUrl + "?next=" + java.net.URLEncoder.encode(contextPath + back, StandardCharsets.UTF_8));
            return false;
        }
        String html;
        int status;
        if (page != null) {
            html = page.html();
            status = page.status();
        } else {
            html = index.html(contextPath);
            status = 200;
        }
        if (dev) html = index.withReload(html, contextPath);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        return false;
    }
}
