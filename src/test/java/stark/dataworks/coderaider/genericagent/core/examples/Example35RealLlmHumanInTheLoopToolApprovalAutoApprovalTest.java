package stark.dataworks.coderaider.genericagent.core.examples;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import stark.dataworks.coderaider.genericagent.core.approval.HumanInTheLoopToolApprovalPolicy;
import stark.dataworks.coderaider.genericagent.core.approval.IToolApprovalPolicy;
import stark.dataworks.coderaider.genericagent.core.context.ContextItemType;
import stark.dataworks.coderaider.genericagent.core.context.ContextResult;

/**
 * Non-interactive verification for Example35.
 */
class Example35RealLlmHumanInTheLoopToolApprovalAutoApprovalTest
{
    @Test
    void realLlmToolCallExecutesWhenApprovalInputIsYes()
    {
        Example35RealLlmHumanInTheLoopToolApprovalTest.OwiwoConfig config =
            Example35RealLlmHumanInTheLoopToolApprovalTest.loadOwiwoConfig();
        if (!config.isComplete())
        {
            System.out.println("Skipping test: OWIWO_BASE_URL, OWIWO_API_KEY, or OWIWO_MODEL not set");
            return;
        }

        IToolApprovalPolicy approvalPolicy = new HumanInTheLoopToolApprovalPolicy(
            new ByteArrayInputStream("Yes\n".getBytes(StandardCharsets.UTF_8)),
            System.out);

        ContextResult result = Example35RealLlmHumanInTheLoopToolApprovalTest.runExample(config, approvalPolicy);
        assertFalse(result.getFinalOutput().isBlank());
        assertFalse(result.getFinalOutput().startsWith("Run failed:"), result.getFinalOutput());
        assertTrue(result.getItems().stream().anyMatch(item -> item.getType() == ContextItemType.TOOL_RESULT),
            "Expected the real LLM to request and execute approved_echo before answering.");
    }
}
