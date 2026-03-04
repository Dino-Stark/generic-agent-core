package stark.dataworks.coderaider.gundam.core.exceptions;

/**
 * ToolExecutionFailureException implements core runtime responsibilities.
 */
public class ToolExecutionFailureException extends AgentsException
{

    /**
     * Creates a new ToolExecutionFailureException instance.
     * @param tool tool instance.
     * @param cause root cause exception.
     */
    public ToolExecutionFailureException(String tool, Throwable cause)
    {
        super("Tool execution failed: " + tool, cause);
    }
}
