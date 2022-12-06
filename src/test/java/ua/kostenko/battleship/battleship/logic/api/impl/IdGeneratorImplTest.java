package ua.kostenko.battleship.battleship.logic.api.impl;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorImplTest {
    IdGeneratorImpl idGeneratorImpl = new IdGeneratorImpl();

    @Test
    void testGenerateId() {
        String result = idGeneratorImpl.generateId();
        assertNotNull(result);
        assertTrue(StringUtils.isNotBlank(result));
    }
}