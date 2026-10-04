package runner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates the class containing the real entry point so the sandbox knows what to run.
 * v1: default or single package; prefers the class whose name matches the file name.
 */
public final class MainClassDetector {

    private static final Pattern MAIN = Pattern.compile(
            "public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*(\\[\\s*\\]|\\.\\.\\.)");
    private static final Pattern CLASS = Pattern.compile(
            "\\b(?:class|record|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern PKG = Pattern.compile(
            "^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private MainClassDetector() {}

    /** Returns the fully-qualified main class name, or null when no main method is found. */
    public static String detect(CodeSubmission submission) {
        for (JavaSourceFile f : submission.files) {
            if (MAIN.matcher(f.source).find()) {
                String pkg = "";
                Matcher pm = PKG.matcher(f.source);
                if (pm.find()) {
                    pkg = pm.group(1) + ".";
                }
                String baseName = f.fileName.replaceAll("\\.java$", "");
                // Prefer the class matching the file name (the public class convention).
                Matcher cm = CLASS.matcher(f.source);
                String first = null;
                while (cm.find()) {
                    if (first == null) {
                        first = cm.group(1);
                    }
                    if (cm.group(1).equals(baseName)) {
                        return pkg + cm.group(1);
                    }
                }
                if (first != null) {
                    return pkg + first;
                }
                return pkg + baseName;
            }
        }
        return null;
    }
}
