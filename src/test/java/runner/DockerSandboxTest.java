package runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class DockerSandboxTest {

    private static final Path JOBS_ROOT = Path.of("/var/lib/codexonia/jobs");

    @Test
    void namedVolumeMountsOnlyTheJobSubpath() {
        DockerSandbox sandbox = new DockerSandbox(
                LimitPolicy.defaults(), JOBS_ROOT, "codexonia-jobs");

        List<String> cmd = sandbox.dockerCommand(
                JOBS_ROOT.resolve("codexonia-job-abc"), "codexonia-job-test", "Sample");

        assertTrue(cmd.contains("--mount"));
        assertTrue(cmd.contains("type=volume,source=codexonia-jobs,"
                + "target=/workspace,volume-subpath=codexonia-job-abc"));
        assertFalse(cmd.contains("-v"));
        assertEquals("/workspace/src", cmd.get(cmd.indexOf("codexonia-sandbox:latest") + 1));
    }

    @Test
    void hostModeUsesBindMount() {
        DockerSandbox sandbox = new DockerSandbox(LimitPolicy.defaults(), JOBS_ROOT, null);
        Path workspace = JOBS_ROOT.resolve("codexonia-job-abc");

        List<String> cmd = sandbox.dockerCommand(workspace, "codexonia-job-test", "Sample");

        int mount = cmd.indexOf("-v");
        assertTrue(mount > 0);
        assertEquals(workspace + ":/workspace:Z", cmd.get(mount + 1));
        assertFalse(cmd.contains("--mount"));
    }

    @Test
    void sandboxKeepsHardeningFlags() {
        DockerSandbox sandbox = new DockerSandbox(
                LimitPolicy.defaults(), JOBS_ROOT, "codexonia-jobs");

        List<String> cmd = sandbox.dockerCommand(
                JOBS_ROOT.resolve("codexonia-job-abc"), "codexonia-job-test", "Sample");

        assertTrue(cmd.contains("--network"));
        assertTrue(cmd.contains("none"));
        assertTrue(cmd.contains("--read-only"));
        assertTrue(cmd.contains("--cap-drop"));
        assertTrue(cmd.contains("ALL"));
        assertTrue(cmd.contains("--security-opt"));
        assertTrue(cmd.contains("no-new-privileges"));
        assertTrue(cmd.contains("--memory"));
        assertTrue(cmd.contains("--pids-limit"));
    }

    @Test
    void rejectsWorkspaceOutsideConfiguredVolume() {
        DockerSandbox sandbox = new DockerSandbox(
                LimitPolicy.defaults(), JOBS_ROOT, "codexonia-jobs");

        String problem = sandbox.mountProblem(Path.of("/tmp/codexonia-job-outside"));

        assertTrue(problem != null && problem.contains("outside CODEXONIA_JOBS_DIR"));
    }
}
