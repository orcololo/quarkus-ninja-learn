package org.acme.restaurants;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExchangeRateSnapshotRepository
    implements PanacheRepositoryBase<ExchangeRateSnapshotEntity, Long> {
}
