package stark.dataworks.coderaider.genericagent.core.memory.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import stark.dataworks.coderaider.genericagent.core.context.IContextManager;
import stark.dataworks.coderaider.genericagent.core.context.ContextItem;

/**
 * Memory implementation backed by external context-service store.
 */
public class ContextServiceContextManager implements IContextManager
{
    private final String namespace;
    private final String sessionId;
    private final IContextServiceMemoryStore store;
    private final List<ContextItem> writes = new ArrayList<>();

    public ContextServiceContextManager(String namespace, String sessionId, IContextServiceMemoryStore store)
    {
        this.namespace = namespace;
        this.sessionId = sessionId;
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public List<ContextItem> items()
    {
        List<ContextItem> baseline = store.read(namespace, sessionId).items();
        if (writes.isEmpty())
        {
            return baseline;
        }
        List<ContextItem> merged = new ArrayList<>(baseline);
        merged.addAll(writes);
        return merged;
    }

    @Override
    public void append(ContextItem item)
    {
        writes.add(item);
        store.write(namespace, sessionId, items());
    }

    @Override
    public void replaceAll(List<ContextItem> messages)
    {
        writes.clear();
        store.write(namespace, sessionId, messages);
    }
}
