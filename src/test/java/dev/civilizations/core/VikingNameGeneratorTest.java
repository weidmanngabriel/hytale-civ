package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VikingNameGeneratorTest {

    private final VikingNameGenerator generator = new VikingNameGenerator();

    @Test
    void generatesThreePartMaleName() {
        VikingNameGenerator.GeneratedName name =
            generator.generate(Gender.MALE, RandomGenerator.getDefault());

        assertEquals(Gender.MALE, name.gender());
        assertFalse(name.firstName().isBlank());
        assertFalse(name.middleName().isBlank());
        assertFalse(name.lastName().isBlank());
        assertEquals(3, name.fullName().split(" ").length);
    }

    @Test
    void generatesThreePartFemaleName() {
        VikingNameGenerator.GeneratedName name =
            generator.generate(Gender.FEMALE, RandomGenerator.getDefault());

        assertEquals(Gender.FEMALE, name.gender());
        assertFalse(name.firstName().isBlank());
        assertFalse(name.middleName().isBlank());
        assertFalse(name.lastName().isBlank());
        assertEquals(3, name.fullName().split(" ").length);
    }
}
