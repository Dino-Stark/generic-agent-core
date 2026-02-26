package stark.dataworks.coderaider.gundam.core.session;

import java.util.List;

import lombok.Getter;
import stark.dataworks.coderaider.gundam.core.model.Message;

/**
 * Session implements session persistence and restoration.
 */
@Getter
public class Session
{
    /**
     * Internal state for id; used while coordinating runtime behavior.
     */
    private final String id;

    /**
     * Internal state for messages; used while coordinating runtime behavior.
     */
    private final List<Message> messages;

    public Session(String id, List<Message> messages)
    {
        this.id = id;
        this.messages = List.copyOf(messages);
    }
}
