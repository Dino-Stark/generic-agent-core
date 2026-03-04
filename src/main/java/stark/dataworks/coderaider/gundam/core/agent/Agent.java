package stark.dataworks.coderaider.gundam.core.agent;

import java.util.Objects;

/**
 * Agent implements agent definitions and lookup used by runners and handoff resolution.
 */
public class Agent implements IAgent
{

    /**
     * Immutable definition object that configures this runtime instance.
     */
    private final AgentDefinition definition;

    /**
     * Creates a new Agent instance.
     * @param definition definition object.
     */
    public Agent(AgentDefinition definition)
    {
        definition.validate();
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    /**
     * Returns definition metadata for this component.
     * @return agent definition result.
     */
    @Override
    public AgentDefinition definition()
    {
        return definition;
    }
}
