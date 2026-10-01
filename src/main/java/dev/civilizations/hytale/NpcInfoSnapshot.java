package dev.civilizations.hytale;

/**
 * Presentation snapshot for one Civ inhabitant. Both compact and detailed UI read the same
 * structure so adding or removing UI fields does not require gameplay state changes.
 */
public record NpcInfoSnapshot(
    Identity identity,
    Work work,
    Needs needs,
    Family family
) {

    public static final String UNAVAILABLE = "—";

    public record Identity(
        String name,
        String gender,
        String ageStage
    ) {
    }

    public record Work(
        String profession,
        String activity,
        int professionXp,
        String workplace
    ) {
    }

    public record Needs(
        String hunger,
        String energy,
        String entertainment
    ) {
    }

    public record Family(
        String home,
        String spouse,
        String children
    ) {
    }
}
