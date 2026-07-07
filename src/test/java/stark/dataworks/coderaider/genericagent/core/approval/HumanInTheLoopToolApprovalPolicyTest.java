package stark.dataworks.coderaider.genericagent.core.approval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HumanInTheLoopToolApprovalPolicyTest
{
    @Test
    void approvesWhenUserTypesYesIgnoringCase()
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HumanInTheLoopToolApprovalPolicy policy = policyWithInput("yEs\n", output);

        ToolApprovalDecision decision = policy.decide(new ToolApprovalRequest("agent", "echo", Map.of("text", "hello")));

        assertTrue(decision.isApproved());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Type Yes"));
    }

    @Test
    void deniesWhenUserDoesNotTypeYes()
    {
        HumanInTheLoopToolApprovalPolicy policy = policyWithInput("no\n", new ByteArrayOutputStream());

        ToolApprovalDecision decision = policy.decide(new ToolApprovalRequest("agent", "echo", Map.of()));

        assertFalse(decision.isApproved());
    }

    private static HumanInTheLoopToolApprovalPolicy policyWithInput(String input, ByteArrayOutputStream output)
    {
        return new HumanInTheLoopToolApprovalPolicy(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(output, true, StandardCharsets.UTF_8));
    }
}
