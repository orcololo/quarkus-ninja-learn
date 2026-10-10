package org.acme.restaurants;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MenuItemRepository implements PanacheRepository<MenuItemEntity> {
  public List<MenuItemEntity> listForRestaurant(long restaurantId, int page, int size) {
    return find("restaurantId = ?1 order by category, name, id", restaurantId)
        .page(Page.of(page, size))
        .list();
  }

  public long countForRestaurant(long restaurantId) {
    return count("restaurantId", restaurantId);
  }

  public MenuItemEntity findForRestaurant(long restaurantId, long itemId) {
    return find("restaurantId = ?1 and id = ?2", restaurantId, itemId).firstResult();
  }

  public List<MenuItemEntity> findItemsWithIds(List<Long> ids) {
    return list("id in ?1", ids);
  }

  public List<Long> listIdsForRestaurant(long restaurantId) {
    return getEntityManager().createQuery(
        "select item.id from MenuItemEntity item where item.restaurantId = :restaurantId order by item.id",
        Long.class)
        .setParameter("restaurantId", restaurantId)
        .getResultList();
  }

  public List<MenuItemEntity> findAfterId(long id, int size) {
    return find("id > ?1 order by id", id)
        .page(io.quarkus.panache.common.Page.ofSize(size))
        .list();
  }
}
