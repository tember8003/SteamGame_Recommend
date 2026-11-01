package SteamGame.recommend.domain.library.share;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
public class LibraryShareService {

    private static final Logger log = LoggerFactory.getLogger(LibraryShareService.class);

    private final SecureRandom random = new SecureRandom();
    private final LibraryShareRepository repository;
    private final int shareDays;

    public LibraryShareService(LibraryShareRepository repository,
                               @Value("${app.library.share-days:90}") int shareDays) {
        this.repository = repository;
        this.shareDays = shareDays;
    }

    // 임의 16바이트를 패딩 없는 base64url 로 써서 22자 주소용 문자열을 만든다
    public String save(String payloadJson) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String shareId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.insert(shareId, payloadJson, Instant.now().plus(shareDays, ChronoUnit.DAYS));
        return shareId;
    }

    public Optional<String> find(String shareId) {
        return repository.findActive(shareId);
    }

    @Scheduled(cron = "0 30 4 * * *")
    public int deleteExpired() {
        int deleted = repository.deleteExpired();
        log.info("만료된 라이브러리 공유 결과를 지움 deleted={}", deleted);
        return deleted;
    }
}
