package com.huazhenghai.readergame.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.NarrativeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:security_boundary;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityBoundaryIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private ExploreService exploreService;
    @MockitoBean private NarrativeService narrativeService;

    @Test
    void publicEndpointWorksButPlayerEndpointRequiresBearerToken() {
        assertEquals(HttpStatus.OK, http.getForEntity("/api/changelog", JsonNode.class).getStatusCode());
        ResponseEntity<JsonNode> privateResponse = http.getForEntity("/api/auth/me", JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, privateResponse.getStatusCode());
        assertNotNull(privateResponse.getBody());
        assertEquals("UNAUTHORIZED", privateResponse.getBody().path("error").path("code").asText());
    }

    @Test
    void adminRoutesRejectPlayerAndLegacyAdminKey() {
        HttpHeaders keyOnly = new HttpHeaders();
        keyOnly.set("X-Admin-Key", "test-admin-key-123456");
        assertEquals(HttpStatus.UNAUTHORIZED,
                http.exchange("/api/admin/nonexistent", HttpMethod.GET,
                        new HttpEntity<>(keyOnly), JsonNode.class).getStatusCode());

        HttpHeaders playerHeaders = new HttpHeaders();
        playerHeaders.setBearerAuth(jwtUtil.generateToken(42L, "player", "player"));
        assertEquals(HttpStatus.FORBIDDEN,
                http.exchange("/api/admin/nonexistent", HttpMethod.GET,
                        new HttpEntity<>(playerHeaders), JsonNode.class).getStatusCode());
    }

    @Test
    void corsAllowsListedOriginAndRejectsUnlistedOrigin() {
        HttpHeaders allowed = new HttpHeaders();
        allowed.setOrigin("http://localhost:5173");
        allowed.set("Access-Control-Request-Method", "POST");
        ResponseEntity<String> accepted = http.exchange("/api/auth/login", HttpMethod.OPTIONS,
                new HttpEntity<>(allowed), String.class);
        assertEquals(HttpStatus.OK, accepted.getStatusCode());
        assertEquals("http://localhost:5173", accepted.getHeaders().getAccessControlAllowOrigin());

        HttpHeaders rejected = new HttpHeaders();
        rejected.setOrigin("https://untrusted.example");
        rejected.set("Access-Control-Request-Method", "POST");
        assertEquals(HttpStatus.FORBIDDEN,
                http.exchange("/api/auth/login", HttpMethod.OPTIONS,
                        new HttpEntity<>(rejected), String.class).getStatusCode());
    }

    @Test
    void shortJwtSecretFailsInsteadOfBeingPadded() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("short", 168));
    }
}
