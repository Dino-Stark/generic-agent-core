package stark.dataworks.coderaider.genericagent.core.examples;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import stark.dataworks.coderaider.genericagent.core.agent.AgentDefinition;
import stark.dataworks.coderaider.genericagent.core.agent.AgentRegistry;
import stark.dataworks.coderaider.genericagent.core.context.ContextResult;
import stark.dataworks.coderaider.genericagent.core.editor.ApplyPatchOperation;
import stark.dataworks.coderaider.genericagent.core.editor.ApplyPatchResult;
import stark.dataworks.coderaider.genericagent.core.editor.IApplyPatchEditor;
import stark.dataworks.coderaider.genericagent.core.llmspi.adapter.ModelScopeLlmClient;
import stark.dataworks.coderaider.genericagent.core.runner.AgentRunner;
import stark.dataworks.coderaider.genericagent.core.runner.RunConfiguration;
import stark.dataworks.coderaider.genericagent.core.tool.ToolDefinition;
import stark.dataworks.coderaider.genericagent.core.tool.ToolParameterSchema;
import stark.dataworks.coderaider.genericagent.core.tool.ToolRegistry;
import stark.dataworks.coderaider.genericagent.core.tool.builtin.ApplyPatchTool;
import stark.dataworks.coderaider.genericagent.core.tool.builtin.LocalShellTool;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * 25) Harder ReAct debug/fix workflow: logical bug fixing with runtime verification.
 * <p>
 * Pattern:
 * - Single debugger agent: inspect, patch, verify by tool calls.
 * - Outer retry loop: rerun agent if verifier still fails.
 */
public class Example25ComplexReActDebugFixTest
{
    private static final String MODEL = "Qwen/Qwen3-4B";
    private static final Path INPUT_FILE = Path.of("src", "test", "resources", "inputs", "InvoiceSummaryEngine.java");
    private static final Path INPUT_VERIFIER_FILE = Path.of("src", "test", "resources", "inputs", "InvoiceSummaryEngineVerifier.java");
    private static final RunConfiguration EXAMPLE_RUN_CONFIGURATION =
        new RunConfiguration(4, null, 0.0, 1024, "auto", "text", Map.of());

    @Test
    public void run() throws IOException
    {
        long startedAt = System.nanoTime();
        Dotenv env = Dotenv.configure().filename(".env.local").ignoreIfMalformed().ignoreIfMissing().load();
        String apiKey = env.get("MODEL_SCOPE_API_KEY", System.getenv("MODEL_SCOPE_API_KEY"));
        if (apiKey == null || apiKey.isBlank())
        {
            System.out.println("Skipping test: MODEL_SCOPE_API_KEY not set");
            return;
        }

        RuntimeOs runtimeOs = detectRuntimeOs();
        Path workspace = Path.of("src", "test", "resources", "outputs", "react-agent", "example25");
        Path targetFile = resetWorkspace(workspace);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(createShellTool());
        toolRegistry.register(new ApplyPatchTool(new FileSystemEditor(workspace), false));

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(createDebuggerAgent(runtimeOs, workspace));

        AgentRunner runner = AgentRunner.builder()
            .llmClient(new ModelScopeLlmClient(apiKey, MODEL))
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .eventPublisher(ExampleStreamingPublishers.textWithToolLifecycle("ReAct25 "))
            .build();

        String behaviorOutput = runBehaviorVerification(runtimeOs, workspace);
        System.out.println("INITIAL_VERIFICATION: " + behaviorOutput.trim());

        ContextResult debuggerResult = null;
        for (int attempt = 1; attempt <= 5; attempt++)
        {
            String sourceSnapshot = Files.readString(targetFile);
            debuggerResult = runner.chatClient("react25-debugger")
                .prompt()
                .stream(true)
                .user(buildDebuggerPrompt(runtimeOs, workspace, attempt, behaviorOutput, sourceSnapshot))
                .runConfiguration(EXAMPLE_RUN_CONFIGURATION)
                .runHooks(ExampleSupport.noopHooks())
                .call()
                .contextResult();

            behaviorOutput = runBehaviorVerification(runtimeOs, workspace);
            System.out.println("ATTEMPT_" + attempt + "_VERIFICATION: " + behaviorOutput.trim());
            if (behaviorOutput.contains("BEHAVIOR_OK"))
            {
                break;
            }
        }

        Assertions.assertNotNull(debuggerResult, "Expected debugger output");
        Assertions.assertTrue(debuggerResult.getFinalOutput() != null && !debuggerResult.getFinalOutput().isBlank(),
            "Expected debugger summary output");
        
        Assertions.assertTrue(behaviorOutput.contains("BEHAVIOR_OK"), 
            "Agent must fix all bugs successfully. Verification output: " + behaviorOutput);
        
        System.out.println("FINAL_VERIFICATION: " + behaviorOutput.trim());
        long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
        Assertions.assertTrue(elapsedSeconds <= 120, "Expected runtime (<=120s) but took " + elapsedSeconds + "s");
    }

    private static Path resetWorkspace(Path workspace) throws IOException
    {
        if (Files.exists(workspace))
        {
            Files.walk(workspace)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(path ->
                {
                    try
                    {
                        Files.delete(path);
                    }
                    catch (IOException ex)
                    {
                        throw new RuntimeException(ex);
                    }
                });
        }
        Files.createDirectories(workspace);
        Path targetFile = workspace.resolve("InvoiceSummaryEngine.java");
        Files.writeString(targetFile, Files.readString(INPUT_FILE));
        stageVerifierSource(workspace.resolve("InvoiceSummaryEngineVerifier.java"));
        return targetFile;
    }

    private static AgentDefinition createDebuggerAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react25-debugger");
        def.setName("Complex ReAct Debugger");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            You are a code debugger. Find and fix bugs in Java files.
            
            Process:
            1. Read the file to understand its logic
            2. Identify bugs by analyzing the code
            3. Apply patches to fix all bugs
            4. Verify the fixes work correctly
            
            OS: %s
            Workspace: %s
            Verify command: %s
            """.formatted(runtimeOs.displayName, workspace, runtimeOs.verifyCommand(workspace)));
        def.setReactInstructions("Read → Diagnose → Fix → Verify → Done. No explanations until verification passes.");
        def.setToolNames(List.of("apply_patch", "local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static String buildDebuggerPrompt(RuntimeOs runtimeOs, Path workspace, int attempt, String behaviorOutput, String sourceSnapshot)
    {
        return """
            Attempt %d to fix InvoiceSummaryEngine.java.
            
            Current verification status:
            %s
            
            Current code:
            %s
            
            Fix all bugs and verify with: %s
            Target: output should contain BEHAVIOR_OK
            
            OS: %s
            Workspace: %s
            """.formatted(attempt, behaviorOutput.trim(), sourceSnapshot, runtimeOs.verifyCommand(workspace), 
                runtimeOs.displayName, workspace);
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
            String output = new String(process.getInputStream().readAllBytes(), runtimeConsoleCharset());
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

    private static Charset runtimeConsoleCharset()
    {
        String nativeEncoding = System.getProperty("native.encoding");
        if (nativeEncoding != null && !nativeEncoding.isBlank())
        {
            try
            {
                return Charset.forName(nativeEncoding);
            }
            catch (Exception ignored)
            {
            }
        }
        return Charset.defaultCharset();
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
                case WINDOWS ->
                    "cmd /c \"cd /d \"" + workspace + "\" && javac InvoiceSummaryEngine.java InvoiceSummaryEngineVerifier.java && java InvoiceSummaryEngineVerifier\"";
                case MACOS, LINUX ->
                    "cd '" + workspace + "' && javac InvoiceSummaryEngine.java InvoiceSummaryEngineVerifier.java && java InvoiceSummaryEngineVerifier";
            };
        }

        private String printFileCommand(Path workspace, String fileName)
        {
            return switch (this)
            {
                case WINDOWS -> "cmd /c \"cd /d \"" + workspace + "\" && type " + fileName + "\"";
                case MACOS, LINUX -> "cd '" + workspace + "' && cat " + fileName;
            };
        }
    }

    private static LocalShellTool createShellTool()
    {
        ToolDefinition definition = new ToolDefinition(
            "local_shell",
            "Execute a local shell command and return stdout/stderr.",
            List.of(new ToolParameterSchema("command", "string", true, "Shell command to execute")));
        return new LocalShellTool(definition);
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
                String diff = operation.getDiff();
                if (containsUnsupportedDiffMarkers(diff))
                {
                    return ApplyPatchResult.failed("Unsupported diff format. Use simple diff only with context/'-'/'+' lines.");
                }
                if (!hasExplicitChangeLines(diff))
                {
                    return ApplyPatchResult.failed("Invalid diff: must include '-' and '+' change lines.");
                }
                String original = Files.readString(path);
                String updated = applySmartDiff(original, diff);
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
            Path raw = Path.of(relativePath == null ? "" : relativePath).normalize();
            Path candidate;
            if (raw.isAbsolute())
            {
                candidate = raw;
            }
            else
            {
                candidate = root.resolve(raw).normalize();
            }
            if (candidate.startsWith(root) && Files.exists(candidate))
            {
                return candidate;
            }

            // Graceful fallback for model-generated long paths: keep file name in workspace.
            Path fileName = raw.getFileName();
            if (fileName != null)
            {
                Path byName = root.resolve(fileName).normalize();
                if (byName.startsWith(root))
                {
                    return byName;
                }
            }
            throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
        }

        private static boolean hasExplicitChangeLines(String diff)
        {
            if (diff == null || diff.isBlank())
            {
                return false;
            }
            boolean hasMinus = false;
            boolean hasPlus = false;
            String[] lines = diff.split("\\R", -1);
            for (String line : lines)
            {
                if (line.startsWith("-"))
                {
                    hasMinus = true;
                }
                else if (line.startsWith("+"))
                {
                    hasPlus = true;
                }
            }
            return hasMinus && hasPlus;
        }

        private static boolean containsUnsupportedDiffMarkers(String diff)
        {
            if (diff == null)
            {
                return false;
            }
            return diff.contains("diff --git")
                || diff.contains("\n--- ")
                || diff.contains("\n+++ ")
                || diff.contains("\n@@");
        }

        private static String applySmartDiff(String original, String diff)
        {
            if (looksLikeReplacementOnlyDiff(diff))
            {
                return applySimpleReplacementDiff(original, diff);
            }
            try
            {
                return ApplyPatchTool.applyDiff(original, diff);
            }
            catch (Exception ignored)
            {
                return applySimpleReplacementDiff(original, diff);
            }
        }

        private static boolean looksLikeReplacementOnlyDiff(String diff)
        {
            if (diff == null || diff.isBlank())
            {
                return false;
            }
            boolean hasMinus = false;
            boolean hasPlus = false;
            String[] lines = diff.split("\\R", -1);
            for (String line : lines)
            {
                if (line.isBlank())
                {
                    continue;
                }
                if (line.startsWith("-"))
                {
                    hasMinus = true;
                    continue;
                }
                if (line.startsWith("+"))
                {
                    hasPlus = true;
                    continue;
                }
                return false;
            }
            return hasMinus && hasPlus;
        }

        private static String applySimpleReplacementDiff(String original, String diff)
        {
            if (diff == null || diff.isBlank())
            {
                throw new IllegalArgumentException("Empty diff.");
            }

            String[] lines = diff.split("\\R", -1);
            String updated = original;
            String pendingRemoved = null;
            int replacedCount = 0;
            for (String line : lines)
            {
                if (line.startsWith("-"))
                {
                    pendingRemoved = line.substring(1);
                    continue;
                }
                if (line.startsWith("+") && pendingRemoved != null)
                {
                    String added = line.substring(1);
                    int index = updated.indexOf(pendingRemoved);
                    if (index >= 0)
                    {
                        updated = updated.substring(0, index) + added + updated.substring(index + pendingRemoved.length());
                        replacedCount++;
                    }
                    pendingRemoved = null;
                    continue;
                }
                if (!line.isBlank())
                {
                    pendingRemoved = null;
                }
            }
            if (replacedCount == 0)
            {
                throw new IllegalArgumentException("Simple replacement diff did not match any content.");
            }
            return updated;
        }
    }
}
