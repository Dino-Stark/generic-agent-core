package stark.dataworks.coderaider.gundam.core.exceptions;

/**
 * AgentsException implements core runtime responsibilities.
 */
public class AgentsException extends RuntimeException
{

    /**
     * Creates a new AgentsException instance.
     * @param message conversation message.
     */
    public AgentsException(String message)
    {
        super(message);
    }

    /**
     * Creates a new AgentsException instance.
     * @param message conversation message.
     * @param cause root cause exception.
     */
    public AgentsException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
