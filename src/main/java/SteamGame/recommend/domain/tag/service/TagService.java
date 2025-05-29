package SteamGame.recommend.domain.tag.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.tag.entity.Tag;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.infra.api.SteamApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TagService {

    private final SteamApiService steamApiService;
    private final TagRepository tagRepository;
    private final GameRepository gameRepository;

    private static final Set<String> ALLOWED_ENGLISH =
            Set.of("2D","3D","RPG","FPS","MMO");

    // 한글이 하나라도 들어있는지 체크하는 정규식
    private static final Pattern KOREAN_PATTERN = Pattern.compile(".*[\\uAC00-\\uD7A3].*");

    public TagService(GameRepository gameRepository,
                      SteamApiService steamApiService,
                      TagRepository tagRepository){
        this.gameRepository = gameRepository;
        this.steamApiService = steamApiService;
        this.tagRepository = tagRepository;
    }

    public List<String> getTopTagsByProfile(String steamId, int topN){
        List<Long> appids = steamApiService.getOwnedGameIds(steamId);

        if(appids.isEmpty()){
            return List.of();
        }

        List<String> allTags = tagRepository.findTagNamesByAppIds(appids);

        return getTopTags(allTags,topN);
    }

    //게임 랜덤 추천 셔플용
    public List<String> shuffleTag(List<String> topTags, int min, int max){
        List<String> shuffled = new ArrayList<>(topTags);

        Collections.shuffle(shuffled);

        int k = min + new Random().nextInt(max - min + 1);
        return shuffled.subList(0, Math.min(k, shuffled.size()));
    }

    public List<String> getTopTags(List<String> tags,int limits){
        Map<String, Long> counts  = tags.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<String> tag_list = counts.entrySet().stream()
                .sorted(Map.Entry.<String,Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limits)
                .map(Map.Entry::getKey)
                .toList();

        return tag_list;
    }

    public List<String> getFilteredTagNames() {
        return tagRepository.findAll().stream()
                .map(Tag::getName)
                .filter(name ->
                        KOREAN_PATTERN.matcher(name).matches()
                                || ALLOWED_ENGLISH.contains(name)
                )
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly=true)
    public List<String> getTagsByGame(String gameName){
        Game optionalGame = gameRepository.findByNameIgnoreCase(gameName).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "해당 이름의 게임을 찾을 수 없습니다: " + gameName
        ));

        List<Long> appid = List.of(optionalGame.getAppid());
        
        List<String> tags = tagRepository.findTagNamesByAppIds(appid);

        return tags;
    }

}
