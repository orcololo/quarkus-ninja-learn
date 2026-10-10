package org.acme.missions.mapper;

import org.acme.missions.MissionEntity;
import org.acme.missions.NinjaMissionEntity;
import org.acme.missions.PointTransactionEntity;
import org.acme.missions.dto.CreateMissionRequest;
import org.acme.missions.dto.MissionResponseDto;
import org.acme.missions.dto.NinjaMissionResponseDto;
import org.acme.missions.dto.PointTransactionResponseDto;
import org.acme.missions.dto.UpdateMissionRequest;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MissionMapper {

  public MissionEntity toEntity(CreateMissionRequest request) {
    MissionEntity mission = new MissionEntity();
    mission.setTitle(request.title());
    mission.setDescription(request.description());
    mission.setPoints(request.points());
    mission.setActive(true);
    return mission;
  }

  public void update(MissionEntity mission, UpdateMissionRequest request) {
    mission.setTitle(request.title());
    mission.setDescription(request.description());
    mission.setPoints(request.points());
  }

  public MissionResponseDto toResponse(MissionEntity mission) {
    return new MissionResponseDto(
        mission.getId(),
        mission.getTitle(),
        mission.getDescription(),
        mission.getPoints(),
        mission.isActive());
  }

  public NinjaMissionResponseDto toResponse(NinjaMissionEntity assignment) {
    return new NinjaMissionResponseDto(
        assignment.getId(),
        assignment.getNinja().getId(),
        assignment.getMission().getId(),
        assignment.getMission().getTitle(),
        assignment.getMission().getPoints(),
        assignment.getStatus(),
        assignment.getAssignedAt(),
        assignment.getSubmittedAt(),
        assignment.getDecidedAt());
  }

  public PointTransactionResponseDto toResponse(PointTransactionEntity transaction) {
    return new PointTransactionResponseDto(
        transaction.getId(),
        transaction.getNinja().getId(),
        transaction.getNinjaMission().getId(),
        transaction.getPoints(),
        transaction.getGrantedAt());
  }
}
