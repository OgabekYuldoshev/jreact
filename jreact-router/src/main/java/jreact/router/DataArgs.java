package jreact.router;

import java.util.Map;

/** The arguments of {@link Loader#load}, {@link Action#run}: the route's {@code params}, the {@code request} and the {@code signal}. */
public record DataArgs(Map<String, String> params, Request request, AbortSignal signal) {}
