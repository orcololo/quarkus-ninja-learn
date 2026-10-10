package org.acme.ninjas.mapper;

import org.acme.ninjas.NinjaEntity;
import org.acme.ninjas.dto.CreateNinjaRequest;
import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.ninjas.dto.VillageReferenceDto;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NinjaMapper {

  public NinjaEntity toEntity(CreateNinjaRequest request) {
    NinjaEntity ninja = new NinjaEntity();
    ninja.setName(request.name());
    ninja.setAge(request.age());
    ninja.setLevel(request.level());
    return ninja;
  }

  public NinjaResponseDto toResponse(NinjaEntity ninja) {
    VillageReferenceDto village = ninja.getVillage() == null
        ? null
        : new VillageReferenceDto(ninja.getVillage().getId(), ninja.getVillage().getName());

    return new NinjaResponseDto(
        ninja.getId(),
        ninja.getName(),
        ninja.getAge(),
        ninja.getLevel(),
        village);
  }
}
