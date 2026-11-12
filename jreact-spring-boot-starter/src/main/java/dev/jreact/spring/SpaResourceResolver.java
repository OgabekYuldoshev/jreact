package dev.jreact.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.util.List;

/**
 * Finds the file for a URL; when there is none and the URL looks like a page of the app (a browser navigation, no file extension,
 * not under a reserved prefix such as /api), answers with index.html so that the client-side router can take over.
 */
final class SpaResourceResolver extends PathResourceResolver {

    private final List<String> reserved;

    SpaResourceResolver(List<String> reservedPrefixes) { this.reserved = reservedPrefixes; }

    @Override
    protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath, List<? extends Resource> locations,
                                               ResourceResolverChain chain) {
        Resource found = super.resolveResourceInternal(request, requestPath, locations, chain);
        if (found != null || request == null || !isPageRequest(request, requestPath)) return found;
        return super.resolveResourceInternal(request, "index.html", locations, chain);
    }

    boolean isPageRequest(HttpServletRequest request, String requestPath) {
        String path = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
        for (String prefix : reserved) {
            if (path.equals(prefix) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/")) return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.indexOf('.') >= 0) return false;       // /missing.js is a 404, not the app
        String accept = request.getHeader("Accept");    // fetch() asking for JSON must not get HTML
        return accept == null || accept.isBlank() || accept.contains("text/html") || accept.contains("*/*");
    }
}
