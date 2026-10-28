package com.iotiq.partnererp.unit.security;

import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditCatalog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCatalogTest {

    /** Every recorded action belongs to exactly one viewer module, and every action column value fits (varchar 40). */
    @Test
    void everyActionBelongsToExactlyOneModule() throws Exception {
        List<String> actions = new ArrayList<>();
        for (Field f : AuditActions.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && !f.getName().startsWith("ENTITY_")) {
                actions.add((String) f.get(null));
            }
        }
        assertThat(actions).isNotEmpty();
        for (String action : actions) {
            assertThat(AuditCatalog.MODULES.stream().filter(m -> m.actions().contains(action)).count())
                    .as(action).isEqualTo(1);
            assertThat(action.length()).as(action).isLessThanOrEqualTo(40);
        }
        List<String> catalogued = AuditCatalog.MODULES.stream().flatMap(m -> m.actions().stream()).toList();
        assertThat(catalogued).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(actions);
    }
}
