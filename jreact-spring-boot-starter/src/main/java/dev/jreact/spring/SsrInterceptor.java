package dev.jreact.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.ParameterizableViewController;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.nio.charset.StandardCharsets;

/**
 * Answers page requests with the server rendered page. Only requests that would get index.html anyway are touched (the handler is the
 * resource handler or the view controller of {@code /}), so controllers and static files are not affected. When the page cannot be rendered
 * here the request goes on and gets the plain index.html.
 */
final class SsrInterceptor implements HandlerInterceptor {

    private final SsrRenderer renderer;                 // null in dev mode
    private final SpaResourceResolver pages;

    SsrInterceptor(SsrRenderer renderer, JReactProperties props) {
        this.renderer = renderer;
        this.pages = new SpaResourceResolver(props.getReservedPrefixes());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (renderer == null || !"GET".equals(request.getMethod())) return true;
        if (!(handler instanceof ResourceHttpRequestHandler || handler instanceof ParameterizableViewController)) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!pages.isPageRequest(request, path)) return true;
        String uri = request.getQueryString() == null ? path : path + "?" + request.getQueryString();
        SsrRenderer.Page page = renderer.render(uri);
        if (page == null) return true;
        response.setHeader("Cache-Control", CacheControl.noCache().cachePrivate().getHeaderValue());
        if (page.redirect() != null) {
            response.setStatus(302);
            response.setHeader("Location", request.getContextPath() + page.redirect());
            return false;
        }
        byte[] bytes = page.html().getBytes(StandardCharsets.UTF_8);
        response.setStatus(page.status());
        response.setContentType("text/html;charset=UTF-8");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        return false;
    }
}
