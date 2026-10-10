package org.acme.missions;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MissionEntityTest {
  @Inject
  EntityManager entityManager;

  @Test
  @Transactional
  void persistsMissionDetailsAndDefaultsToActive() {
    MissionEntity mission = new MissionEntity();
    mission.setTitle("Deliver a scroll");
    mission.setDescription("Deliver the sealed scroll to the Hokage.");
    mission.setPoints(25);

    entityManager.persist(mission);
    entityManager.flush();

    MissionEntity storedMission = entityManager.find(MissionEntity.class, mission.getId());
    assertEquals("Deliver a scroll", storedMission.getTitle());
    assertEquals(25, storedMission.getPoints());
    assertTrue(storedMission.isActive());
  }
}
