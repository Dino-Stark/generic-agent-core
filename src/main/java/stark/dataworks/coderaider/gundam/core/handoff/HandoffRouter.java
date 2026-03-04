package stark.dataworks.coderaider.gundam.core.handoff;

import java.util.ArrayList;
import java.util.List;

/**
 * HandoffRouter implements agent transfer rules between specialized agents.
 */
public class HandoffRouter
{

    /**
 * Filters that decide whether a handoff candidate is allowed.
     */
    private final List<IHandoffFilter> filters = new ArrayList<>();

    /**
     * Adds filter.
     * @param filter filter.
     */
    public void addFilter(IHandoffFilter filter)
    {
        filters.add(filter);
    }

    /**
     * Checks whether the handoff can route to the target agent.
     * @param handoff handoff.
     * @return True when the operation succeeds.
     */
    public boolean canRoute(Handoff handoff)
    {
        for (IHandoffFilter filter : filters)
        {
            if (!filter.allow(handoff))
            {
                return false;
            }
        }
        return true;
    }
}
