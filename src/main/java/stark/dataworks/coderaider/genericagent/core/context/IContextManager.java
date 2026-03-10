package stark.dataworks.coderaider.genericagent.core.context;

import java.util.List;

/**
 * IContextManager implements conversation-context retention and mutation between turns.
 */
public interface IContextManager
{

    /**
     * Returns the context history for this manager backend.
     *
     * @return List of context items.
     */
    List<ContextItem> items();

    /**
     * Appends one context item.
     *
     * @param item context item.
     */
    void append(ContextItem item);

    /**
     * Replaces all context items.
     * Implementations that do not support mutation can throw {@link UnsupportedOperationException}.
     *
     * @param items full normalized context list.
     */
    default void replaceAll(List<ContextItem> items)
    {
        throw new UnsupportedOperationException("replaceAll is not supported");
    }
}
