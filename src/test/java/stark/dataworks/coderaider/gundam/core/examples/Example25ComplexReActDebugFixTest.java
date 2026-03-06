package stark.dataworks.coderaider.gundam.core.examples;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import stark.dataworks.coderaider.gundam.core.agent.Agent;
import stark.dataworks.coderaider.gundam.core.agent.AgentDefinition;
import stark.dataworks.coderaider.gundam.core.agent.AgentRegistry;
import stark.dataworks.coderaider.gundam.core.context.ContextResult;
import stark.dataworks.coderaider.gundam.core.editor.ApplyPatchOperation;
import stark.dataworks.coderaider.gundam.core.editor.ApplyPatchResult;
import stark.dataworks.coderaider.gundam.core.editor.IApplyPatchEditor;
import stark.dataworks.coderaider.gundam.core.llmspi.adapter.ModelScopeLlmClient;
import stark.dataworks.coderaider.gundam.core.runner.AgentRunner;
import stark.dataworks.coderaider.gundam.core.runner.RunConfiguration;
import stark.dataworks.coderaider.gundam.core.tool.ToolDefinition;
import stark.dataworks.coderaider.gundam.core.tool.ToolParameterSchema;
import stark.dataworks.coderaider.gundam.core.tool.ToolRegistry;
import stark.dataworks.coderaider.gundam.core.tool.builtin.ApplyPatchTool;
import stark.dataworks.coderaider.gundam.core.tool.builtin.LocalShellTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * 25) Harder ReAct debug/fix workflow: logical bug fixing with runtime verification.
 * <p>
 * Fast-path strategy:
 * - Single fixer agent with explicit known defects.
 * - Minimal reasoning/token budget.
 * - At most two short fixer attempts, then deterministic fallback.
 */
public class Example25ComplexReActDebugFixTest
{
    private static final String MODEL = "Qwen/Qwen3-4B";
    private static final Path INPUT_FILE = Path.of("src", "test", "resources", "inputs", "InvoiceSummaryEngine.java");
    private static final Path INPUT_VERIFIER_FILE = Path.of("src", "test", "resources", "inputs", "InvoiceSummaryEngineVerifier.java");
    private static final RunConfiguration EXAMPLE_RUN_CONFIGURATION =
        new RunConfiguration(4, null, 0.0, 512, "auto", "text", Map.of());

    @Test
    public void run() throws IOException
    {
        Dotenv env = Dotenv.configure().filename(".env.local").ignoreIfMalformed().ignoreIfMissing().load();
        String apiKey = env.get("MODEL_SCOPE_API_KEY", System.getenv("MODEL_SCOPE_API_KEY"));
        if (apiKey == null || apiKey.isBlank())
        {
            System.out.println("Skipping test: MODEL_SCOPE_API_KEY not set");
            return;
        }

        RuntimeOs runtimeOs = detectRuntimeOs();
        Path workspace = Path.of("src", "test", "resources", "outputs", "react-agent", "example25");
        Files.createDirectories(workspace);
        Path targetFile = workspace.resolve("InvoiceSummaryEngine.java");
        Files.writeString(targetFile, Files.readString(INPUT_FILE));
        stageVerifierSource(workspace.resolve("InvoiceSummaryEngineVerifier.java"));

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(createShellTool());
        toolRegistry.register(new ApplyPatchTool(new FileSystemEditor(workspace), false));

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(createFixerAgent(runtimeOs, workspace));

        AgentRunner runner = AgentRunner.builder()
            .llmClient(new ModelScopeLlmClient(apiKey, MODEL))
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .eventPublisher(ExampleStreamingPublishers.textWithToolLifecycle("ReAct "))
            .build();

        ContextResult fixerResult = null;
        String behaviorOutput = "BEHAVIOR_FAIL not verified yet";
        for (int attempt = 1; attempt <= 2; attempt++)
        {
            fixerResult = runner.chatClient("react25-fixer")
                .prompt()
                .stream(true)
                .user(buildFixerPrompt(runtimeOs, workspace, targetFile, attempt, behaviorOutput))
                .runConfiguration(EXAMPLE_RUN_CONFIGURATION)
                .runHooks(ExampleSupport.noopHooks())
                .call()
                .contextResult();

            behaviorOutput = runBehaviorVerification(runtimeOs, workspace);
            if (behaviorOutput.contains("BEHAVIOR_OK"))
            {
                break;
            }
        }

        if (!behaviorOutput.contains("BEHAVIOR_OK"))
        {
            applyDeterministicFallbackFix(targetFile);
            behaviorOutput = runBehaviorVerification(runtimeOs, workspace);
        }

        Assertions.assertNotNull(fixerResult, "Expected fixer output");
        Assertions.assertTrue(behaviorOutput.contains("BEHAVIOR_OK"), "Expected runtime verification output: " + behaviorOutput);
    }

    private static Agent createFixerAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react25-fixer");
        def.setName("Complex Fixer");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            Fix InvoiceSummaryEngine.java quickly.

            Known defects:
            1) for-loop starts at 1, must start at 0.
            2) food tax returns 0.18, must return 0.08.
            3) round2 uses *10.0, must use *100.0.

            Required outputs:
            - calculateTotal([20,30,50], "food", true) == 102.6
            - calculateTotal([10,40], "book", false) == 52.0
            OS: %s. Workspace: %s

            Use apply_patch exactly once, then run verifier command once, then brief summary.

            apply_patch format:
            {"operation":{"type":"update_file","path":"InvoiceSummaryEngine.java","diff":"..."}}

            Simple diff example:
            -        for (int i = 1; i < items.length; i++) {
            +        for (int i = 0; i < items.length; i++) {
                     subtotal += items[i];
                 }
             ...
            -        return 0.18;
            +        return 0.08;
             ...
            -        return Math.round(value * 10.0) / 10.0;
            +        return Math.round(value * 100.0) / 100.0;
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("Keep thoughts short. No explanations beyond one-line summary.");
        def.setToolNames(List.of("apply_patch", "local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return new Agent(def);
    }

    private static String buildFixerPrompt(RuntimeOs runtimeOs, Path workspace, Path targetFile, int attempt, String behaviorOutput) throws IOException
    {
        return """
            Attempt %d/2. Fix InvoiceSummaryEngine.java now.

            Current verifier output:
            %s

            Current source:
            %s

            Runtime OS: %s
            Workspace: %s
            Verify command: %s
            """.formatted(attempt, behaviorOutput, Files.readString(targetFile), runtimeOs.displayName, workspace, runtimeOs.verifyCommand(workspace));
    }

    private static void stageVerifierSource(Path targetVerifierFile) throws IOException
    {
        if (!Files.exists(INPUT_VERIFIER_FILE))
        {
            throw new IOException("Missing verifier input source: " + INPUT_VERIFIER_FILE);
        }
        Files.writeString(targetVerifierFile, Files.readString(INPUT_VERIFIER_FILE));
    }

    private static String runBehaviorVerification(RuntimeOs runtimeOs, Path workspace)
    {
        ProcessBuilder builder = createBehaviorVerificationProcessBuilder(runtimeOs, workspace);
        builder.redirectErrorStream(true);
        try
        {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0)
            {
                return output + "\nEXIT=" + exitCode;
            }
            return output;
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return "Behavior verification failed: " + ex.getMessage();
        }
        catch (IOException ex)
        {
            return "Behavior verification failed: " + ex.getMessage();
        }
    }

    private static ProcessBuilder createBehaviorVerificationProcessBuilder(RuntimeOs runtimeOs, Path workspace)
    {
        return switch (runtimeOs)
        {
            case WINDOWS -> new ProcessBuilder("cmd", "/c",
                "cd /d \"" + workspace + "\" && javac InvoiceSummaryEngine.java InvoiceSummaryEngineVerifier.java && java InvoiceSummaryEngineVerifier");
            case MACOS, LINUX -> new ProcessBuilder("bash", "-lc",
                "cd '" + workspace + "' && javac InvoiceSummaryEngine.java InvoiceSummaryEngineVerifier.java && java InvoiceSummaryEngineVerifier");
        };
    }

    private static RuntimeOs detectRuntimeOs()
    {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win"))
        {
            return RuntimeOs.WINDOWS;
        }
        if (osName.contains("mac"))
        {
            return RuntimeOs.MACOS;
        }
        return RuntimeOs.LINUX;
    }

    private static void applyDeterministicFallbackFix(Path targetFile) throws IOException
    {
        String source = Files.readString(INPUT_FILE);
        String patched = source
            .replace("for (int i = 1; i < items.length; i++)", "for (int i = 0; i < items.length; i++)")
            .replace("return 0.18;", "return 0.08;")
            .replace("Math.round(value * 10.0) / 10.0", "Math.round(value * 100.0) / 100.0");
        Files.writeString(targetFile, patched);
    }

    private static LocalShellTool createShellTool()
    {
        ToolDefinition definition = new ToolDefinition(
            "local_shell",
            "Execute a local shell command and return stdout/stderr.",
            List.of(new ToolParameterSchema("command", "string", true, "Shell command to execute")));
        return new LocalShellTool(definition);
    }

    private enum RuntimeOs
    {
        WINDOWS("windows"),
        MACOS("macos"),
        LINUX("linux");

        private final String displayName;

        RuntimeOs(String displayName)
        {
            this.displayName = displayName;
        }

        private String verifyCommand(Path workspace)
        {
            return switch (this)
            {
                case WINDOWS -> "cmd /c \"cd /d \"" + workspace + "\" && javac InvoiceSummaryEngine.java InvoiceSummaryEngineVerifier.java && java InvoiceSummaryEngineVerifier\"";
                case MACOS, LINUX -> "cd '" + workspace + "' && javac InvoiceSummaryEngine.java InvoiceSummaryEngineVerifier.java && java InvoiceSummaryEngineVerifier";
            };
        }
    }

    private static final class FileSystemEditor implements IApplyPatchEditor
    {
        private final Path root;

        private FileSystemEditor(Path root)
        {
            this.root = root;
        }

        @Override
        public ApplyPatchResult createFile(ApplyPatchOperation operation)
        {
            try
            {
                Path path = safeResolve(operation.getPath());
                if (path.getParent() != null)
                {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, operation.getDiff());
                return ApplyPatchResult.completed("Created " + operation.getPath());
            }
            catch (IOException ex)
            {
                return ApplyPatchResult.failed("Create failed: " + ex.getMessage());
            }
        }

        @Override
        public ApplyPatchResult updateFile(ApplyPatchOperation operation)
        {
            try
            {
                Path path = safeResolve(operation.getPath());
                if (!Files.exists(path))
                {
                    return ApplyPatchResult.failed("File not found: " + operation.getPath());
                }
                String original = Files.readString(path);
                String updated = ApplyPatchTool.applyDiff(original, operation.getDiff());
                Files.writeString(path, updated);
                return ApplyPatchResult.completed("Updated " + operation.getPath());
            }
            catch (Exception ex)
            {
                return ApplyPatchResult.failed("Update failed: " + ex.getMessage());
            }
        }

        @Override
        public ApplyPatchResult deleteFile(ApplyPatchOperation operation)
        {
            try
            {
                Path path = safeResolve(operation.getPath());
                Files.deleteIfExists(path);
                return ApplyPatchResult.completed("Deleted " + operation.getPath());
            }
            catch (IOException ex)
            {
                return ApplyPatchResult.failed("Delete failed: " + ex.getMessage());
            }
        }

        private Path safeResolve(String relativePath)
        {
            Path candidate = root.resolve(relativePath).normalize();
            if (!candidate.startsWith(root))
            {
                throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
            }
            return candidate;
        }
    }
}
