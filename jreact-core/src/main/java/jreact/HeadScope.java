package jreact;

/** Gives its child a {@link Head}: what {@code @Page(title = "...")} generates around the page. Renders the child as is. */
public final class HeadScope extends Component<HeadScope.Props> {

    public record Props(Head head, Element child) {}

    @Override
    public Element render(Props props) {
        useHead(props.head());
        return props.child();
    }
}
