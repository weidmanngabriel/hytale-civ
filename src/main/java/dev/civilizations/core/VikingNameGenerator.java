package dev.civilizations.core;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Generates the initial single-faction Viking inhabitant names.
 *
 * <p>The generated values are persisted on the inhabitant. The pool is not consulted again
 * after an inhabitant has been initialized.</p>
 */
public final class VikingNameGenerator {

    private static final String[] MALE_FIRST = {
        "Eirik", "Bjorn", "Leif", "Harald", "Ivar", "Ragnar", "Sven", "Torsten"
    };
    private static final String[] MALE_MIDDLE = {
        "Arne", "Bjarne", "Gunnar", "Hakon", "Knut", "Sten", "Ulf", "Vidar"
    };
    private static final String[] MALE_LAST = {
        "Erikson", "Haraldson", "Ivarson", "Rorikson", "Sigurdson", "Svenson", "Thorson", "Ulfson"
    };
    private static final String[] FEMALE_FIRST = {
        "Astrid", "Freydis", "Gudrun", "Helga", "Ingrid", "Sigrid", "Solveig", "Thyra"
    };
    private static final String[] FEMALE_MIDDLE = {
        "Alva", "Brynhild", "Eira", "Frida", "Liv", "Runa", "Saga", "Yrsa"
    };
    private static final String[] FEMALE_LAST = {
        "Eriksdottir", "Haraldsdottir", "Ivarsdottir", "Roriksdottir",
        "Sigurdsdottir", "Svensdottir", "Thorsdottir", "Ulfsdottir"
    };

    public GeneratedName generate(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        Gender gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
        return generate(gender, random);
    }

    GeneratedName generate(Gender gender, RandomGenerator random) {
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(random, "random");

        String[] first = gender == Gender.MALE ? MALE_FIRST : FEMALE_FIRST;
        String[] middle = gender == Gender.MALE ? MALE_MIDDLE : FEMALE_MIDDLE;
        String[] last = gender == Gender.MALE ? MALE_LAST : FEMALE_LAST;
        return new GeneratedName(
            gender,
            pick(first, random),
            pick(middle, random),
            pick(last, random)
        );
    }

    private static String pick(String[] values, RandomGenerator random) {
        return values[random.nextInt(values.length)];
    }

    public record GeneratedName(
        Gender gender,
        String firstName,
        String middleName,
        String lastName
    ) {
        public String fullName() {
            return firstName + " " + middleName + " " + lastName;
        }
    }
}
