package stark.dataworks.coderaider.gundam.core.tool.builtin;

import java.util.Map;
import java.util.function.Function;

import stark.dataworks.coderaider.gundam.core.tool.ToolCategory;
import stark.dataworks.coderaider.gundam.core.tool.ToolDefinition;

/**
 * FunctionTool implements tool contracts, schema metadata, and executable tool registration.
 */
public class FunctionTool extends AbstractBuiltinTool
{

    /**
     * Function implementation invoked by this tool.
     */
    private final Function<Map<String, Object>, String> function;

    /**
     * Creates a new FunctionTool instance.
     * @param definition definition object.
     * @param Function<Map<String function<map<string.
     * @param Object> object>.
     * @param function function.
     */
    public FunctionTool(ToolDefinition definition, Function<Map<String, Object>, String> function)
    {
        super(definition, ToolCategory.FUNCTION);
        this.function = function;
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
        return function.apply(input);
    }
}
