package org.acme.users;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserDeliveryAddressRepository implements PanacheRepository<UserDeliveryAddressEntity> {
  public List<UserDeliveryAddressEntity> listForUser(GameUserEntity user) {
    return find("user = ?1 order by defaultAddress desc, createdAt, id", user).list();
  }

  public UserDeliveryAddressEntity findForUser(GameUserEntity user, long addressId) {
    return find("user = ?1 and id = ?2", user, addressId).firstResult();
  }
}
