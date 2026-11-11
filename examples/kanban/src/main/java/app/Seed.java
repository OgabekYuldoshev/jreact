package app;

import java.util.List;

/** Starting data of the board. */
final class Seed {
    private Seed() {}

    static List<Task> tasks() {
        return List.of(
            new Task(1, "Design the onboarding flow", "design", 0),
            new Task(2, "Fix the date picker on Safari", "bug", 0),
            new Task(3, "Add CSV export to reports", "feature", 0),
            new Task(4, "Write the API quick start", "docs", 1),
            new Task(5, "Dark mode for the settings page", "feature", 1),
            new Task(6, "Set up error tracking", "feature", 2),
            new Task(7, "Fix the double-submit on checkout", "bug", 2)
        );
    }
}
