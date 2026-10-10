package org.acme.missions;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NinjaMissionRepository implements PanacheRepository<NinjaMissionEntity> {
  public List<NinjaMissionEntity> listSubmittedPage(int page, int size) {
    return find("status = ?1 order by id", NinjaMissionStatus.SUBMITTED)
        .page(Page.of(page, size))
        .list();
  }
}
