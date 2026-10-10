package SteamGame.recommend.domain.recommendation.similar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recommend")
public class RecommendProperties {

    // 비슷한 게임 추천에 태그 구조 벡터(GNN) 결과를 섞을지, 학습 배치로 벡터를 채운 뒤에 켠다
    // 꺼져 있으면 설명문 임베딩만 써서 지금과 같게 동작한다
    private boolean similarInterleaveEnabled = false;

    public boolean isSimilarInterleaveEnabled() { return similarInterleaveEnabled; }
    public void setSimilarInterleaveEnabled(boolean similarInterleaveEnabled) {
        this.similarInterleaveEnabled = similarInterleaveEnabled;
    }
}
