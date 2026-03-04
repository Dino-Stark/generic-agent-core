package stark.dataworks.coderaider.gundam.core.exceptions;

/**
 * HandoffDeniedException implements core runtime responsibilities.
 */
public class HandoffDeniedException extends AgentsException
{

    /**
     * Creates a new HandoffDeniedException instance.
     * @param from from.
     * @param to to.
     */
    public HandoffDeniedException(String from, String to)
    {
        super("Handoff denied from " + from + " to " + to);
    }
}
