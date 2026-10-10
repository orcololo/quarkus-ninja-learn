package org.acme.missions;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MissionRepository implements PanacheRepository<MissionEntity> {
  public List<MissionEntity> listPage(int page, int size) {
    return find("order by id").page(Page.of(page, size)).list();
  }
}
