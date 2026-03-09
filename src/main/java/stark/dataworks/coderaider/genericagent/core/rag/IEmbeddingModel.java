package stark.dataworks.coderaider.genericagent.core.rag;

import java.util.List;

public interface IEmbeddingModel
{
    List<Double> embed(String text);
}
