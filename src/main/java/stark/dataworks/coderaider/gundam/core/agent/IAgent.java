package stark.dataworks.coderaider.gundam.core.agent;

/**
 * IAgent implements agent definitions and lookup used by runners and handoff resolution.
 */
public interface IAgent
{
    /**
     * Returns definition metadata for this component.
     * @return agent definition result.
     */
    AgentDefinition definition();
}
