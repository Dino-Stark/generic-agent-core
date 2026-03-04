package stark.dataworks.coderaider.gundam.core.tool.builtin;

import java.util.Map;

import stark.dataworks.coderaider.gundam.core.tool.ToolCategory;
import stark.dataworks.coderaider.gundam.core.tool.ToolDefinition;

/**
 * FileSearchTool implements tool contracts, schema metadata, and executable tool registration.
 */
public class FileSearchTool extends AbstractBuiltinTool
{

    /**
     * Creates a new FileSearchTool instance.
     * @param definition definition object.
     */
    public FileSearchTool(ToolDefinition definition)
    {
        super(definition, ToolCategory.FILE_SEARCH);
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
        return "FileSearch(simulated): " + input.getOrDefault("path", "") + " q=" + input.getOrDefault("query", "");
    }
}
