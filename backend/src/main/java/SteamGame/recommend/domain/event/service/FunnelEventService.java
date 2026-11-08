package SteamGame.recommend.domain.event.service;

import SteamGame.recommend.domain.event.entity.FunnelEvent;
import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.entity.ShareChannel;
import SteamGame.recommend.domain.event.repository.FunnelEventRepository;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
public class FunnelEventService {

    private static final Logger log = LoggerFactory.getLogger(FunnelEventService.class);

    // 라이브러리 분석 공유는 유형 코드 대신 이 값을 ref 로 쓴다
    private static final String LIBRARY_REF = "LIBRARY";

    private static final Set<String> FEATURES = Set.of("TASTE", "SIMILAR", "LIBRARY");

    private final FunnelEventRepository repository;
    private final TasteContentLoader contentLoader;

    public FunnelEventService(FunnelEventRepository repository, TasteContentLoader contentLoader) {
        this.repository = repository;
        this.contentLoader = contentLoader;
    }

    // 이벤트 종류별 허용 ref, channel 을 벗어나면 400, 자유 문자열을 받지 않아 개인정보가 섞이지 않게 한다
    // TEST_COMPLETE, LIBRARY_COMPLETE 는 서버가 직접 남기므로 클라이언트 요청으로는 받지 않는다
    public void validate(FunnelEventType type, String ref, ShareChannel channel) {
        boolean ok = switch (type) {
            case LANDING_VISIT, TEST_START -> ref == null && channel == null;
            case FEATURE_SELECT -> channel == null && ref != null && FEATURES.contains(ref);
            case TEST_COMPLETE, LIBRARY_COMPLETE -> false;
            case SHARE_CLICK -> channel != null && isShareRef(ref);
            case SHARE_INFLOW -> channel == null && isShareRef(ref);
        };
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EVENT");
        }
    }

    private boolean isShareRef(String ref) {
        return ref != null && (LIBRARY_REF.equalsIgnoreCase(ref) || contentLoader.content().type(ref).isPresent());
    }

    // 유형 코드는 대문자로 맞춰 저장한다, 저장에 실패해도 예외를 밖으로 던지지 않아 사용자 응답에는 영향이 없다
    @Async("funnelEventExecutor")
    public void record(String clientId, FunnelEventType type, String ref, ShareChannel channel) {
        try {
            String stored = ref == null ? null : ref.toUpperCase(Locale.ROOT);
            repository.save(new FunnelEvent(clientId, type, stored, channel));
        } catch (Exception e) {
            log.warn("이벤트를 DB에 저장하지 못함, 사용자 응답에는 영향 없음 clientId={} type={} ref={}", clientId, type, ref, e);
        }
    }

    // 라이브러리 분석 완료와 판정 코드를 남긴다, 저장에 실패해도 예외를 밖으로 던지지 않아 사용자 응답에는 영향이 없다
    @Async("funnelEventExecutor")
    public void recordLibraryComplete(String clientId, String ledgerCode, String crimeCode, String genreCode) {
        try {
            repository.save(FunnelEvent.libraryComplete(clientId, ledgerCode, crimeCode, genreCode));
        } catch (Exception e) {
            log.warn("라이브러리 완료 이벤트를 DB에 저장하지 못함, 사용자 응답에는 영향 없음 clientId={}", clientId, e);
        }
    }
}
