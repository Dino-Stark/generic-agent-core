package stark.dataworks.coderaider.gundam.core.guardrail;

import java.util.ArrayList;
import java.util.List;

import stark.dataworks.coderaider.gundam.core.llmspi.LlmResponse;
import stark.dataworks.coderaider.gundam.core.runtime.ExecutionContext;

/**
 * GuardrailEngine implements input/output policy evaluation around model responses.
 */
public class GuardrailEngine
{

    /**
 * Input guardrails captured for this step.
     */
    private final List<IInputGuardrail> inputGuardrails = new ArrayList<>();

    /**
 * Output guardrails produced by this step.
     */
    private final List<IOutputGuardrail> outputGuardrails = new ArrayList<>();

    /**
     * Registers input.
     * @param guardrail guardrail.
     */
    public void registerInput(IInputGuardrail guardrail)
    {
        inputGuardrails.add(guardrail);
    }

    /**
     * Registers output.
     * @param guardrail guardrail.
     */
    public void registerOutput(IOutputGuardrail guardrail)
    {
        outputGuardrails.add(guardrail);
    }

    /**
     * Evaluates input.
     * @param context execution context.
     * @param input input payload.
     * @return guardrail decision result.
     */
    public GuardrailDecision evaluateInput(ExecutionContext context, String input)
    {
        for (IInputGuardrail guardrail : inputGuardrails)
        {
            GuardrailDecision decision = guardrail.evaluate(context, input);
            if (!decision.isAllowed())
            {
                return decision;
            }
        }
        return GuardrailDecision.allow();
    }

    /**
     * Evaluates output.
     * @param context execution context.
     * @param response model/tool response payload.
     * @return guardrail decision result.
     */
    public GuardrailDecision evaluateOutput(ExecutionContext context, LlmResponse response)
    {
        for (IOutputGuardrail guardrail : outputGuardrails)
        {
            GuardrailDecision decision = guardrail.evaluate(context, response);
            if (!decision.isAllowed())
            {
                return decision;
            }
        }
        return GuardrailDecision.allow();
    }
}
