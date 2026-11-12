package jreact.lsp;

/** Language server for .jrx over stdio: {@code java -jar jrx-lsp.jar}. */
public final class Main {
    public static void main(String[] args) {
        System.exit(new Server(System.in, System.out).run());
    }
}
