package dhrlang.error;

public class SourceLocation {
    private final String filename;
    private final int line;
    private final int column;
    private final int startOffset;
    private final int endOffset;

    public SourceLocation(String filename, int line, int column, int startOffset, int endOffset) {
        this.filename = filename;
        this.line = line;
        this.column = column;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public SourceLocation(String filename, int line, int column) {
        this(filename, line, column, -1, -1);
    }

    public String getFilename() {
        return filename;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public String toString() {
        if (filename != null) {
            return filename + ":" + line + ":" + column;
        }
        return "line " + line + ":" + column;
    }

    public String toShortString() {
        return line + ":" + column;
    }
}
