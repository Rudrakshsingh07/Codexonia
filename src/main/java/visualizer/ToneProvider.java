package visualizer;

/**
 * Supplies the deterministic tone mapping to the timeline builder.
 *
 * <p>The core stays free of Spring and of the legacy default-package facade; the
 * controller passes in the shared {@code HashFunction} implementation.</p>
 */
@FunctionalInterface
public interface ToneProvider {
    ToneSpec toneFor(String signature);
}
