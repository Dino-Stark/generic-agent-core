package stark.dataworks.coderaider.genericagent.core.rag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Minimal RAG orchestration service.
 */
public class RagService
{
    private final IVectorStore vectorStore;

    public RagService(IVectorStore vectorStore)
    {
        this.vectorStore = vectorStore;
    }

    public String retrieveContext(String query, int topK)
    {
        List<Document> docs = vectorStore.similaritySearch(new SearchRequest(query, topK, -1, null));
        if (docs.isEmpty())
        {
            return "";
        }
        return docs.stream().map(Document::getContent).collect(Collectors.joining("\n---\n"));
    }
}
