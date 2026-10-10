package org.acme.village;

import java.util.ArrayList;
import java.util.List;

import org.acme.ninjas.NinjaEntity;
import org.hibernate.annotations.Formula;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

/**
 * VillageEntity
 */

@Entity
public class VillageEntity extends PanacheEntity {
  private String name;
  private String location;
  private String description;
  @ManyToOne
  @JoinColumn(name = "leader_id")
  private NinjaEntity leader;
  @OneToMany(mappedBy = "village")
  private List<NinjaEntity> ninjas = new ArrayList<>();
  @Formula("(select count(*) from NinjaEntity n where n.village_id = {alias}.id)")
  private Integer population;
  private VillageTypeEnum type;

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public NinjaEntity getLeader() {
    return leader;
  }

  public void setLeader(NinjaEntity leader) {
    this.leader = leader;
    if (leader != null && leader.getVillage() != this) {
      leader.setVillage(this);
    }
  }

  public List<NinjaEntity> getNinjas() {
    return ninjas;
  }

  public void addNinja(NinjaEntity ninja) {
    if (!ninjas.contains(ninja)) {
      ninjas.add(ninja);
    }
    if (ninja.getVillage() != this) {
      ninja.setVillage(this);
    }
  }

  public void removeNinja(NinjaEntity ninja) {
    ninjas.remove(ninja);
    if (leader == ninja) {
      leader = null;
    }
    if (ninja.getVillage() == this) {
      ninja.setVillage(null);
    }
  }

  public Integer getPopulation() {
    return population;
  }

  public VillageTypeEnum getType() {
    return type;
  }

  public void setType(VillageTypeEnum type) {
    this.type = type;
  }

}
