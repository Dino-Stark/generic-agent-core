package stark.dataworks.coderaider.gundam.core.tool.builtin;

import java.util.Map;

import stark.dataworks.coderaider.gundam.core.tool.ToolCategory;
import stark.dataworks.coderaider.gundam.core.tool.ToolDefinition;

/**
 * WebSearchTool implements tool contracts, schema metadata, and executable tool registration.
 */
public class WebSearchTool extends AbstractBuiltinTool
{

    /**
     * Creates a new WebSearchTool instance.
     * @param definition definition object.
     */
    public WebSearchTool(ToolDefinition definition)
    {
        super(definition, ToolCategory.WEB_SEARCH);
    }

    /**
     * Executes the operation and returns its output.
     * @param Map<String map<string.
     * @param input input payload.
     * @return Result text returned by this operation.
     */
    @Override
    public String execute(Map<String, Object> input)
    {
        return "WebSearch(simulated): " + input.getOrDefault("query", "");
    }
}
