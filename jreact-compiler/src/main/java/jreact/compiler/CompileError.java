package jreact.compiler;

/** A .jsxj compile error with an exact source position. */
public final class CompileError extends RuntimeException {
    private final String file;
    private final int line;
    private final int col;
    private final String sourceLine;

    public CompileError(String file, int line, int col, String message, String sourceLine) {
        super(message);
        this.file = file;
        this.line = line;
        this.col = col;
        this.sourceLine = sourceLine;
    }

    public String file() { return file; }
    public int line() { return line; }
    public int col() { return col; }

    /** javac-style report: file:line:col: error: message, the source line and a caret. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(file).append(':').append(line).append(':').append(col)
          .append(": error: ").append(getMessage());
        if (sourceLine != null) {
            sb.append('\n').append("    ").append(sourceLine.replace('\t', ' '));
            sb.append('\n').append("    ").append(" ".repeat(Math.max(0, col - 1))).append('^');
        }
        return sb.toString();
    }
}
