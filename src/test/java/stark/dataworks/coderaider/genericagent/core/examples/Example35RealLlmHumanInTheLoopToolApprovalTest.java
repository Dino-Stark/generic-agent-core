package stark.dataworks.coderaider.genericagent.core.examples;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import stark.dataworks.coderaider.genericagent.core.agent.AgentDefinition;
import stark.dataworks.coderaider.genericagent.core.agent.AgentRegistry;
import stark.dataworks.coderaider.genericagent.core.approval.HumanInTheLoopToolApprovalPolicy;
import stark.dataworks.coderaider.genericagent.core.approval.IToolApprovalPolicy;
import stark.dataworks.coderaider.genericagent.core.context.ContextResult;
import stark.dataworks.coderaider.genericagent.core.llmspi.adapter.OpenAiCompatibleConfiguration;
import stark.dataworks.coderaider.genericagent.core.llmspi.adapter.OpenAiCompatibleLlmClient;
import stark.dataworks.coderaider.genericagent.core.runner.AgentRunner;
import stark.dataworks.coderaider.genericagent.core.runner.RunConfiguration;
import stark.dataworks.coderaider.genericagent.core.tool.ITool;
import stark.dataworks.coderaider.genericagent.core.tool.ToolDefinition;
import stark.dataworks.coderaider.genericagent.core.tool.ToolParameterSchema;
import stark.dataworks.coderaider.genericagent.core.tool.ToolRegistry;

/**
 * 35) Real OWIWO OpenAI-compatible LLM using a human-in-the-loop policy before tool execution.
 * <p>
 * Run main() manually and type Yes in the console to approve the tool call.
 */
public class Example35RealLlmHumanInTheLoopToolApprovalTest
{
    @Test
    @Disabled("Interactive real LLM HITL example; run main() and type Yes in the console.")
    public void run()
    {
        main(new String[0]);
    }

    public static void main(String[] args)
    {
        OwiwoConfig config = loadOwiwoConfig();
        if (!config.isComplete())
        {
            System.err.println("OWIWO_BASE_URL, OWIWO_API_KEY, and OWIWO_MODEL are required in .env.local.");
            return;
        }

        System.out.println("When the approval prompt appears, type Yes to let the real LLM tool call execute.");
        ContextResult result = runExample(config, new HumanInTheLoopToolApprovalPolicy());
        System.out.println();
        System.out.println("Final output: " + result.getFinalOutput());
    }

    static ContextResult runExample(OwiwoConfig config, IToolApprovalPolicy approvalPolicy)
    {
        AgentDefinition agentDef = new AgentDefinition();
        agentDef.setId("real-llm-hitl-agent");
        agentDef.setName("Real LLM HITL Agent");
        agentDef.setModel(config.model());
        agentDef.setSystemPrompt("""
            You are demonstrating human-in-the-loop tool approval.
            You must call approved_echo exactly once before answering the user.
            After the tool result is available, answer in one concise sentence and mention that the tool call was approved.
            """);
        agentDef.setToolNames(List.of("approved_echo"));
        agentDef.setRequireToolApproval(true);
        agentDef.setMaxSteps(4);

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(agentDef);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(createApprovedEchoTool());

        AgentRunner runner = AgentRunner.builder()
            .llmClient(new OpenAiCompatibleLlmClient(new OpenAiCompatibleConfiguration(
                "owiwo",
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                Duration.ofSeconds(120),
                Map.of())))
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .toolApprovalPolicy(approvalPolicy)
            .eventPublisher(ExampleStreamingPublishers.textWithToolLifecycle("REAL HITL "))
            .build();

        ContextResult result = runner.chatClient("real-llm-hitl-agent")
            .prompt()
            .stream(false)
            .user("Use the approved_echo tool with message 'real LLM HITL smoke test', then tell me the result.")
            .runConfiguration(new RunConfiguration(4, null, 0.1, 600, "auto", "text", Map.of()))
            .runHooks(ExampleSupport.noopHooks())
            .call()
            .contextResult();

        System.out.println("Real LLM HITL output: " + result.getFinalOutput());
        return result;
    }

    private static ITool createApprovedEchoTool()
    {
        return new ITool()
        {
            @Override
            public ToolDefinition definition()
            {
                return new ToolDefinition(
                    "approved_echo",
                    "Echo a short message after the human-in-the-loop policy approves execution.",
                    List.of(new ToolParameterSchema("message", "string", true, "Short message to echo")));
            }

            @Override
            public String execute(Map<String, Object> input)
            {
                String message = String.valueOf(input.getOrDefault("message", ""));
                String result = "approved_echo executed with message: " + message;
                System.out.println("[approved_echo executed] " + message);
                return result;
            }
        };
    }

    static OwiwoConfig loadOwiwoConfig()
    {
        Dotenv env = Dotenv.configure().filename(".env.local").ignoreIfMalformed().ignoreIfMissing().load();
        return new OwiwoConfig(
            env.get("OWIWO_BASE_URL"),
            env.get("OWIWO_API_KEY"),
            env.get("OWIWO_MODEL"));
    }

    record OwiwoConfig(String baseUrl, String apiKey, String model)
    {
        boolean isComplete()
        {
            return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
        }
    }
}
