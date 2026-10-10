package org.acme.restaurants;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RestaurantRepository implements PanacheRepository<RestaurantEntity> {
  public List<RestaurantEntity> findRestaurantsByIds(List<Long> ids) {
    return list("id in ?1", ids);
  }

  public List<RestaurantEntity> findAfterId(long id, int size) {
    return find("id > ?1 order by id", id).page(Page.ofSize(size)).list();
  }
}
