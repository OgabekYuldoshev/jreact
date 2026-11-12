package dev.jreact.api;

import jreact.Element;
import jreact.router.RouteObject;

import java.util.List;

/**
 * The routes of an app, in one class that the browser and the server both load: the browser renders them
 * ({@code Routers.createBrowserRouter(app.routes())}), and the Spring Boot server renders the first page from the same
 * routes to HTML (server side rendering, see {@code jreact.ssr.app}). Keep it free of browser classes ({@code jreact.web.*}):
 * loaders call {@code @ServerApi} clients, which work in both places.
 */
public interface ClientApp {

    List<RouteObject> routes();

    /** Shown while the first page's loaders run (browser only: the server answers with the finished page). */
    default Element fallback() { return null; }
}
