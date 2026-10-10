package org.acme.ninjas;

import java.util.List;

import org.acme.ninjas.dto.CreateNinjaRequest;
import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.ninjas.mapper.NinjaMapper;
import org.acme.village.VillageEntity;
import org.acme.village.VillageRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class NinjaService {

  private final NinjaRepository ninjaRepository;
  private final VillageRepository villageRepository;
  private final NinjaMapper ninjaMapper;

  NinjaService(
      NinjaRepository ninjaRepository,
      VillageRepository villageRepository,
      NinjaMapper ninjaMapper) {
    this.ninjaRepository = ninjaRepository;
    this.villageRepository = villageRepository;
    this.ninjaMapper = ninjaMapper;
  }

  @Transactional
  public NinjaResponseDto createNinja(CreateNinjaRequest request) {
    NinjaEntity ninja = ninjaMapper.toEntity(request);
    if (request.villageId() != null) {
      VillageEntity village = villageRepository.findById(request.villageId());
      if (village == null) {
        throw new NotFoundException("Village not found: " + request.villageId());
      }
      ninja.setVillage(village);
    }
    ninjaRepository.persist(ninja);
    return ninjaMapper.toResponse(ninja);
  }

  public List<NinjaResponseDto> getAllNinjas() {
    return ninjaRepository.listAll().stream()
        .map(ninjaMapper::toResponse)
        .toList();
  }

  public NinjaResponseDto getNinja(Long id) {
    NinjaEntity ninja = ninjaRepository.findById(id);
    if (ninja == null) {
      throw new NotFoundException("Ninja not found: " + id);
    }
    return ninjaMapper.toResponse(ninja);
  }
}
