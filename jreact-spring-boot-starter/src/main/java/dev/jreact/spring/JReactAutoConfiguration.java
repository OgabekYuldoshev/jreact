package dev.jreact.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.EncodedResourceResolver;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceTransformerSupport;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Serves the JReact client from the same server as the API:
 * <ul>
 *   <li>{@code /assets/**}: hashed files, {@code Cache-Control: immutable} for a year, the pre-compressed .gz copy when the browser accepts it</li>
 *   <li>everything else: index.html is {@code no-cache}; a URL that is no file and no API answers with index.html (the router takes over)</li>
 *   <li>dev mode ({@code jreact.dev-dir}): files straight from that folder, nothing cached, reload script in the page</li>
 * </ul>
 * Controllers always win over these handlers, so {@code @GetMapping("/something")} works as usual.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "jreact", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(JReactProperties.class)
public class JReactAutoConfiguration {

    /** Server side rendering of page requests, on when {@code jreact.ssr.app} is set (and not in dev mode). */
    @Bean
    @ConditionalOnProperty(prefix = "jreact.ssr", name = "app")
    SsrInterceptor jreactSsrInterceptor(JReactProperties props, ResourceLoader loader, RpcController rpc) {
        if (props.isDev()) return new SsrInterceptor(null, props);       // dev: the client changes all the time, the browser renders
        Resource index;
        try { index = locationOf(props, loader).createRelative("index.html"); }
        catch (IOException e) { throw new IllegalStateException("bad jreact.location: " + e.getMessage(), e); }
        ClassLoader cl = loader != null && loader.getClassLoader() != null ? loader.getClassLoader() : getClass().getClassLoader();
        return new SsrInterceptor(new SsrRenderer(props.getSsr().getApp(), props.getSsr().getContainerId(), rpc, index, cl), props);
    }

    @Bean
    WebMvcConfigurer jreactWebMvcConfigurer(JReactProperties props, ResourceLoader loader,
                                            org.springframework.beans.factory.ObjectProvider<SsrInterceptor> ssr) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                SsrInterceptor i = ssr.getIfAvailable();
                if (i != null) registry.addInterceptor(i);
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/").setViewName("forward:/index.html");
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                boolean dev = props.isDev();
                Resource location = locationOf(props, loader);

                var assets = registry.addResourceHandler("/assets/**").addResourceLocations(assetsOf(location))
                        .setCacheControl(dev ? CacheControl.noStore() : CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
                var chain = assets.resourceChain(false);
                if (!dev) chain.addResolver(new EncodedResourceResolver());
                chain.addResolver(new PathResourceResolver());

                var pages = registry.addResourceHandler("/**").addResourceLocations(location).setCacheControl(CacheControl.noCache());
                var pageChain = pages.resourceChain(false);
                if (!dev) pageChain.addResolver(new EncodedResourceResolver());
                pageChain.addResolver(new SpaResourceResolver(props.getReservedPrefixes()));
                if (dev) pageChain.addTransformer(new ReloadScriptInjector());
            }
        };
    }

    /** The server side of {@code @ServerApi}: POST /_rpc/Api/method. */
    @Bean
    @ConditionalOnProperty(prefix = "jreact.rpc", name = "enabled", matchIfMissing = true)
    RpcController jreactRpcController(org.springframework.context.ApplicationContext context) throws java.io.IOException {
        return new RpcController(context);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jreact", name = "dev-dir")
    DevReloadController jreactDevReloadController(JReactProperties props) { return new DevReloadController(props); }

    /** /assets/** maps to the assets/ folder of the location (the handler strips the /assets/ prefix). */
    private static Resource assetsOf(Resource location) {
        try { return location.createRelative("assets/"); }
        catch (IOException e) { throw new IllegalStateException("bad jreact.location: " + e.getMessage(), e); }
    }

    private static Resource locationOf(JReactProperties props, ResourceLoader loader) {
        String where;
        if (props.isDev()) {
            String dir = java.nio.file.Path.of(props.getDevDir()).toAbsolutePath().normalize().toString();
            where = "file:" + dir + "/";
        } else {
            where = props.getLocation().endsWith("/") ? props.getLocation() : props.getLocation() + "/";
        }
        return (loader == null ? new DefaultResourceLoader() : loader).getResource(where);
    }

    /** Dev mode: adds the reload script to index.html. */
    private static final class ReloadScriptInjector extends ResourceTransformerSupport {
        @Override
        public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain) throws IOException {
            resource = chain.transform(request, resource);
            if (!"index.html".equals(resource.getFilename())) return resource;
            String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String tag = "<script src=\"/__jreact/reload.js\"></script>";
            int end = html.lastIndexOf("</body>");
            String out = end < 0 ? html + tag : html.substring(0, end) + tag + html.substring(end);
            return new TransformedResource(resource, out.getBytes(StandardCharsets.UTF_8));
        }
    }
}
