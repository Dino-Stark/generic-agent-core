package stark.dataworks.coderaider.genericagent.core.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import stark.dataworks.coderaider.genericagent.core.agent.AgentDefinition;
import stark.dataworks.coderaider.genericagent.core.agent.AgentRegistry;
import stark.dataworks.coderaider.genericagent.core.context.ContextResult;
import stark.dataworks.coderaider.genericagent.core.events.RunEventType;
import stark.dataworks.coderaider.genericagent.core.guardrail.GuardrailEngine;
import stark.dataworks.coderaider.genericagent.core.handoff.HandoffRouter;
import stark.dataworks.coderaider.genericagent.core.hooks.HookManager;
import stark.dataworks.coderaider.genericagent.core.llmspi.LlmResponse;
import stark.dataworks.coderaider.genericagent.core.memory.context.ContextServiceContextManager;
import stark.dataworks.coderaider.genericagent.core.memory.context.InMemoryContextServiceMemoryStore;
import stark.dataworks.coderaider.genericagent.core.memory.policy.CompositeMemoryLifecyclePolicy;
import stark.dataworks.coderaider.genericagent.core.memory.policy.SlidingWindowMemoryPolicy;
import stark.dataworks.coderaider.genericagent.core.memory.policy.SummarizingMemoryPolicy;
import stark.dataworks.coderaider.genericagent.core.metrics.TokenUsage;
import stark.dataworks.coderaider.genericagent.core.output.OutputSchemaRegistry;
import stark.dataworks.coderaider.genericagent.core.output.OutputValidator;
import stark.dataworks.coderaider.genericagent.core.session.InMemorySessionStore;
import stark.dataworks.coderaider.genericagent.core.streaming.RunEventPublisher;
import stark.dataworks.coderaider.genericagent.core.tool.ToolRegistry;
import stark.dataworks.coderaider.genericagent.core.tracing.NoopTraceProvider;
import stark.dataworks.coderaider.genericagent.core.approval.AllowAllToolApprovalPolicy;
import stark.dataworks.coderaider.genericagent.core.context.DefaultContextBuilder;

class MemoryRoadmapFeaturesTest
{
    private static AgentRegistry registry(AgentDefinition def)
    {
        AgentRegistry registry = new AgentRegistry();
        registry.register(def);
        return registry;
    }

    @Test
    void usesContextServiceMemoryAndEmitsMemoryTelemetry()
    {
        AgentDefinition def = new AgentDefinition();
        def.setId("roadmap");
        def.setName("roadmap-agent");
        def.setModel("demo-model");
        def.setSystemPrompt("Be helpful");

        AgentRunner runner = new AgentRunner(
            request -> new LlmResponse("ok", List.of(), null, new TokenUsage(1, 1)),
            new ToolRegistry(),
            registry(def),
            new DefaultContextBuilder(),
            new HookManager(),
            new GuardrailEngine(),
            new HandoffRouter(),
            new InMemorySessionStore(),
            new NoopTraceProvider(),
            new AllowAllToolApprovalPolicy(),
            new OutputSchemaRegistry(),
            new OutputValidator(),
            new RunEventPublisher());

        InMemoryContextServiceMemoryStore store = new InMemoryContextServiceMemoryStore();
        ContextServiceContextManager memory = new ContextServiceContextManager("agent-a", "session-a", store);
        RunConfiguration config = new RunConfiguration(8, "session-a", 0.2, 128, "auto", "text", Map.of(), null, null, memory,
            CompositeMemoryLifecyclePolicy.of(new SummarizingMemoryPolicy(4), new SlidingWindowMemoryPolicy(4)));

        ContextResult result = runner.run(def, "hello", config, new IRunHooks()
        {
        });

        assertEquals("ok", result.getFinalOutput());
        assertTrue(result.getEvents().stream().anyMatch(e -> e.getType() == RunEventType.MEMORY_READ));
        assertTrue(result.getEvents().stream().anyMatch(e -> e.getType() == RunEventType.MEMORY_WRITE));
        assertTrue(store.read("agent-a", "session-a").items().size() >= 2);
    }
}
