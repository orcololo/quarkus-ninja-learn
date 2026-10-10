package org.acme.village;

import org.acme.ninjas.NinjaEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VillageEntityTest {
  @Test
  void populationTracksNinjaAssignmentsAndRemovals() {
    VillageEntity firstVillage = new VillageEntity();
    VillageEntity secondVillage = new VillageEntity();
    NinjaEntity ninja = new NinjaEntity();

    ninja.setVillage(firstVillage);
    assertEquals(1, firstVillage.getNinjas().size());

    ninja.setVillage(firstVillage);
    assertEquals(1, firstVillage.getNinjas().size());

    ninja.setVillage(secondVillage);
    assertEquals(0, firstVillage.getNinjas().size());
    assertEquals(1, secondVillage.getNinjas().size());
    assertSame(secondVillage, ninja.getVillage());

    secondVillage.removeNinja(ninja);
    assertEquals(0, secondVillage.getNinjas().size());
    assertNull(ninja.getVillage());
  }

  @Test
  void leaderIsCountedAsAMemberAndOnlyCountedOnce() {
    VillageEntity village = new VillageEntity();
    NinjaEntity leader = new NinjaEntity();

    village.setLeader(leader);

    assertSame(village, leader.getVillage());
    assertSame(leader, village.getNinjas().getFirst());
    assertEquals(1, village.getNinjas().size());

    village.addNinja(leader);
    assertEquals(1, village.getNinjas().size());

    village.removeNinja(leader);
    assertNull(village.getLeader());
    assertNull(leader.getVillage());
    assertEquals(0, village.getNinjas().size());
  }
}
