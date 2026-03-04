package stark.dataworks.coderaider.gundam.core.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AgentDefinitionLoader implements agent definitions and lookup used by runners and handoff resolution.
 */
public final class AgentDefinitionLoader
{

    /**
 * ObjectMapper used for JSON serialization/deserialization.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Creates a new AgentDefinitionLoader instance.
     */
    private AgentDefinitionLoader()
    {
    }

    /**
     * Creates json.
     * @param json json.
     * @return agent definition result.
     */
    public static AgentDefinition fromJson(String json)
    {
        try
        {
            AgentDefinition definition = MAPPER.readValue(json, AgentDefinition.class);
            definition.validate();
            return definition;
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException("Invalid agent definition json", e);
        }
    }
}
