package org.acme.village.mapper;

import org.acme.ninjas.NinjaEntity;
import org.acme.village.VillageEntity;
import org.acme.village.dto.CreateVillageRequest;
import org.acme.village.dto.NinjaReferenceDto;
import org.acme.village.dto.VillageResponseDto;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VillageMapper {

  public VillageEntity toEntity(CreateVillageRequest request) {
    VillageEntity village = new VillageEntity();
    village.setName(request.name());
    village.setLocation(request.location());
    village.setDescription(request.description());
    village.setType(request.type());
    return village;
  }

  public VillageResponseDto toResponse(VillageEntity village) {
    NinjaEntity leader = village.getLeader();
    NinjaReferenceDto leaderReference = leader == null
        ? null
        : new NinjaReferenceDto(leader.getId(), leader.getName());

    return new VillageResponseDto(
        village.getId(),
        village.getName(),
        village.getLocation(),
        village.getDescription(),
        village.getPopulation() == null ? 0 : village.getPopulation(),
        village.getType(),
        leaderReference);
  }
}
