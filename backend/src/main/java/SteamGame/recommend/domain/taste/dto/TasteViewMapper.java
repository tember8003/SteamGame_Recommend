package SteamGame.recommend.domain.taste.dto;

import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.taste.content.AxisSide;
import SteamGame.recommend.domain.taste.content.Lang;
import SteamGame.recommend.domain.taste.content.TasteAxis;
import SteamGame.recommend.domain.taste.content.TasteContent;
import SteamGame.recommend.domain.taste.content.TasteType;
import SteamGame.recommend.domain.taste.content.TasteV2Content;
import SteamGame.recommend.domain.taste.service.AxisDetail;

import java.util.ArrayList;
import java.util.List;

// 문구 데이터를 응답 모양으로 바꾼다, 언어 하나를 골라 문구를 평평하게 만들고 문항의 side는 빼낸다
public final class TasteViewMapper {

    private TasteViewMapper() {
    }

    public static TypeView toView(TasteContent c, TasteType t, Lang lang, List<SteamAppDTO> featured) {
        List<AxisView> axes = new ArrayList<>();
        for (int i = 0; i < c.axes().size(); i++) {
            TasteAxis axis = c.axes().get(i);
            String letter = String.valueOf(t.code().charAt(i));
            AxisSide side = axis.left().code().equals(letter) ? axis.left() : axis.right();
            axes.add(new AxisView(axis.id(), letter, side.label().get(lang)));
        }
        return new TypeView(
                t.code(),
                t.name().get(lang),
                t.work().get(lang),
                t.workAppid(),
                t.oneLiner().get(lang),
                t.description().get(lang),
                t.traits().get(lang),
                axes,
                t.tagWeights().stream().map(w -> new TagView(w.tag(), w.weight())).toList(),
                featured,
                new ShareView(t.share().title().get(lang), t.share().text().get(lang)));
    }

    public static TasteQuestionsResponse toView(TasteContent c, Lang lang) {
        List<QuestionView> questions = c.questions().stream()
                .map(q -> new QuestionView(q.id(), q.text().get(lang), q.options().stream()
                        .map(o -> new OptionView(o.key(), o.text().get(lang)))
                        .toList()))
                .toList();
        return new TasteQuestionsResponse(c.version(), questions);
    }

    public static TypeSummary toSummary(TasteType t, Lang lang) {
        return new TypeSummary(t.code(), t.name().get(lang), t.work().get(lang), t.oneLiner().get(lang));
    }

    // v2 문항 응답, 점수와 축은 빼고 v1과 같은 모양으로 만든다
    public static TasteQuestionsResponse toView(TasteV2Content c, Lang lang) {
        List<QuestionView> questions = c.questions().stream()
                .map(q -> new QuestionView(q.id(), q.text().get(lang), q.options().stream()
                        .map(o -> new OptionView(o.key(), o.text().get(lang)))
                        .toList()))
                .toList();
        return new TasteQuestionsResponse(c.version(), questions);
    }

    // 축 결과에 요청 언어의 보조 설명 문구를 붙인다
    public static List<AxisDetailView> toDetailViews(List<AxisDetail> details, TasteV2Content c, Lang lang) {
        return details.stream()
                .map(d -> new AxisDetailView(d.id(), d.left(), d.right(), d.total(), d.leftPercent(),
                        d.rightPercent(), d.winner(), d.balance().name(), d.tieBroken(),
                        c.axisSummaries().get(d.id()).get(d.summaryKey()).get(lang)))
                .toList();
    }
}
