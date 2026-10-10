package org.acme.missions;

import java.util.List;

import org.acme.missions.dto.CreateMissionRequest;
import org.acme.missions.dto.MissionResponseDto;
import org.acme.missions.dto.UpdateMissionRequest;
import org.acme.missions.mapper.MissionMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class MissionService {
  private final MissionRepository missionRepository;
  private final MissionMapper missionMapper;

  MissionService(MissionRepository missionRepository, MissionMapper missionMapper) {
    this.missionRepository = missionRepository;
    this.missionMapper = missionMapper;
  }

  @Transactional
  public MissionResponseDto create(CreateMissionRequest request) {
    MissionEntity mission = missionMapper.toEntity(request);
    missionRepository.persist(mission);
    return missionMapper.toResponse(mission);
  }

  public List<MissionResponseDto> list() {
    return missionRepository.listAll().stream().map(missionMapper::toResponse).toList();
  }

  public MissionResponseDto get(Long id) {
    return missionMapper.toResponse(findMission(id));
  }

  @Transactional
  public MissionResponseDto update(Long id, UpdateMissionRequest request) {
    MissionEntity mission = findMission(id);
    missionMapper.update(mission, request);
    return missionMapper.toResponse(mission);
  }

  @Transactional
  public MissionResponseDto setActive(Long id, boolean active) {
    MissionEntity mission = findMission(id);
    mission.setActive(active);
    return missionMapper.toResponse(mission);
  }

  MissionEntity findMission(Long id) {
    MissionEntity mission = missionRepository.findById(id);
    if (mission == null) {
      throw new NotFoundException("Mission not found: " + id);
    }
    return mission;
  }
}
