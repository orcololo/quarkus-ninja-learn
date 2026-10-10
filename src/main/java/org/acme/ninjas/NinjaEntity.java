package org.acme.ninjas;

import org.acme.village.VillageEntity;
import org.acme.users.GameUserEntity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class NinjaEntity extends PanacheEntity {
  private String name;
  private int age;
  private NinjaLevelEnum level;
  @ManyToOne
  @JoinColumn(name = "village_id")
  private VillageEntity village;
  @ManyToOne
  @JoinColumn(name = "owner_user_id")
  private GameUserEntity owner;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public NinjaLevelEnum getLevel() {
    return level;
  }

  public void setLevel(NinjaLevelEnum level) {
    this.level = level;
  }

  public VillageEntity getVillage() {
    return village;
  }

  public void setVillage(VillageEntity village) {
    if (this.village == village) {
      return;
    }

    VillageEntity previousVillage = this.village;
    this.village = village;
    if (previousVillage != null) {
      previousVillage.removeNinja(this);
    }
    if (village != null) {
      village.addNinja(this);
    }
  }

  public GameUserEntity getOwner() {
    return owner;
  }

  public void setOwner(GameUserEntity owner) {
    if (this.owner == owner) {
      return;
    }
    GameUserEntity previousOwner = this.owner;
    this.owner = owner;
    if (previousOwner != null) {
      previousOwner.removeNinja(this);
    }
    if (owner != null) {
      owner.addNinja(this);
    }
  }
}
