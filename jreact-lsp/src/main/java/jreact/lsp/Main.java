package jreact.lsp;

/** Language server for .jsxj over stdio: {@code java -jar jsxj-lsp.jar}. */
public final class Main {
    public static void main(String[] args) {
        System.exit(new Server(System.in, System.out).run());
    }
}
