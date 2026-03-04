package stark.dataworks.coderaider.gundam.core.runerror;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RunErrorHandlers implements error classification and handler dispatch.
 */
public class RunErrorHandlers
{

    /**
 * Handlers registered for in-memory MCP tool execution.
     */
    private final Map<RunErrorKind, IRunErrorHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Registers this value.
     * @param kind kind.
     * @param handler handler.
     */
    public void register(RunErrorKind kind, IRunErrorHandler handler)
    {
        handlers.put(kind, handler);
    }

    /**
     * Returns this value.
     * @param kind kind.
     * @return Optional irun error handler value.
     */
    public Optional<IRunErrorHandler> get(RunErrorKind kind)
    {
        return Optional.ofNullable(handlers.get(kind));
    }
}
