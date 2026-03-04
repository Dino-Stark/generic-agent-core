package stark.dataworks.coderaider.gundam.core.output;

import lombok.Getter;

/**
 * OutputValidationResult implements structured output schema validation.
 */
@Getter
public class OutputValidationResult
{

    /**
     * Whether output passed schema validation.
     */
    private final boolean valid;

    /**
     * Reason why execution is allowed or blocked.
     */
    private final String reason;

    /**
     * Creates a new OutputValidationResult instance.
     * @param valid valid.
     * @param reason human-readable reason.
     */
    private OutputValidationResult(boolean valid, String reason)
    {
        this.valid = valid;
        this.reason = reason == null ? "" : reason;
    }

    /**
     * Returns a successful validation/result object.
     * @return output validation result result.
     */
    public static OutputValidationResult ok()
    {
        return new OutputValidationResult(true, "");
    }

    /**
     * Returns a failed validation/result object.
     * @param reason human-readable reason.
     * @return output validation result result.
     */
    public static OutputValidationResult fail(String reason)
    {
        return new OutputValidationResult(false, reason);
    }
}
