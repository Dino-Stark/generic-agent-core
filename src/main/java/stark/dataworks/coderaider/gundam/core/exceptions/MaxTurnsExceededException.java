package stark.dataworks.coderaider.gundam.core.exceptions;

/**
 * MaxTurnsExceededException implements core runtime responsibilities.
 */
public class MaxTurnsExceededException extends AgentsException
{

    /**
     * Creates a new MaxTurnsExceededException instance.
     * @param maxTurns maximum turn limit.
     */
    public MaxTurnsExceededException(int maxTurns)
    {
        super("Max turns exceeded: " + maxTurns);
    }
}
