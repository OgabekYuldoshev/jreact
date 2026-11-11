package jreact.reconciler;

import jreact.Effect;

/** A useEffect that is scheduled to run (or already ran) with its cleanup. */
final class HookEffect {
    final Effect create;
    final Object[] deps;
    Runnable destroy;       // cleanup returned by create
    HookEffect prev;        // previous effect of the same hook whose cleanup must run first

    HookEffect(Effect create, Object[] deps) { this.create = create; this.deps = deps; }
}
