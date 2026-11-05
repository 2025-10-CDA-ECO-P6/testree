package fr.testree.ingestion;

import java.time.Instant;
import java.util.List;

/** Une suite TestNG du fichier : son nom, sa date de début ({@code null} si illisible) et ses tests ATS. */
public record ParsedSuite(String name, Instant startedAt, List<ParsedTest> tests) {
}
