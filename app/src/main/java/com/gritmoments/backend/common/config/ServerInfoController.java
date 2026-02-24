package com.gritmoments.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서버 정보 API (세션 06: 고가용성 - 로드밸런싱 확인용)
 *
 * Nginx 로드밸런서가 요청을 어떤 서버로 보냈는지 확인할 때 사용합니다.
 * 여러 번 요청하면 app-1, app-2가 번갈아 응답하는 것을 확인할 수 있습니다.
 */
@RestController
public class ServerInfoController {

    @Value("${server.id:unknown}")
    private String serverId;

    @GetMapping("/api/server-info")
    public Map<String, String> serverInfo() {
        return Map.of(
                "serverId", serverId,
                "timestamp", java.time.LocalDateTime.now().toString()
        );
    }
}
