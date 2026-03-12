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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 33) Planner-first ReAct debug workflow with 4 agents: understand -> plan -> execute -> summarize.
 */
public class Example33PlannerReActDebugFixTest
{
    private static final String MODEL = "Qwen/Qwen3-4B";
    private static final Path INPUT_FILE_1 = Path.of("src", "test", "resources", "inputs", "BuggyCalcService.java");
    private static final Path INPUT_FILE_2 = Path.of("src", "test", "resources", "inputs", "BuggyOrderTotalApp.java");
    private static final RunConfiguration EXAMPLE_RUN_CONFIGURATION =
        new RunConfiguration(4, null, 0.0, 900, "auto", "text", Map.of());

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
        Path workspace = Path.of("src", "test", "resources", "outputs", "react-agent", "example33");
        resetWorkspace(workspace);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(createShellTool());
        toolRegistry.register(new ApplyPatchTool(new FileSystemEditor(workspace), false));

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(createUnderstandingAgent(runtimeOs, workspace));
        agentRegistry.register(createPlannerAgent(runtimeOs, workspace));
        agentRegistry.register(createExecutorAgent(runtimeOs, workspace));
        agentRegistry.register(createSummarizerAgent(runtimeOs, workspace));

        AgentRunner runner = AgentRunner.builder()
            .llmClient(new ModelScopeLlmClient(apiKey, MODEL))
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .eventPublisher(ExampleStreamingPublishers.textWithToolLifecycle("ReAct33 "))
            .build();

        String userRequest = "Fix both Java files quickly and provide a short summary at the end.";
        ContextResult understanding = runner.chatClient("react30-understanding").prompt().stream(true).user(userRequest)
            .runConfiguration(EXAMPLE_RUN_CONFIGURATION).runHooks(ExampleSupport.noopHooks()).call().contextResult();

        ContextResult planning = runner.chatClient("react30-planner").prompt().stream(true)
            .user(understanding.getFinalOutput())
            .runConfiguration(EXAMPLE_RUN_CONFIGURATION).runHooks(ExampleSupport.noopHooks()).call().contextResult();

        String verifyOutput = runBehaviorVerification(runtimeOs, workspace);
        ContextResult execution = null;
        for (int attempt = 1; attempt <= 6; attempt++)
        {
            execution = runner.chatClient("react30-executor").prompt().stream(true)
                .user(buildExecutorPrompt(runtimeOs, workspace, planning.getFinalOutput(), verifyOutput, attempt))
                .runConfiguration(EXAMPLE_RUN_CONFIGURATION).runHooks(ExampleSupport.noopHooks()).call().contextResult();
            verifyOutput = runBehaviorVerification(runtimeOs, workspace);
            if (verifyOutput.contains("BEHAVIOR_OK"))
            {
                break;
            }
        }

        ContextResult summary = null;
        if (!userRequest.toLowerCase(Locale.ROOT).contains("no summary"))
        {
            summary = runner.chatClient("react30-summarizer").prompt().stream(true)
                .user("Plan:\n" + planning.getFinalOutput() + "\nExecution:\n" + execution.getFinalOutput() + "\nVerify:\n" + verifyOutput)
                .runConfiguration(EXAMPLE_RUN_CONFIGURATION).runHooks(ExampleSupport.noopHooks()).call().contextResult();
        }

        Assertions.assertTrue(verifyOutput.contains("BEHAVIOR_OK"), 
            "Agent must fix the bugs successfully. Verification output: " + verifyOutput);
        Assertions.assertNotNull(understanding.getFinalOutput());
        Assertions.assertNotNull(planning.getFinalOutput());
        Assertions.assertNotNull(execution.getFinalOutput());
        
        if (summary != null)
        {
            String summaryText = summary.getFinalOutput();
            Assertions.assertFalse(summaryText.isBlank());
            Assertions.assertTrue(summaryText.contains("Problem") || summaryText.contains("Fix") || summaryText.contains("Verification"),
                "Expected summary with relevant sections. Got: " + summaryText);
        }
        
        long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
        Assertions.assertTrue(elapsedSeconds <= 150, "Expected runtime (<=150s) but took " + elapsedSeconds + "s");
    }

    private static String buildExecutorPrompt(RuntimeOs runtimeOs, Path workspace, String plan, String verifyOutput, int attempt)
    {
        return """
            Attempt %d to fix the bugs.
            
            Fix plan:
            %s
            
            Current verification status:
            %s
            
            Verify command: %s
            Target: output should contain BEHAVIOR_OK
            
            OS: %s
            Workspace: %s
            """.formatted(attempt, plan, verifyOutput.trim(), runtimeOs.verifyCommand(workspace), 
                runtimeOs.displayName, workspace);
    }

    private static AgentDefinition createUnderstandingAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react30-understanding");
        def.setName("Task Understanding Agent");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            You are a task analyzer. Quickly understand the debugging task.
            
            Identify:
            - Which files need to be fixed
            - What the expected behavior is
            - How to verify the fix
            
            OS: %s
            Workspace: %s
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("Briefly state: files, expected behavior, verification method. Keep it under 4 lines.");
        def.setToolNames(List.of("local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static AgentDefinition createPlannerAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react30-planner");
        def.setName("Step Planner Agent");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            You are a debugging planner. Create a concise fix plan.
            
            Process:
            1. Read the buggy files
            2. Identify the bugs by analyzing code logic
            3. List the fixes needed
            
            OS: %s
            Workspace: %s
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("Read files → Identify bugs → List fixes in numbered steps. Keep plan under 6 steps.");
        def.setToolNames(List.of("local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static AgentDefinition createExecutorAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react30-executor");
        def.setName("Step Executor Agent");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            You are a code fixer. Execute the fix plan and verify it works.
            
            Process:
            1. Apply patches to fix the bugs
            2. Verify the fixes work correctly
            3. Iterate if needed
            
            OS: %s
            Workspace: %s
            Verify: %s
            """.formatted(runtimeOs.displayName, workspace, runtimeOs.verifyCommand(workspace)));
        def.setReactInstructions("Apply patches → Verify → Retry if fails → Done. No explanations until verification passes.");
        def.setToolNames(List.of("apply_patch", "local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static AgentDefinition createSummarizerAgent(RuntimeOs runtimeOs, Path workspace)
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("react30-summarizer");
        def.setName("Task Summary Agent");
        def.setModel(MODEL);
        def.setReactEnabled(true);
        def.setSystemPrompt("""
            You are a result summarizer. Briefly report the debugging outcome.
            
            OS: %s
            Workspace: %s
            """.formatted(runtimeOs.displayName, workspace));
        def.setReactInstructions("Summarize in 2-3 lines: Problem, Fix, Verification result.");
        def.setToolNames(List.of("local_shell"));
        def.setModelProviderOptions(Map.of("working_directory", workspace.toString()));
        def.setModelReasoning(Map.of("effort", "low"));
        return def;
    }

    private static void resetWorkspace(Path workspace) throws IOException
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
        Files.writeString(workspace.resolve("BuggyCalcService.java"), Files.readString(INPUT_FILE_1));
        Files.writeString(workspace.resolve("BuggyOrderTotalApp.java"), Files.readString(INPUT_FILE_2));
    }

    private static LocalShellTool createShellTool()
    {
        return new LocalShellTool(new ToolDefinition(
            "local_shell",
            "Execute a local shell command and return stdout/stderr.",
            List.of(new ToolParameterSchema("command", "string", true, "Shell command to execute"))));
    }

    private static String runBehaviorVerification(RuntimeOs runtimeOs, Path workspace)
    {
        ProcessBuilder builder = switch (runtimeOs)
        {
            case WINDOWS -> new ProcessBuilder("cmd", "/c",
                "cd /d \"" + workspace + "\" && javac BuggyCalcService.java BuggyOrderTotalApp.java && java BuggyOrderTotalApp");
            case MACOS, LINUX -> new ProcessBuilder("bash", "-lc",
                "cd '" + workspace + "' && javac BuggyCalcService.java BuggyOrderTotalApp.java && java BuggyOrderTotalApp");
        };
        builder.redirectErrorStream(true);
        try
        {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output;
        }
        catch (Exception ex)
        {
            return "VERIFY_ERROR: " + ex.getMessage();
        }
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
        WINDOWS("Windows"),
        MACOS("macOS"),
        LINUX("Linux");

        private final String displayName;

        RuntimeOs(String displayName)
        {
            this.displayName = displayName;
        }

        private String verifyCommand(Path workspace)
        {
            return switch (this)
            {
                case WINDOWS -> "cd /d \"" + workspace + "\" && javac BuggyCalcService.java BuggyOrderTotalApp.java && java BuggyOrderTotalApp";
                case MACOS, LINUX -> "cd '" + workspace + "' && javac BuggyCalcService.java BuggyOrderTotalApp.java && java BuggyOrderTotalApp";
            };
        }
    }

    private static final class FileSystemEditor implements IApplyPatchEditor
    {
        private final Path workspaceRoot;

        private FileSystemEditor(Path workspaceRoot)
        {
            this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        }

        @Override
        public ApplyPatchResult createFile(ApplyPatchOperation operation)
        {
            return upsert(operation, true);
        }

        @Override
        public ApplyPatchResult updateFile(ApplyPatchOperation operation)
        {
            return upsert(operation, false);
        }

        @Override
        public ApplyPatchResult deleteFile(ApplyPatchOperation operation)
        {
            if (operation == null || operation.getPath() == null)
            {
                return ApplyPatchResult.failed("Invalid operation");
            }
            Path target = workspaceRoot.resolve(operation.getPath()).normalize();
            if (!target.startsWith(workspaceRoot))
            {
                return ApplyPatchResult.failed("Path escapes workspace");
            }
            try
            {
                Files.deleteIfExists(target);
                return ApplyPatchResult.completed("Deleted " + operation.getPath());
            }
            catch (IOException ex)
            {
                return ApplyPatchResult.failed("Delete failed: " + ex.getMessage());
            }
        }

        private ApplyPatchResult upsert(ApplyPatchOperation operation, boolean createMode)
        {
            if (operation == null || operation.getPath() == null)
            {
                return ApplyPatchResult.failed("Invalid operation");
            }
            Path target = workspaceRoot.resolve(operation.getPath()).normalize();
            if (!target.startsWith(workspaceRoot))
            {
                return ApplyPatchResult.failed("Path escapes workspace");
            }
            try
            {
                Files.createDirectories(target.getParent());
                if (createMode)
                {
                    String content = ApplyPatchTool.applyCreateDiff(operation.getDiff());
                    Files.writeString(target, content);
                    return ApplyPatchResult.completed("Created " + operation.getPath());
                }
                String source = Files.exists(target) ? Files.readString(target) : "";
                String patched = ApplyPatchTool.applyDiff(source, operation.getDiff());
                Files.writeString(target, patched);
                return ApplyPatchResult.completed("Updated " + operation.getPath());
            }
            catch (Exception ex)
            {
                return ApplyPatchResult.failed("Patch failed: " + ex.getMessage());
            }
        }
    }
}
