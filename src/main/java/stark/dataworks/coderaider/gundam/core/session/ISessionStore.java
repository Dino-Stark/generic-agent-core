package stark.dataworks.coderaider.gundam.core.session;

import java.util.Optional;

/**
 * SessionStore implements session persistence and restoration.
 */
public interface ISessionStore
{

    /**
     * Saves this value.
     * @param session session.
     */
    void save(Session session);

    /**
     * Loads this value.
     * @param sessionId session id.
     * @return Optional session value.
     */

    Optional<Session> load(String sessionId);
}
