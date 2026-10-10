package org.acme.users.mapper;

import java.util.List;

import org.acme.ninjas.NinjaEntity;
import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.ninjas.mapper.NinjaMapper;
import org.acme.users.GameUserEntity;
import org.acme.users.dto.CreateOwnedNinjaRequest;
import org.acme.users.UserDeliveryAddressEntity;
import org.acme.users.dto.DeliveryAddressRequest;
import org.acme.users.dto.DeliveryAddressResponseDto;
import org.acme.users.dto.GameUserResponseDto;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GameUserMapper {
  private final NinjaMapper ninjaMapper;

  GameUserMapper(NinjaMapper ninjaMapper) {
    this.ninjaMapper = ninjaMapper;
  }

  public GameUserResponseDto toResponse(GameUserEntity user) {
    return toResponse(user, user.getNinjas().stream().map(ninjaMapper::toResponse).toList());
  }

  public GameUserResponseDto toResponse(GameUserEntity user, List<NinjaResponseDto> ninjas) {
    return new GameUserResponseDto(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getLevel(),
        user.getLevel().getNinjaLimit(),
        ninjas);
  }

  public NinjaEntity toNinja(CreateOwnedNinjaRequest request) {
    NinjaEntity ninja = new NinjaEntity();
    ninja.setName(request.name());
    ninja.setAge(request.age());
    ninja.setLevel(request.level());
    return ninja;
  }

  public void update(UserDeliveryAddressEntity address, DeliveryAddressRequest request) {
    address.setLabel(request.label());
    address.setRecipientName(request.recipientName());
    address.setPhone(request.phone());
    address.setStreet(request.street());
    address.setNumber(request.number());
    address.setComplement(request.complement());
    address.setNeighborhood(request.neighborhood());
    address.setCity(request.city());
    address.setState(request.state());
    address.setPostalCode(request.postalCode());
    address.setCountry(request.country().toUpperCase(java.util.Locale.ROOT));
    address.setDeliveryInstructions(request.deliveryInstructions());
  }

  public DeliveryAddressResponseDto toAddressResponse(UserDeliveryAddressEntity address) {
    return new DeliveryAddressResponseDto(
        address.getId(),
        address.getLabel(),
        address.getRecipientName(),
        address.getPhone(),
        address.getStreet(),
        address.getNumber(),
        address.getComplement(),
        address.getNeighborhood(),
        address.getCity(),
        address.getState(),
        address.getPostalCode(),
        address.getCountry(),
        address.getDeliveryInstructions(),
        address.isDefaultAddress(),
        address.getCreatedAt());
  }
}
