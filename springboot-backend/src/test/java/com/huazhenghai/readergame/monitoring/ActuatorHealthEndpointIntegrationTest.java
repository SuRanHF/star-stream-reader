package com.huazhenghai.readergame.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.NarrativeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies public, detail-free probes against an isolated H2 database. */
@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:actuator_health;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                // Simulate an unavailable optional cache without changing the local Redis service.
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                "spring.data.redis.connect-timeout=100ms",
                "app.scheduler.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class ActuatorHealthEndpointIntegrationTest {

    @MockitoBean
    private ExploreService exploreService;

    @MockitoBean
    private NarrativeService narrativeService;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void readinessRequiresDatabaseButNotOptionalRedis() {
        ResponseEntity<JsonNode> response = get("/actuator/health/readiness");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().path("status").asText());
        assertFalse(response.getBody().has("components"));
        assertFalse(response.getBody().has("details"));
    }

    @Test
    void livenessDoesNotDependOnExternalServices() {
        ResponseEntity<JsonNode> response = get("/actuator/health/liveness");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().path("status").asText());
        assertFalse(response.getBody().has("components"));
    }

    @Test
    void onlySafeManagementEndpointsAreExposed() {
        ResponseEntity<JsonNode> info = get("/actuator/info");
        ResponseEntity<String> env = restTemplate.getForEntity(url("/actuator/env"), String.class);

        assertEquals(HttpStatus.OK, info.getStatusCode());
        assertNotNull(info.getBody());
        assertEquals("星流读者", info.getBody().path("app").path("name").asText());
        assertEquals(HttpStatus.UNAUTHORIZED, env.getStatusCode());
    }

    private ResponseEntity<JsonNode> get(String path) {
        return restTemplate.getForEntity(url(path), JsonNode.class);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
