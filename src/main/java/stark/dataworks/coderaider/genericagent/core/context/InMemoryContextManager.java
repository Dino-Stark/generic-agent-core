package stark.dataworks.coderaider.genericagent.core.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory context manager that retains conversation context items.
 */
public class InMemoryContextManager implements IContextManager
{
    private final List<ContextItem> items = new ArrayList<>();

    @Override
    public List<ContextItem> items()
    {
        return Collections.unmodifiableList(items);
    }

    @Override
    public void append(ContextItem item)
    {
        items.add(item);
    }

    @Override
    public void replaceAll(List<ContextItem> newItems)
    {
        items.clear();
        items.addAll(newItems);
    }
}
