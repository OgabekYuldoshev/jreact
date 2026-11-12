package app;

import jreact.Context;

import java.util.function.Consumer;

/** Who is signed in (null: nobody) and how to change it. Shared through {@link #CONTEXT}. */
public record Auth(String user, Consumer<String> signIn, Runnable signOut) {

    public static final Context<Auth> CONTEXT = Context.create(new Auth(null, u -> { }, () -> { }), "auth");

    public boolean signedIn() { return user != null; }

    /** A path as one query value: {@code /account/x} becomes {@code %2Faccount%2Fx}. */
    public static String encodePath(String path) { return path.replace("%", "%25").replace("/", "%2F").replace("?", "%3F").replace("&", "%26"); }
}
