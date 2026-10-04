package runner;

import java.util.Arrays;
import java.util.List;

/** A set of one or more Java files submitted for validation + traced execution. */
public final class CodeSubmission {
    public final List<JavaSourceFile> files;

    private CodeSubmission(List<JavaSourceFile> files) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("submission must contain at least one file");
        }
        this.files = List.copyOf(files);
    }

    public static CodeSubmission of(JavaSourceFile... files) {
        return new CodeSubmission(Arrays.asList(files));
    }

    public static CodeSubmission singleFile(String fileName, String source) {
        return of(new JavaSourceFile(fileName, source, true));
    }

    public int totalSourceBytes() {
        int total = 0;
        for (JavaSourceFile f : files) {
            total += f.source.length();
        }
        return total;
    }
}
