package stark.dataworks.coderaider.genericagent.core.examples;

import stark.dataworks.coderaider.genericagent.core.agent.IAgentRegistry;
import stark.dataworks.coderaider.genericagent.core.llmspi.ILlmClient;
import stark.dataworks.coderaider.genericagent.core.runner.AgentRunner;
import stark.dataworks.coderaider.genericagent.core.runner.IRunHooks;
import stark.dataworks.coderaider.genericagent.core.session.ISessionStore;
import stark.dataworks.coderaider.genericagent.core.session.InMemorySessionStore;
import stark.dataworks.coderaider.genericagent.core.streaming.RunEventPublisher;
import stark.dataworks.coderaider.genericagent.core.tool.IToolRegistry;

public final class ExampleSupport
{
    private ExampleSupport()
    {
    }

    static AgentRunner runner(ILlmClient client, IToolRegistry toolRegistry, IAgentRegistry agentRegistry, ISessionStore sessionStore)
    {
        return AgentRunner.builder()
            .llmClient(client)
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .sessionStore(sessionStore == null ? new InMemorySessionStore() : sessionStore)
            .build();
    }

    static AgentRunner runnerWithPublisher(ILlmClient client, IToolRegistry toolRegistry, IAgentRegistry agentRegistry, ISessionStore sessionStore, RunEventPublisher publisher)
    {
        return AgentRunner.builder()
            .llmClient(client)
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .sessionStore(sessionStore == null ? new InMemorySessionStore() : sessionStore)
            .eventPublisher(publisher)
            .build();
    }


    public static IRunHooks noopHooks()
    {
        return new IRunHooks()
        {
        };
    }
}
