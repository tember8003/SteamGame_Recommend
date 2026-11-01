package SteamGame.recommend.domain.event.repository;

import SteamGame.recommend.domain.event.entity.FunnelEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunnelEventRepository extends JpaRepository<FunnelEvent, Long> {
}
