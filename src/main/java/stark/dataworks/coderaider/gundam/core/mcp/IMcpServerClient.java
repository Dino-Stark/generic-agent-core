package stark.dataworks.coderaider.gundam.core.mcp;

import java.util.List;
import java.util.Map;

/**
 * McpServerClient implements MCP server integration and tool bridging.
 */
public interface IMcpServerClient
{

    /**
     * Lists tools.
     * @param config configuration.
     * @return List of mcp tool descriptor values.
     */
    List<McpToolDescriptor> listTools(McpServerConfiguration config);

    /**
     * Calls the specified tool provided by the MCP server.
     * @param config configuration.
     * @param toolName tool name.
     * @param args args.
     * @return Result text returned by this operation.
     */

    String callTool(McpServerConfiguration config, String toolName, Map<String, Object> args);

    /**
     * Lists resources.
     * @param config configuration.
     * @return List of mcp resource values.
     */

    List<McpResource> listResources(McpServerConfiguration config);

    /**
     * Lists resource templates.
     * @param config configuration.
     * @return List of mcp resource template values.
     */

    List<McpResourceTemplate> listResourceTemplates(McpServerConfiguration config);

    /**
     * Reads resource.
     * @param config configuration.
     * @param uri resource URI.
     * @return mcp resource result.
     */

    McpResource readResource(McpServerConfiguration config, String uri);
}
