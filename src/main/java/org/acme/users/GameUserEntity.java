package org.acme.users;

import java.util.ArrayList;
import java.util.List;

import org.acme.ninjas.NinjaEntity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "game_user_subject_unique", columnNames = "subject"),
    @UniqueConstraint(name = "game_user_username_unique", columnNames = "username"),
    @UniqueConstraint(name = "game_user_email_unique", columnNames = "email")
})
public class GameUserEntity extends PanacheEntity {
  @Column(nullable = false, length = 128)
  private String subject;

  @Column(nullable = false, length = 64)
  private String username;

  @Column(nullable = false, length = 254)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AccountLevel level = AccountLevel.BASIC;

  @OneToMany(mappedBy = "owner", cascade = CascadeType.PERSIST)
  private List<NinjaEntity> ninjas = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public AccountLevel getLevel() {
    return level;
  }

  public void setLevel(AccountLevel level) {
    this.level = level;
  }

  public List<NinjaEntity> getNinjas() {
    return ninjas;
  }

  public void addNinja(NinjaEntity ninja) {
    if (!ninjas.contains(ninja)) {
      ninjas.add(ninja);
    }
    if (ninja.getOwner() != this) {
      ninja.setOwner(this);
    }
  }

  public void removeNinja(NinjaEntity ninja) {
    ninjas.remove(ninja);
    if (ninja.getOwner() == this) {
      ninja.setOwner(null);
    }
  }
}
