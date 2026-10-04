package runner;

/** One submitted Java source file. */
public final class JavaSourceFile {
    public final String fileName;
    public final String source;
    public final boolean isMainFile;

    public JavaSourceFile(String fileName, String source, boolean isMainFile) {
        this.fileName = fileName;
        this.source = source;
        this.isMainFile = isMainFile;
    }
}
