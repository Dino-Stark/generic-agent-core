package stark.dataworks.coderaider.gundam.core.tool;

import java.util.Map;

/**
 * ITool implements tool contracts, schema metadata, and executable tool registration.
 */
public interface ITool
{

    /**
     * Returns definition metadata for this component.
     * @return tool definition result.
     */
    ToolDefinition definition();

    /**
     * Runs the primary execution flow, coordinating model/tool work and runtime policies.
     * @param input input payload.
     * @return Result text returned by this operation.
     */

    String execute(Map<String, Object> input);
}
