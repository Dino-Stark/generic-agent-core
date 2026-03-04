package stark.dataworks.coderaider.gundam.core.session;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InMemorySessionStore implements session persistence and restoration.
 */
public class InMemorySessionStore implements ISessionStore
{

    /**
 * In-memory session storage keyed by session id.
     */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Saves this value.
     * @param session session.
     */
    @Override
    public void save(Session session)
    {
        sessions.put(session.getId(), session);
    }

    /**
     * Loads this value.
     * @param sessionId session id.
     * @return Optional session value.
     */
    @Override
    public Optional<Session> load(String sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId));
    }
}
