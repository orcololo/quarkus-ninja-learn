package org.acme.village;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VillageRepository implements PanacheRepository<VillageEntity> {
}
