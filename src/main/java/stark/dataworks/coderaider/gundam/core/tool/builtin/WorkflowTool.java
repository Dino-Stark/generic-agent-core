package stark.dataworks.coderaider.gundam.core.tool.builtin;

import stark.dataworks.coderaider.gundam.core.tool.ToolCategory;
import stark.dataworks.coderaider.gundam.core.tool.ToolDefinition;
import stark.dataworks.coderaider.gundam.core.workflow.WorkflowExecutionResult;
import stark.dataworks.coderaider.gundam.core.workflow.WorkflowExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * WorkflowTool implements tool contracts, schema metadata, and executable tool registration.
 */
public class WorkflowTool extends AbstractBuiltinTool
{
    /**
     * Executor used to run workflow DAGs as a tool.
     */
    private final WorkflowExecutor workflowExecutor;

    /**
     * Creates a new WorkflowTool instance.
     * @param definition definition object.
     * @param workflowExecutor workflow executor.
     */
    public WorkflowTool(ToolDefinition definition, WorkflowExecutor workflowExecutor)
    {
        super(definition, ToolCategory.FUNCTION);
        this.workflowExecutor = workflowExecutor;
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
        WorkflowExecutionResult result = workflowExecutor.execute(new HashMap<>(input));
        return result.getFinalOutput();
    }
}
