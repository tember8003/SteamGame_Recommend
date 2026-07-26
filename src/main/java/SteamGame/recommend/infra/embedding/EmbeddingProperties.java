package SteamGame.recommend.infra.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {
    // dimension 은 V2 마이그레이션의 vector(N) 와 반드시 일치
    private String model = "gemini-embedding-001";
    private int dimension = 768;
    private String taskType = "SEMANTIC_SIMILARITY";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models";
    private int batchSize = 100;
    private long requestDelayMs = 1000;
    private int descriptionMaxChars = 1000;
    private int candidatePoolK = 40;
    private int resultSize = 4;
    // 배치 임베딩이 429(rate limit)로 실패하면 백오프 후 재시도하는 최대 횟수·대기시간
    private int maxBatchRetries = 3;
    private long retryBackoffMs = 60000;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public long getRequestDelayMs() { return requestDelayMs; }
    public void setRequestDelayMs(long requestDelayMs) { this.requestDelayMs = requestDelayMs; }
    public int getDescriptionMaxChars() { return descriptionMaxChars; }
    public void setDescriptionMaxChars(int descriptionMaxChars) { this.descriptionMaxChars = descriptionMaxChars; }
    public int getCandidatePoolK() { return candidatePoolK; }
    public void setCandidatePoolK(int candidatePoolK) { this.candidatePoolK = candidatePoolK; }
    public int getResultSize() { return resultSize; }
    public void setResultSize(int resultSize) { this.resultSize = resultSize; }
    public int getMaxBatchRetries() { return maxBatchRetries; }
    public void setMaxBatchRetries(int maxBatchRetries) { this.maxBatchRetries = maxBatchRetries; }
    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
}
