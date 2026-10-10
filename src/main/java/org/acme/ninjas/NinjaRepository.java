package org.acme.ninjas;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NinjaRepository implements PanacheRepository<NinjaEntity> {
  public List<NinjaEntity> listPage(int page, int size) {
    return find("order by id").page(Page.of(page, size)).list();
  }
}
