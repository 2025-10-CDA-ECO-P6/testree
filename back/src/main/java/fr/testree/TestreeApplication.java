package fr.testree;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du back de Testree.
 *
 * <p>Placée à la racine du package {@code fr.testree} pour que Spring détecte automatiquement
 * tous les composants des sous-packages (controllers, services, repositories).
 */
@SpringBootApplication
public class TestreeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestreeApplication.class, args);
    }
}
