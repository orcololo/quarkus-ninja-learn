package org.acme.missions;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PointTransactionRepository implements PanacheRepository<PointTransactionEntity> {
}
