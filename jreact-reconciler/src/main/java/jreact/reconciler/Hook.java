package jreact.reconciler;

/** One slot of a component's hook list (React's Hook). The list is matched by call order. */
final class Hook {
    enum Kind { STATE, EFFECT, MEMO, REF }

    final Kind kind;
    Object memoizedState;   // STATE: value, EFFECT: HookEffect, MEMO: MemoState, REF: Ref
    UpdateQueue queue;      // STATE only, shared by current and work-in-progress hooks
    Hook next;

    Hook(Kind kind) { this.kind = kind; }
}
