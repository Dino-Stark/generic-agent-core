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
import stark.dataworks.coderaider.genericagent.core.tool.builtin.LocalShellTool;
import stark.dataworks.coderaider.genericagent.core.tool.builtin.ApplyPatchTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * 24) ReAct-style debug/fix workflow using a multi-agent topology.
 * <p>
 * Pattern:
 * - Coordinator: decides delegation order.
 * - Investigator: inspects source + compiler output.
 * - Fixer: applies patch and recompiles iteratively.
 * - Reviewer: validates result and summarizes.
 */
public class Example24ReActAgentDebugFixTest
{
    private static final String MODEL = "Qwen/Qwen3-4B";
    private static final Path INPUT_BUG_FILE = Path.of("src", "test", "resources", "inputs", "BuggyCalculator.java");
    private static final Path INPUT_VERIFIER_FILE = Path.of("src", "test", "resources", "inputs", "BuggyCalculatorVerifier.java");
    private static final RunConfiguration EXAMPLE_RUN_CONFIGURATION =
        new RunConfiguration(4, null, 0.0, 768, "auto", "text", Map.of());

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
        Path workspace = Path.of("src", "test", "resources", "outputs", "react-agent", "example24");
        Files.createDirectories(workspace);
        Path targetBugFile = workspace.resolve("BuggyCalculator.java");
        resetBuggySource(targetBugFile);
        stageVerifierSource(workspace.resolve("BuggyCalculatorVerifier.java"));

        AgentRegistry agentRegistry = createAgentRegistry(runtimeOs, workspace);
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(createShellTool());
        toolRegistry.register(new ApplyPatchTool(new FileSystemEditor(workspace), false));

        AgentRunner runner = AgentRunner.builder()
            .llmClient(new ModelScopeLlmClient(apiKey, MODEL))
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .eventPublisher(ExampleStreamingPublishers.textWithToolLifecycle("ReAct "))
            .build();

        ContextResult coordinatorPlan = runner.chatClient("react-coordinator")
            .prompt()
            .stream(true)
            .user(buildCoordinatorUserPrompt(runtimeOs, workspace))
            .runConfiguration(EXAMPLE_RUN_CONFIGURATION)
            .runHooks(ExampleSupport.noopHooks())
            .call()
            .contextResult();

        ContextResult investigatorResult = runner.chatClient("react-investigator")
            .prompt()
            .stream(true)
            .user(buildInvestigatorPrompt(runtimeOs, workspace))
            .runConfiguration(EXAMPLE_RUN_CONFIGURATION)
            .runHooks(ExampleSupport.noopHooks())
            .call()
            .contextResult();

        ContextResult fixerResult = null;
        ContextResult reviewerResult = null;
        for (int attempt = 1; attempt <= 5; attempt++)
        {
            String sourceSnapshot = Files.readString(targetBugFile);
            fixerResult = runner.chatClient("react-fixer")
                .prompt()
                .stream(true)
                .user(buildFixerPrompt(runtimeOs, workspace, investigatorResult.getFinalOutput(), sourceSnapshot, attempt))
                .runConfiguration(EXAMPLE_RUN_CONFIGURATION)
                .runHooks(ExampleSupport.noopHooks())
                .call()
                .contextResult();

            reviewerResult = runner.chatClient("react-reviewer")
                .prompt()
                .stream(true)
                .user(buildReviewerPrompt(runtimeOs, workspace, fixerResult.getFinalOutput()))
                .runConfiguration(EXAMPLE_RUN_CONFIGURATION)
                .runHooks(ExampleSupport.noopHooks())
                .call()
                .contextResult();

            String behaviorNow = runBehaviorVerification(workspace);
            if (behaviorNow.contains("BEHAVIOR_OK"))
            {
                break;
            }
        }

        Assertions.assertNotNull(coordinatorPlan, "Expected plan output from coordinator");
        Assertions.assertNotNull(investigatorResult, "Expected investigation output");
        Assertions.assertNotNull(fixerResult, "Expected fixer output");
        Assertions.assertNotNull(reviewerResult, "Expected reviewer output");
        Assertions.assertTrue(Files.exists(targetBugFile), "Expected buggy source in workspace");

        String behaviorOutput = runBehaviorVerification(workspace);
        Assertions.assertTrue(behaviorOutput.contains("BEHAVIOR_OK"), 
            "Agent must fix the bug successfully. Verification output: " + behaviorOutput);
        
        Assertions.assertTrue(reviewerResult.getFinalOutput() != null && !reviewerResult.getFinalOutput().isBlank(), 
            "Expected reviewer summary output");
        
        long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
        Assertions.assertTrue(elapsedSeconds <= 120, "Expected runtime (<=120s) but took " + elapsedSeconds + "s");
    }

    private static AgentRegistry createAgentRegistry(RuntimeOs runtimeOs, Path workspace)
    {
        AgentRegistry registry = new AgentRegistry();
        registry.register(createCoordinatorAgent(runtimeOs, workspace));
        registry.register(createInvestigatorAgent(runtimeOs, workspace));
        registry.register(createFixerAgent(runtimeOs, workspace));
        registry.register(createReviewerAgent(runtimeOs, workspace));
        return registry;
    }

    private static AgentDefinition createCoordinatorAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react-coordinator");
        def.setName("ReAct Coordinator");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            You are the debugging coordinator. Output a 3-step plan only.
            Bug: add(a, b) subtracts instead of adds.
            OS: %s. Workspace: %s
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("Output plan in numbered list. No tool calls.");
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static AgentDefinition createInvestigatorAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react-investigator");
        def.setName("ReAct Investigator");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            Investigate BuggyCalculator.java bug.
            Expected: add(7,5)=12, add(3,-2)=1.
            OS: %s. Workspace: %s
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("1) Read file 2) Compile 3) Report root cause. Keep thoughts brief.");
        def.setToolNames(List.of("local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static AgentDefinition createFixerAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react-fixer");
        def.setName("ReAct Fixer");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            You are a code fixer. Diagnose and fix bugs in Java files.
            
            Process:
            1. Read the buggy file to understand the code
            2. Identify the bug by analyzing the logic
            3. Apply a patch to fix it
            4. Compile and verify the fix works
            
            OS: %s
            Workspace: %s
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("Read code → Find bug → Fix it → Verify → Done. No explanations until fix is verified.");
        def.setToolNames(List.of("apply_patch", "local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static AgentDefinition createReviewerAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react-reviewer");
        def.setName("ReAct Reviewer");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            Verify fix. Required: add(7,5)=12, add(3,-2)=1.
            OS: %s. Workspace: %s
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("Compile and run verifier. Output PASS or FAIL with evidence.");
        def.setToolNames(List.of("local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static String buildCoordinatorUserPrompt(RuntimeOs runtimeOs, Path workspace)
    {
        return """
            Run a multi-agent ReAct bug-fix workflow for BuggyCalculator.java.
            
            The buggy source is in: %s
            Runtime OS: %s
            Known bug: add(a, b) is implemented as subtraction and gives wrong results.
            
            Produce a concise delegation plan for:
            1) Investigator: inspect source and identify root cause
            2) Fixer: patch the code and compile
            3) Reviewer: verify runtime behavior tests pass
            
            Recommended compile command: %s
            """.formatted(workspace.resolve("BuggyCalculator.java"), runtimeOs.displayName, runtimeOs.compileCommand(workspace));
    }


    private static String buildInvestigatorPrompt(RuntimeOs runtimeOs, Path workspace)
    {
        return """
            Investigate the bug in BuggyCalculator.java.
            
            Steps:
            1) Print file content to understand the code
            2) Compile the source and observe any errors
            3) Explain why add(7,5) and add(3,-2) are currently wrong
            4) Provide root cause summary for the fixer
            
            Runtime OS: %s
            Workspace: %s
            File print command: %s
            Compile command: %s
            """.formatted(runtimeOs.displayName, workspace, runtimeOs.printFileCommand(workspace, "BuggyCalculator.java"), runtimeOs.compileCommand(workspace));
    }

    private static String buildFixerPrompt(RuntimeOs runtimeOs, Path workspace, String investigationOutput, String sourceSnapshot, int attempt)
    {
        return """
            Attempt %d to fix BuggyCalculator.java.
            
            Investigation findings:
            %s
            
            Current code:
            %s
            
            Fix the bug and verify with: javac BuggyCalculator.java BuggyCalculatorVerifier.java && java BuggyCalculatorVerifier
            Target: output should contain BEHAVIOR_OK
            
            OS: %s
            Workspace: %s
            """.formatted(attempt, investigationOutput, sourceSnapshot, runtimeOs.displayName, workspace);
    }

    private static String buildReviewerPrompt(RuntimeOs runtimeOs, Path workspace, String fixerOutput)
    {
        return """
            Review the fixer's result and verify correctness.
            
            Required behavior: add(7,5)=12 and add(3,-2)=1
            
            Fixer output:
            %s
            
            Runtime OS: %s
            Workspace: %s
            
            Run verification command:
            %s
            
            Report PASS only if output contains BEHAVIOR_OK, otherwise FAIL with details.
            """.formatted(fixerOutput, runtimeOs.displayName, workspace, runtimeOs.verifyCommand(workspace));
    }

    private static LocalShellTool createShellTool()
    {
        ToolDefinition definition = new ToolDefinition(
            "local_shell",
            "Execute a local shell command and return stdout/stderr.",
            List.of(new ToolParameterSchema("command", "string", true, "Shell command to execute")));
        return new LocalShellTool(definition);
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

    private static void resetBuggySource(Path targetBugFile) throws IOException
    {
        if (!Files.exists(INPUT_BUG_FILE))
        {
            throw new IOException("Missing bug input source: " + INPUT_BUG_FILE);
        }
        Files.writeString(targetBugFile, Files.readString(INPUT_BUG_FILE));
    }

    private static void stageVerifierSource(Path targetVerifierFile) throws IOException
    {
        if (!Files.exists(INPUT_VERIFIER_FILE))
        {
            throw new IOException("Missing verifier input source: " + INPUT_VERIFIER_FILE);
        }
        Files.writeString(targetVerifierFile, Files.readString(INPUT_VERIFIER_FILE));
    }

    private static String runBehaviorVerification(Path workspace)
    {
        Path verifierFile = workspace.resolve("BuggyCalculatorVerifier.java");
        try
        {
            Files.writeString(verifierFile, Files.readString(INPUT_VERIFIER_FILE));
        }
        catch (IOException ex)
        {
            return "Behavior verification setup failed: " + ex.getMessage();
        }

        RuntimeOs runtimeOs = detectRuntimeOs();
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
                "cd /d \"" + workspace + "\" && javac BuggyCalculator.java BuggyCalculatorVerifier.java && java BuggyCalculatorVerifier");
            case MACOS, LINUX -> new ProcessBuilder("bash", "-lc",
                "cd '" + workspace + "' && javac BuggyCalculator.java BuggyCalculatorVerifier.java && java BuggyCalculatorVerifier");
        };
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

        private String compileCommand(Path workspace)
        {
            return switch (this)
            {
                case WINDOWS -> "cmd /c \"cd /d \"\"" + workspace + "\"\" && javac BuggyCalculator.java\"";
                case MACOS, LINUX -> "cd '" + workspace + "' && javac BuggyCalculator.java";
            };
        }

        private String verifyCommand(Path workspace)
        {
            return switch (this)
            {
                case WINDOWS ->
                    "cmd /c \"cd /d \"\"" + workspace + "\"\" && javac BuggyCalculator.java BuggyCalculatorVerifier.java && java BuggyCalculatorVerifier\"";
                case MACOS, LINUX ->
                    "cd '" + workspace + "' && javac BuggyCalculator.java BuggyCalculatorVerifier.java && java BuggyCalculatorVerifier";
            };
        }

        private String printFileCommand(Path workspace, String fileName)
        {
            return switch (this)
            {
                case WINDOWS -> "cmd /c \"cd /d \"\"" + workspace + "\"\" && type " + fileName + "\"";
                case MACOS, LINUX -> "cd '" + workspace + "' && cat " + fileName;
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
