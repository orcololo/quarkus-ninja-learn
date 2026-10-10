package org.acme.users;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.acme.ninjas.NinjaEntity;
import org.acme.ninjas.NinjaRepository;
import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.ninjas.mapper.NinjaMapper;
import org.acme.users.dto.CreateOwnedNinjaRequest;
import org.acme.users.dto.GameUserPageResponseDto;
import org.acme.users.dto.GameUserResponseDto;
import org.acme.users.dto.RegisterUserRequest;
import org.acme.users.dto.UpdateAccountLevelRequest;
import org.acme.users.mapper.GameUserMapper;

import io.quarkus.narayana.jta.QuarkusTransaction;
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
public class GameUserService {
  private final GameUserRepository userRepository;
  private final NinjaRepository ninjaRepository;
  private final NinjaMapper ninjaMapper;
  private final GameUserMapper mapper;
  private final KeycloakProvisioner keycloak;
  private final SecurityIdentity identity;

  GameUserService(
      GameUserRepository userRepository,
      NinjaRepository ninjaRepository,
      NinjaMapper ninjaMapper,
      GameUserMapper mapper,
      KeycloakProvisioner keycloak,
      SecurityIdentity identity) {
    this.userRepository = userRepository;
    this.ninjaRepository = ninjaRepository;
    this.ninjaMapper = ninjaMapper;
    this.mapper = mapper;
    this.keycloak = keycloak;
    this.identity = identity;
  }

  public GameUserResponseDto register(RegisterUserRequest request) {
    String username = request.username().toLowerCase();
    String email = request.email().toLowerCase();
    if (userRepository.count("lower(username) = ?1 or lower(email) = ?2", username, email) > 0) {
      throw new WebApplicationException("Username or email is already registered", Status.CONFLICT);
    }

    String subject = keycloak.createUser(request.username(), request.email(), request.password());
    GameUserEntity user = new GameUserEntity();
    user.setSubject(subject);
    user.setUsername(request.username());
    user.setEmail(request.email());
    user.setLevel(AccountLevel.BASIC);
    try {
      return QuarkusTransaction.requiringNew().call(() -> {
        userRepository.persistAndFlush(user);
        return mapper.toResponse(user);
      });
    } catch (RuntimeException exception) {
      try {
        keycloak.deleteUser(subject);
      } catch (RuntimeException cleanupFailure) {
        exception.addSuppressed(cleanupFailure);
      }
      throw exception;
    }
  }

  public GameUserResponseDto getMe() {
    return mapper.toResponse(currentUser());
  }

  public GameUserPageResponseDto list(int page, int size) {
    List<GameUserEntity> users = userRepository.find("order by id")
        .page(Page.of(page, size))
        .list();
    Map<Long, List<NinjaResponseDto>> ninjasByOwner = new HashMap<>();
    if (!users.isEmpty()) {
      List<Long> userIds = users.stream().map(GameUserEntity::getId).toList();
      for (NinjaEntity ninja : ninjaRepository.find("owner.id in ?1", userIds).list()) {
        ninjasByOwner.computeIfAbsent(ninja.getOwner().getId(), ignored -> new ArrayList<>())
            .add(ninjaMapper.toResponse(ninja));
      }
    }
    return new GameUserPageResponseDto(
        users.stream()
            .map(user -> mapper.toResponse(
                user, ninjasByOwner.getOrDefault(user.getId(), List.of())))
            .toList(),
        page,
        size,
        userRepository.count());
  }

  @Transactional
  public NinjaResponseDto createNinja(CreateOwnedNinjaRequest request) {
    GameUserEntity user = lockUser(currentUser().getId());
    ensureCapacity(user);
    NinjaEntity ninja = mapper.toNinja(request);
    ninja.setOwner(user);
    ninjaRepository.persistAndFlush(ninja);
    return ninjaMapper.toResponse(ninja);
  }

  @Transactional
  public GameUserResponseDto setLevel(Long userId, UpdateAccountLevelRequest request) {
    GameUserEntity user = lockUser(userId);
    long ninjaCount = ninjaRepository.count("owner", user);
    Integer limit = request.level().getNinjaLimit();
    if (limit != null && ninjaCount > limit) {
      throw new WebApplicationException(
          "Remove ninjas before lowering this account's level", Status.CONFLICT);
    }
    user.setLevel(request.level());
    return mapper.toResponse(user);
  }

  @Transactional
  public GameUserResponseDto linkNinja(Long userId, Long ninjaId) {
    GameUserEntity user = lockUser(userId);
    NinjaEntity ninja = findNinja(ninjaId, LockModeType.PESSIMISTIC_WRITE);
    if (ninja.getOwner() == user) {
      return mapper.toResponse(user);
    }
    ensureCapacity(user);
    ninja.setOwner(user);
    return mapper.toResponse(user);
  }

  @Transactional
  public GameUserResponseDto unlinkNinja(Long userId, Long ninjaId) {
    GameUserEntity user = lockUser(userId);
    NinjaEntity ninja = findNinja(ninjaId, LockModeType.PESSIMISTIC_WRITE);
    if (ninja.getOwner() != user) {
      throw new NotFoundException("Ninja is not linked to this account");
    }
    ninja.setOwner(null);
    return mapper.toResponse(user);
  }

  private GameUserEntity currentUser() {
    GameUserEntity user = userRepository.find("subject", identity.getPrincipal().getName()).firstResult();
    if (user == null) {
      throw new ForbiddenException("No game account is linked to this identity");
    }
    return user;
  }

  private GameUserEntity lockUser(Long id) {
    GameUserEntity user = userRepository.findById(id, LockModeType.PESSIMISTIC_WRITE);
    if (user == null) {
      throw new NotFoundException("User not found: " + id);
    }
    return user;
  }

  private NinjaEntity findNinja(Long id, LockModeType lockMode) {
    NinjaEntity ninja = ninjaRepository.findById(id, lockMode);
    if (ninja == null) {
      throw new NotFoundException("Ninja not found: " + id);
    }
    return ninja;
  }

  private void ensureCapacity(GameUserEntity user) {
    long currentCount = ninjaRepository.count("owner", user);
    Integer limit = user.getLevel().getNinjaLimit();
    if (limit != null && currentCount >= limit) {
      throw new WebApplicationException("Account has reached its Ninja limit", Status.CONFLICT);
    }
  }
}
