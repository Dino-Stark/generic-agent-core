package stark.dataworks.coderaider.genericagent.core.examples;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import stark.dataworks.coderaider.genericagent.core.agent.AgentDefinition;
import stark.dataworks.coderaider.genericagent.core.agent.AgentRegistry;
import stark.dataworks.coderaider.genericagent.core.approval.HumanInTheLoopToolApprovalPolicy;
import stark.dataworks.coderaider.genericagent.core.context.ContextItem;
import stark.dataworks.coderaider.genericagent.core.context.ContextResult;
import stark.dataworks.coderaider.genericagent.core.llmspi.ILlmClient;
import stark.dataworks.coderaider.genericagent.core.llmspi.LlmRequest;
import stark.dataworks.coderaider.genericagent.core.llmspi.LlmResponse;
import stark.dataworks.coderaider.genericagent.core.metrics.TokenUsage;
import stark.dataworks.coderaider.genericagent.core.model.Role;
import stark.dataworks.coderaider.genericagent.core.model.ToolCall;
import stark.dataworks.coderaider.genericagent.core.runner.AgentRunner;
import stark.dataworks.coderaider.genericagent.core.runner.RunConfiguration;
import stark.dataworks.coderaider.genericagent.core.tool.ITool;
import stark.dataworks.coderaider.genericagent.core.tool.ToolDefinition;
import stark.dataworks.coderaider.genericagent.core.tool.ToolParameterSchema;
import stark.dataworks.coderaider.genericagent.core.tool.ToolRegistry;

/**
 * 34) Human-in-the-loop tool approval from the console.
 * <p>
 * Run main() from an IDE or terminal, then type Yes when the tool approval prompt appears.
 */
public class Example34HumanInTheLoopToolApprovalTest
{
    @Test
    @Disabled("Interactive console example; run main() manually to type Yes.")
    public void run()
    {
        runExample();
    }

    public static void main(String[] args)
    {
        runExample();
    }

    private static void runExample()
    {
        AgentDefinition agentDef = new AgentDefinition();
        agentDef.setId("hitl-agent");
        agentDef.setName("HITL Agent");
        agentDef.setModel("mock-model");
        agentDef.setSystemPrompt("Use display_message when the user asks to display a message.");
        agentDef.setToolNames(List.of("display_message"));
        agentDef.setRequireToolApproval(true);
        agentDef.setMaxSteps(3);

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(agentDef);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(createDisplayMessageTool());

        AgentRunner runner = AgentRunner.builder()
            .llmClient(new ScriptedToolCallClient())
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .toolApprovalPolicy(new HumanInTheLoopToolApprovalPolicy())
            .eventPublisher(ExampleStreamingPublishers.textWithToolLifecycle("HITL "))
            .build();

        System.out.println("When prompted, type Yes to approve the tool call.");
        ContextResult result = runner.run(agentDef, "Display a greeting message with a tool.", new RunConfiguration(3, null, 0.1, 512, "auto", "text", Map.of()), ExampleSupport.noopHooks());
        System.out.println();
        System.out.println("Final output: " + result.getFinalOutput());
    }

    private static ITool createDisplayMessageTool()
    {
        return new ITool()
        {
            @Override
            public ToolDefinition definition()
            {
                return new ToolDefinition(
                    "display_message",
                    "Display a message after human approval.",
                    List.of(new ToolParameterSchema("message", "string", true, "Message to display")));
            }

            @Override
            public String execute(Map<String, Object> input)
            {
                String message = String.valueOf(input.getOrDefault("message", ""));
                System.out.println("[display_message executed] " + message);
                return "Displayed message: " + message;
            }
        };
    }

    private static final class ScriptedToolCallClient implements ILlmClient
    {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public LlmResponse chat(LlmRequest request)
        {
            return latestToolResult(request)
                .map(toolResult -> new LlmResponse("Tool execution was approved. " + toolResult.getContent(), List.of(), null, new TokenUsage(1, 1)))
                .orElseGet(() ->
                {
                    if (calls.getAndIncrement() == 0)
                    {
                        return new LlmResponse("", List.of(new ToolCall("display_message", Map.of("message", "Hello from a HITL-approved tool."))), null, new TokenUsage(1, 1));
                    }
                    return new LlmResponse("Tool execution was not approved, so no tool result was produced.", List.of(), null, new TokenUsage(1, 1));
                });
        }

        private java.util.Optional<ContextItem> latestToolResult(LlmRequest request)
        {
            List<ContextItem> messages = request.getMessages();
            for (int i = messages.size() - 1; i >= 0; i--)
            {
                ContextItem message = messages.get(i);
                if (message.getRole() == Role.TOOL)
                {
                    return java.util.Optional.of(message);
                }
            }
            return java.util.Optional.empty();
        }
    }
}
