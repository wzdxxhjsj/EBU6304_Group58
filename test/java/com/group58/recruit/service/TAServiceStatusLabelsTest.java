package com.group58.recruit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group58.recruit.model.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Role B (TA browse / apply) — example unit tests with no disk I/O.
 * <p>
 * Heavier behaviour (e.g. {@link TAService#submitApplication}) touches JSON under {@code data/};
 * those are easier after either dependency injection of repositories or a dedicated temp {@code user.dir}.
 */
@DisplayName("TAService: status labels and acceptance cap (Role B demo)")
final class TAServiceStatusLabelsTest {

    @Nested
    @DisplayName("displayLabelForStatus")
    class DisplayLabel {

        @Test
        void submitted() {
            assertEquals("Submitted", TAService.displayLabelForStatus(ApplicationStatus.SUBMITTED));
        }

        @Test
        void accepted() {
            assertEquals("Accepted", TAService.displayLabelForStatus(ApplicationStatus.ACCEPTED));
        }

        @Test
        void reassignedMatchesHistoryUi() {
            assertEquals("Reassigned", TAService.displayLabelForStatus(ApplicationStatus.REASSIGNED));
        }

        @Test
        void waitingForAssignment() {
            assertEquals("Waiting for Assignment",
                    TAService.displayLabelForStatus(ApplicationStatus.WAITING_FOR_ASSIGNMENT));
        }

        @Test
        void nullFallsBackToSubmitted() {
            assertEquals("Submitted", TAService.displayLabelForStatus(null));
        }
    }

    @Nested
    @DisplayName("countsAsAcceptedForTa (max 3 placements)")
    class AcceptedCap {

        @Test
        void acceptedCounts() {
            assertTrue(TAService.countsAsAcceptedForTa(ApplicationStatus.ACCEPTED));
        }

        @Test
        void reassignedCounts() {
            assertTrue(TAService.countsAsAcceptedForTa(ApplicationStatus.REASSIGNED));
        }

        @Test
        void submittedDoesNotCount() {
            assertFalse(TAService.countsAsAcceptedForTa(ApplicationStatus.SUBMITTED));
        }

        @Test
        void waitingDoesNotCount() {
            assertFalse(TAService.countsAsAcceptedForTa(ApplicationStatus.WAITING_FOR_ASSIGNMENT));
        }
    }
}
