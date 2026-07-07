package stark.dataworks.coderaider.genericagent.core.approval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Human-in-the-loop policy that asks an operator to approve each tool call.
 */
public class HumanInTheLoopToolApprovalPolicy implements IToolApprovalPolicy
{
    private static final String APPROVAL_TEXT = "Yes";

    private final BufferedReader input;
    private final PrintStream output;

    /**
     * Creates a console-backed approval policy.
     */
    public HumanInTheLoopToolApprovalPolicy()
    {
        this(System.in, System.out);
    }

    /**
     * Creates an approval policy backed by custom input and output streams.
     *
     * @param inputStream input stream used to read the operator response.
     * @param output      output stream used to display the approval prompt.
     */
    public HumanInTheLoopToolApprovalPolicy(InputStream inputStream, PrintStream output)
    {
        this(new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream, "inputStream"), StandardCharsets.UTF_8)), output);
    }

    /**
     * Creates an approval policy backed by a reader and output stream.
     *
     * @param input  reader used to read the operator response.
     * @param output output stream used to display the approval prompt.
     */
    public HumanInTheLoopToolApprovalPolicy(BufferedReader input, PrintStream output)
    {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    /**
     * Evaluates and returns an approval decision.
     *
     * @param request request payload.
     * @return tool approval decision result.
     */
    @Override
    public ToolApprovalDecision decide(ToolApprovalRequest request)
    {
        Objects.requireNonNull(request, "request");
        output.println();
        output.println("Tool approval required");
        output.println("Agent: " + request.getAgentId());
        output.println("Tool: " + request.getToolName());
        output.println("Arguments: " + request.getArguments());
        output.print("Type Yes to approve this tool call: ");
        output.flush();

        String answer;
        try
        {
            answer = input.readLine();
        }
        catch (IOException ex)
        {
            return ToolApprovalDecision.deny("Failed to read human approval: " + ex.getMessage());
        }

        if (answer != null && APPROVAL_TEXT.equalsIgnoreCase(answer.trim()))
        {
            return ToolApprovalDecision.approve();
        }
        return ToolApprovalDecision.deny("Human approval was not granted. Type Yes to approve.");
    }
}
