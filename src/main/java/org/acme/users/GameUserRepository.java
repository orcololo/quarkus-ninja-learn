package org.acme.users;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GameUserRepository implements PanacheRepository<GameUserEntity> {
}
