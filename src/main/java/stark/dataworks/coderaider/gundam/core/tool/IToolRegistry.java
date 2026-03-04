package stark.dataworks.coderaider.gundam.core.tool;

import java.util.Optional;

/**
 * IToolRegistry implements tool contracts, schema metadata, and executable tool registration.
 */
public interface IToolRegistry
{

    /**
     * Registers this value for later lookup and reuse.
     * @param tool tool instance.
     */
    void register(ITool tool);

    /**
     * Returns the value requested by the caller from this IToolRegistry.
     * @param toolName tool name.
     * @return Optional itool value.
     */

    Optional<ITool> get(String toolName);
}
