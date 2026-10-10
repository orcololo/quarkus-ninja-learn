package org.acme.missions;

import java.time.Instant;
import java.util.List;

import org.acme.dto.PageResponseDto;
import org.acme.missions.dto.NinjaMissionResponseDto;
import org.acme.missions.dto.PointBalanceResponseDto;
import org.acme.missions.dto.PointTransactionResponseDto;
import org.acme.missions.mapper.MissionMapper;
import org.acme.ninjas.NinjaEntity;
import org.acme.ninjas.NinjaRepository;
import org.acme.users.GameUserEntity;
import org.acme.users.GameUserRepository;

import io.quarkus.panache.common.Page;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

@ApplicationScoped
public class NinjaMissionService {
  private final NinjaMissionRepository assignmentRepository;
  private final MissionRepository missionRepository;
  private final NinjaRepository ninjaRepository;
  private final GameUserRepository userRepository;
  private final PointTransactionRepository pointTransactionRepository;
  private final MissionMapper missionMapper;
  private final SecurityIdentity identity;

  NinjaMissionService(
      NinjaMissionRepository assignmentRepository,
      MissionRepository missionRepository,
      NinjaRepository ninjaRepository,
      GameUserRepository userRepository,
      PointTransactionRepository pointTransactionRepository,
      MissionMapper missionMapper,
      SecurityIdentity identity) {
    this.assignmentRepository = assignmentRepository;
    this.missionRepository = missionRepository;
    this.ninjaRepository = ninjaRepository;
    this.userRepository = userRepository;
    this.pointTransactionRepository = pointTransactionRepository;
    this.missionMapper = missionMapper;
    this.identity = identity;
  }

  @Transactional
  public NinjaMissionResponseDto assign(Long ninjaId, Long missionId) {
    NinjaEntity ninja = ninjaRepository.findById(ninjaId, LockModeType.PESSIMISTIC_WRITE);
    if (ninja == null) {
      throw new NotFoundException("Ninja not found: " + ninjaId);
    }
    MissionEntity mission = missionRepository.findById(missionId, LockModeType.PESSIMISTIC_WRITE);
    if (mission == null) {
      throw new NotFoundException("Mission not found: " + missionId);
    }
    if (!mission.isActive()) {
      throw new WebApplicationException("Mission is inactive", Status.CONFLICT);
    }
    if (assignmentRepository.find("ninja = ?1 and mission = ?2", ninja, mission).firstResult() != null) {
      throw new WebApplicationException("Mission is already assigned to this ninja", Status.CONFLICT);
    }

    NinjaMissionEntity assignment = new NinjaMissionEntity();
    assignment.setNinja(ninja);
    assignment.setMission(mission);
    assignment.setAssignedAt(Instant.now());
    assignmentRepository.persist(assignment);
    return missionMapper.toResponse(assignment);
  }

  @Transactional
  public NinjaMissionResponseDto submit(Long assignmentId) {
    NinjaMissionEntity assignment = findLockedAssignment(assignmentId);
    requireOwnAssignment(assignment);
    if (assignment.getStatus() == NinjaMissionStatus.SUBMITTED) {
      return missionMapper.toResponse(assignment);
    }
    if (assignment.getStatus() != NinjaMissionStatus.ASSIGNED) {
      throw new WebApplicationException("Only assigned missions can be submitted", Status.CONFLICT);
    }
    assignment.setStatus(NinjaMissionStatus.SUBMITTED);
    assignment.setSubmittedAt(Instant.now());
    return missionMapper.toResponse(assignment);
  }

  @Transactional
  public PointTransactionResponseDto approve(Long assignmentId) {
    NinjaMissionEntity assignment = findLockedAssignment(assignmentId);
    PointTransactionEntity existing = pointTransactionRepository
        .find("ninjaMission", assignment).firstResult();
    if (existing != null) {
      return missionMapper.toResponse(existing);
    }
    if (assignment.getStatus() != NinjaMissionStatus.SUBMITTED) {
      throw new WebApplicationException("Only submitted missions can be approved", Status.CONFLICT);
    }

    PointTransactionEntity transaction = new PointTransactionEntity();
    transaction.setNinja(assignment.getNinja());
    transaction.setNinjaMission(assignment);
    transaction.setPoints(assignment.getMission().getPoints());
    transaction.setGrantedAt(Instant.now());
    assignment.setStatus(NinjaMissionStatus.APPROVED);
    assignment.setDecidedAt(transaction.getGrantedAt());
    pointTransactionRepository.persist(transaction);
    return missionMapper.toResponse(transaction);
  }

  @Transactional
  public NinjaMissionResponseDto reject(Long assignmentId) {
    NinjaMissionEntity assignment = findLockedAssignment(assignmentId);
    if (assignment.getStatus() == NinjaMissionStatus.REJECTED) {
      return missionMapper.toResponse(assignment);
    }
    if (assignment.getStatus() != NinjaMissionStatus.SUBMITTED) {
      throw new WebApplicationException("Only submitted missions can be rejected", Status.CONFLICT);
    }
    assignment.setStatus(NinjaMissionStatus.REJECTED);
    assignment.setDecidedAt(Instant.now());
    return missionMapper.toResponse(assignment);
  }

  public PageResponseDto<NinjaMissionResponseDto> listSubmitted(int page, int size) {
    List<NinjaMissionResponseDto> items = assignmentRepository.listSubmittedPage(page, size).stream()
        .map(missionMapper::toResponse)
        .toList();
    long total = assignmentRepository.count("status", NinjaMissionStatus.SUBMITTED);
    return new PageResponseDto<>(items, page, size, total);
  }

  public NinjaMissionResponseDto get(Long assignmentId) {
    NinjaMissionEntity assignment = assignmentRepository.findById(assignmentId);
    if (assignment == null) {
      throw new NotFoundException("Ninja mission not found: " + assignmentId);
    }
    if (!identity.hasRole("mission-admin")) {
      requireOwnAssignment(assignment);
    }
    return missionMapper.toResponse(assignment);
  }

  public PointBalanceResponseDto getPointBalance(Long ninjaId, int page, int size) {
    if (ninjaRepository.findById(ninjaId) == null) {
      throw new NotFoundException("Ninja not found: " + ninjaId);
    }
    if (!identity.hasRole("mission-admin")
        && !ownsNinja(ninjaRepository.findById(ninjaId))) {
      throw new ForbiddenException("Ninjas can only view their own points");
    }
    long balance = pointTransactionRepository.getEntityManager()
        .createQuery(
            "select coalesce(sum(transaction.points), 0) "
                + "from PointTransactionEntity transaction where transaction.ninja.id = ?1",
            Long.class)
        .setParameter(1, ninjaId)
        .getSingleResult();
    long totalTransactions = pointTransactionRepository.find("ninja.id", ninjaId).count();
    List<PointTransactionEntity> transactions = pointTransactionRepository
        .find("ninja.id = ?1 order by grantedAt desc, id desc", ninjaId)
        .page(Page.of(page, size))
        .list();
    return new PointBalanceResponseDto(
        balance,
        transactions.stream().map(missionMapper::toResponse).toList(),
        page,
        size,
        totalTransactions);
  }

  private NinjaMissionEntity findLockedAssignment(Long id) {
    NinjaMissionEntity assignment = assignmentRepository.findById(id, LockModeType.PESSIMISTIC_WRITE);
    if (assignment == null) {
      throw new NotFoundException("Ninja mission not found: " + id);
    }
    return assignment;
  }

  private void requireOwnAssignment(NinjaMissionEntity assignment) {
    if (!ownsNinja(assignment.getNinja())) {
      throw new ForbiddenException("Ninjas can only submit their own missions");
    }
  }

  private boolean ownsNinja(NinjaEntity ninja) {
    GameUserEntity user = userRepository.find("subject", identity.getPrincipal().getName()).firstResult();
    return ninja != null && user != null && ninja.getOwner() != null
        && ninja.getOwner().getId().equals(user.getId());
  }
}
