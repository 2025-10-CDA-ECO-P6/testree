package fr.testree;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Premier test du back : l'application démarre et son endpoint de santé ne révèle rien de plus
 * que l'état « UP ».
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthEndpointTest {

    private static final String HEALTH_URL = "/actuator/health";

    private final MockMvcTester mvc;

    HealthEndpointTest(@Autowired MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("GET /actuator/health répond 200 avec le statut UP")
    void healthReturnsUp() {
        assertThat(mvc.get().uri(HEALTH_URL))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    @DisplayName("L'endpoint de santé ne détaille pas les composants internes")
    void healthHidesDetails() {
        assertThat(mvc.get().uri(HEALTH_URL))
                .bodyJson()
                .doesNotHavePath("$.components");
    }

    @Test
    @DisplayName("Les autres endpoints Actuator ne sont pas exposés")
    void otherActuatorEndpointsAreNotExposed() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
