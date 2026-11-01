package SteamGame.recommend.domain.taste;

import SteamGame.recommend.domain.taste.content.AxisSide;
import SteamGame.recommend.domain.taste.content.LocalizedText;
import SteamGame.recommend.domain.taste.content.ShareText;
import SteamGame.recommend.domain.taste.content.TagWeight;
import SteamGame.recommend.domain.taste.content.TasteAxis;
import SteamGame.recommend.domain.taste.content.TasteContent;
import SteamGame.recommend.domain.taste.content.TasteOption;
import SteamGame.recommend.domain.taste.content.TasteQuestion;
import SteamGame.recommend.domain.taste.content.TasteType;

import java.util.ArrayList;
import java.util.List;

// 4축, 축마다 3문항(12문항), 16유형을 갖춘 정상 문구 데이터 픽스처
public final class TasteFixtures {

    private static final String[] AXIS_IDS = {"EG", "NM", "HC", "SP"};

    private TasteFixtures() {
    }

    public static LocalizedText text(String s) {
        return new LocalizedText(s + " ko", s + " en");
    }

    public static TasteContent validContent() {
        List<TasteAxis> axes = new ArrayList<>();
        for (String id : AXIS_IDS) {
            axes.add(new TasteAxis(id,
                    new AxisSide(id.substring(0, 1), text("left " + id)),
                    new AxisSide(id.substring(1, 2), text("right " + id))));
        }

        List<TasteQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String axis = AXIS_IDS[(i - 1) % 4];
            questions.add(question(i, axis, axis.substring(0, 1), axis.substring(1, 2)));
        }

        List<TasteType> types = new ArrayList<>();
        for (char a : new char[]{'E', 'G'}) {
            for (char b : new char[]{'N', 'M'}) {
                for (char c : new char[]{'H', 'C'}) {
                    for (char d : new char[]{'S', 'P'}) {
                        types.add(type("" + a + b + c + d));
                    }
                }
            }
        }
        return new TasteContent("v1", axes, questions, types);
    }

    public static TasteQuestion question(int id, String axis, String sideA, String sideB) {
        return new TasteQuestion(id, axis, text("q" + id), List.of(
                new TasteOption("A", sideA, text("q" + id + "A")),
                new TasteOption("B", sideB, text("q" + id + "B"))));
    }

    public static TasteType type(String code) {
        return new TasteType(code, text("name " + code), text("work " + code), 100L, text("one " + code), text("desc " + code),
                text("traits " + code), weights(6), List.of(1L, 2L, 3L),
                new ShareText(text("title " + code), text("share " + code)));
    }

    public static List<TagWeight> weights(int count) {
        List<TagWeight> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new TagWeight("Tag" + i, 1.0 - i * 0.1));
        }
        return list;
    }
}
