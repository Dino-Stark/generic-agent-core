package stark.dataworks.coderaider.genericagent.core.memory.context;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import stark.dataworks.coderaider.genericagent.core.context.ContextItem;

/**
 * In-memory default context-service store for local runs/tests.
 */
public class InMemoryContextServiceMemoryStore implements IContextServiceMemoryStore
{
    private final Map<String, List<ContextItem>> data = new ConcurrentHashMap<>();

    @Override
    public ContextReadResult read(String namespace, String sessionId)
    {
        List<ContextItem> items = data.get(key(namespace, sessionId));
        return new ContextReadResult(items == null ? List.of() : List.copyOf(items), items != null);
    }

    @Override
    public void write(String namespace, String sessionId, List<ContextItem> items)
    {
        data.put(key(namespace, sessionId), List.copyOf(items));
    }

    private String key(String namespace, String sessionId)
    {
        return (namespace == null ? "default" : namespace) + "::" + (sessionId == null ? "default" : sessionId);
    }
}
