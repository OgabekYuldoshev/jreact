package jreact.router;

import jreact.Component;
import jreact.Element;

/**
 * Put one {@code <ScrollRestoration />} in your root layout: after a navigation to a new page the window scrolls to the top (or to the
 * {@code #hash} element), and back/forward return to where the page was. Needs a {@link Scroller} (the browser routers set one).
 */
public class ScrollRestoration extends Component<ScrollRestoration.Props> {

    public record Props() {}

    @Override
    public Element render(Props props) {
        RouterContext.DataState data = useContext(RouterContext.DATA);
        if (data == null) throw new IllegalStateException("<ScrollRestoration> must be inside a <RouterProvider>");
        DataRouter.Snapshot s = data.snapshot();
        String where = s.location().toString();
        useEffect(() -> {
            Scroller scroller = data.router().scroller();
            if (scroller == null || !s.navigation().state().equals(DataRouter.NavigationState.IDLE)) return;
            if (s.historyAction() == DataRouter.HistoryAction.POP) {
                int[] saved = data.router().savedScroll(s.location());
                if (saved != null) scroller.scrollTo(saved[0], saved[1]);
                else if (!s.location().hash().isEmpty()) scroller.toHash(s.location().hash());
                else scroller.scrollTo(0, 0);
            } else if (!s.location().hash().isEmpty()) scroller.toHash(s.location().hash());
            else scroller.scrollTo(0, 0);
        }, where, s.historyAction());
        return null;
    }
}
