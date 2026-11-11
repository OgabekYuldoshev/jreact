package jreact.reconciler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Pending setState calls of one useState hook (React's UpdateQueue). */
final class UpdateQueue {
    final Reconciler<?> reconciler;
    final Fiber fiber;                                      // the fiber that mounted the hook
    final List<UnaryOperator<Object>> pending = new ArrayList<>();
    Object lastRendered;                                    // state seen by the last render, for eager bail-out

    UpdateQueue(Reconciler<?> reconciler, Fiber fiber, Object initial) {
        this.reconciler = reconciler; this.fiber = fiber; this.lastRendered = initial;
    }
}
