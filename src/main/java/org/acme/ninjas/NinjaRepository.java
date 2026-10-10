package org.acme.ninjas;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NinjaRepository implements PanacheRepository<NinjaEntity> {

}
