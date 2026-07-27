package SteamGame.recommend.infra.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

// RemoteIpValve 적용 후이므로 getRemoteAddr() 가 실제 클라 IP 를 반환함
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return (ip != null && !ip.isBlank()) ? ip : "unknown";
    }
}
