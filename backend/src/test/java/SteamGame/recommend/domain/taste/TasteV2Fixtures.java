package SteamGame.recommend.domain.taste;

import SteamGame.recommend.domain.taste.content.LocalizedText;
import SteamGame.recommend.domain.taste.content.TasteAxis;
import SteamGame.recommend.domain.taste.content.TasteV2Content;
import SteamGame.recommend.domain.taste.content.TasteV2ContentValidator;
import SteamGame.recommend.domain.taste.content.TasteV2Option;
import SteamGame.recommend.domain.taste.content.TasteV2Question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 16문항, 축마다 4문항, 보기마다 A(3,0) B(2,1) C(1,2) D(0,3)인 정상 v2 콘텐츠 픽스처를 만든다
// 축은 TasteFixtures.validContent()의 EG, NM, HC, SP를 쓴다
public final class TasteV2Fixtures {

    public static final List<TasteAxis> AXES = TasteFixtures.validContent().axes();

    private TasteV2Fixtures() {
    }

    public static TasteV2Content validContent() {
        List<TasteV2Question> questions = new ArrayList<>();
        for (int id = 1; id <= 16; id++) {
            TasteAxis axis = AXES.get((id - 1) % 4);
            String l = axis.left().code();
            String r = axis.right().code();
            questions.add(new TasteV2Question(id, axis.id(), TasteFixtures.text("q" + id), List.of(
                    option("A", l, 3, r, 0),
                    option("B", l, 2, r, 1),
                    option("C", l, 1, r, 2),
                    option("D", l, 0, r, 3))));
        }
        Map<String, Map<String, LocalizedText>> summaries = new LinkedHashMap<>();
        for (TasteAxis axis : AXES) {
            Map<String, LocalizedText> byKey = new LinkedHashMap<>();
            for (String key : TasteV2ContentValidator.summaryKeys(axis)) {
                byKey.put(key, TasteFixtures.text(axis.id() + " " + key));
            }
            summaries.put(axis.id(), byKey);
        }
        return new TasteV2Content("v2", questions, summaries);
    }

    public static TasteV2Option option(String key, String left, int l, String right, int r) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(left, l);
        scores.put(right, r);
        return new TasteV2Option(key, scores, TasteFixtures.text("opt " + key));
    }

    public static TasteV2Content withQuestions(TasteV2Content c, List<TasteV2Question> questions) {
        return new TasteV2Content(c.version(), questions, c.axisSummaries());
    }

    public static TasteV2Content withSummaries(TasteV2Content c, Map<String, Map<String, LocalizedText>> summaries) {
        return new TasteV2Content(c.version(), c.questions(), summaries);
    }
}
