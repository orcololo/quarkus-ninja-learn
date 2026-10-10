package org.acme.village;

import java.util.List;

import org.acme.dto.PageResponseDto;
import org.acme.ninjas.NinjaEntity;
import org.acme.ninjas.NinjaRepository;
import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.ninjas.mapper.NinjaMapper;
import org.acme.village.dto.CreateVillageRequest;
import org.acme.village.dto.UpdateVillageRequest;
import org.acme.village.dto.VillageResponseDto;
import org.acme.village.mapper.VillageMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class VillageService {

  private final VillageRepository villageRepository;
  private final NinjaRepository ninjaRepository;
  private final VillageMapper villageMapper;
  private final NinjaMapper ninjaMapper;
  private final EntityManager entityManager;

  VillageService(
      VillageRepository villageRepository,
      NinjaRepository ninjaRepository,
      VillageMapper villageMapper,
      NinjaMapper ninjaMapper,
      EntityManager entityManager) {
    this.villageRepository = villageRepository;
    this.ninjaRepository = ninjaRepository;
    this.villageMapper = villageMapper;
    this.ninjaMapper = ninjaMapper;
    this.entityManager = entityManager;
  }

  @Transactional
  public VillageResponseDto createVillage(CreateVillageRequest request) {
    VillageEntity village = villageMapper.toEntity(request);
    if (request.leaderId() != null) {
      NinjaEntity leader = ninjaRepository.findById(request.leaderId());
      if (leader == null) {
        throw new NotFoundException("Ninja not found: " + request.leaderId());
      }
      village.setLeader(leader);
    }
    villageRepository.persist(village);
    refreshPopulation(village);
    return villageMapper.toResponse(village);
  }

  public PageResponseDto<VillageResponseDto> getAllVillages(int page, int size) {
    List<VillageResponseDto> items = villageRepository.listPage(page, size).stream()
        .map(villageMapper::toResponse)
        .toList();
    return new PageResponseDto<>(items, page, size, villageRepository.count());
  }

  public VillageResponseDto getVillage(Long id) {
    return villageMapper.toResponse(findVillage(id));
  }

  @Transactional
  public VillageResponseDto updateVillage(Long id, UpdateVillageRequest request) {
    VillageEntity village = findVillage(id);
    village.setName(request.name());
    village.setLocation(request.location());
    village.setDescription(request.description());
    village.setType(request.type());
    return villageMapper.toResponse(village);
  }

  @Transactional
  public void deleteVillage(Long id) {
    VillageEntity village = findVillage(id);
    village.setLeader(null);
    for (NinjaEntity ninja : List.copyOf(village.getNinjas())) {
      ninja.setVillage(null);
    }
    villageRepository.delete(village);
  }

  @Transactional
  public VillageResponseDto assignLeader(Long villageId, Long ninjaId) {
    VillageEntity village = findVillage(villageId);
    NinjaEntity ninja = findNinja(ninjaId);
    village.setLeader(ninja);
    refreshPopulation(village);
    return villageMapper.toResponse(village);
  }

  @Transactional
  public VillageResponseDto removeLeader(Long villageId) {
    VillageEntity village = findVillage(villageId);
    village.setLeader(null);
    refreshPopulation(village);
    return villageMapper.toResponse(village);
  }

  public PageResponseDto<NinjaResponseDto> getNinjas(Long villageId, int page, int size) {
    VillageEntity village = findVillage(villageId);
    List<NinjaResponseDto> items = ninjaRepository.find("village = ?1 order by id", village)
        .page(io.quarkus.panache.common.Page.of(page, size))
        .list()
        .stream()
        .map(ninjaMapper::toResponse)
        .toList();
    long total = ninjaRepository.count("village", village);
    return new PageResponseDto<>(items, page, size, total);
  }

  @Transactional
  public NinjaResponseDto assignNinja(Long villageId, Long ninjaId) {
    VillageEntity village = findVillage(villageId);
    NinjaEntity ninja = findNinja(ninjaId);
    ninja.setVillage(village);
    refreshPopulation(village);
    return ninjaMapper.toResponse(ninja);
  }

  @Transactional
  public void removeNinja(Long villageId, Long ninjaId) {
    VillageEntity village = findVillage(villageId);
    NinjaEntity ninja = findNinja(ninjaId);
    if (ninja.getVillage() != village) {
      throw new NotFoundException("Ninja is not a member of village: " + villageId);
    }
    village.removeNinja(ninja);
    refreshPopulation(village);
  }

  private VillageEntity findVillage(Long id) {
    VillageEntity village = villageRepository.findById(id);
    if (village == null) {
      throw new NotFoundException("Village not found: " + id);
    }
    return village;
  }

  private NinjaEntity findNinja(Long id) {
    NinjaEntity ninja = ninjaRepository.findById(id);
    if (ninja == null) {
      throw new NotFoundException("Ninja not found: " + id);
    }
    return ninja;
  }

  private void refreshPopulation(VillageEntity village) {
    entityManager.flush();
    entityManager.refresh(village);
  }
}
