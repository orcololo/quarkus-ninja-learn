package org.acme.restaurants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class RestaurantSearchOutboxWorker {
  private static final int BATCH_SIZE = 100;
  private static final int MAX_BATCHES_PER_RUN = 10;
  private static final int REINDEX_BATCH_SIZE = 500;
  private static final long LOCK_ID = 5_389_012L;
  private final DataSource dataSource;
  private final EntityManager entityManager;
  private final RestaurantRepository restaurants;
  private final MenuItemRepository menuItems;
  private final RestaurantOpeningPeriodRepository openingPeriods;
  private final RestaurantSearchIndex searchIndex;
  private final ObjectMapper objectMapper;

  RestaurantSearchOutboxWorker(
      DataSource dataSource,
      EntityManager entityManager,
      RestaurantRepository restaurants,
      MenuItemRepository menuItems,
      RestaurantOpeningPeriodRepository openingPeriods,
      RestaurantSearchIndex searchIndex,
      ObjectMapper objectMapper) {
    this.dataSource = dataSource;
    this.entityManager = entityManager;
    this.restaurants = restaurants;
    this.menuItems = menuItems;
    this.openingPeriods = openingPeriods;
    this.searchIndex = searchIndex;
    this.objectMapper = objectMapper;
  }

  @Scheduled(every = "2s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void processPendingEvents() {
    try (Connection connection = dataSource.getConnection()) {
      if (!tryLock(connection)) {
        return;
      }
      try {
        enqueueReindexPage();
        boolean restaurantIndexReady = false;
        boolean menuIndexReady = false;
        for (int batchNumber = 0; batchNumber < MAX_BATCHES_PER_RUN; batchNumber++) {
          Batch batch = loadBatch();
          if (batch.eventIds().isEmpty()) {
            return;
          }
          if (batch.restaurantOperationCount() > 0) {
            if (!restaurantIndexReady) {
              searchIndex.ensureIndex();
              restaurantIndexReady = true;
            }
            searchIndex.bulk(
                RestaurantSearchIndex.INDEX,
                batch.restaurantOperations(),
                batch.restaurantOperationCount());
          }
          if (batch.menuOperationCount() > 0) {
            if (!menuIndexReady) {
              searchIndex.ensureMenuIndex();
              menuIndexReady = true;
            }
            searchIndex.bulk(
                RestaurantSearchIndex.MENU_INDEX,
                batch.menuOperations(),
                batch.menuOperationCount());
          }
          acknowledge(batch.eventIds());
        }
      } finally {
        unlock(connection);
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Could not acquire or release the restaurant outbox lock", exception);
    }
  }

  private void enqueueReindexPage() {
    QuarkusTransaction.requiringNew().run(() -> {
      List<RestaurantReindexJobEntity> jobs = entityManager.createQuery(
          "from RestaurantReindexJobEntity j where j.complete = false order by j.id",
          RestaurantReindexJobEntity.class)
          .setLockMode(LockModeType.PESSIMISTIC_WRITE)
          .setMaxResults(1)
          .getResultList();
      if (jobs.isEmpty()) {
        return;
      }
      RestaurantReindexJobEntity job = jobs.getFirst();
      if (!job.isRestaurantsDone()) {
        List<RestaurantEntity> batch = restaurants.findAfterId(
            job.getRestaurantAfterId(), REINDEX_BATCH_SIZE);
        batch.forEach(restaurant -> enqueueSearchChange(
            RestaurantSearchAggregateType.RESTAURANT, restaurant.getId()));
        if (!batch.isEmpty()) {
          job.setRestaurantAfterId(batch.getLast().getId());
          job.setRestaurantsQueued(job.getRestaurantsQueued() + batch.size());
        }
        job.setRestaurantsDone(batch.size() < REINDEX_BATCH_SIZE);
      }
      if (!job.isMenuItemsDone()) {
        List<MenuItemEntity> batch = menuItems.findAfterId(
            job.getMenuItemAfterId(), REINDEX_BATCH_SIZE);
        batch.forEach(item -> enqueueSearchChange(
            RestaurantSearchAggregateType.MENU_ITEM, item.getId()));
        if (!batch.isEmpty()) {
          job.setMenuItemAfterId(batch.getLast().getId());
          job.setMenuItemsQueued(job.getMenuItemsQueued() + batch.size());
        }
        job.setMenuItemsDone(batch.size() < REINDEX_BATCH_SIZE);
      }
      job.setComplete(job.isRestaurantsDone() && job.isMenuItemsDone());
    });
  }

  private void enqueueSearchChange(RestaurantSearchAggregateType aggregateType, long aggregateId) {
    RestaurantSearchOutboxEntity event = new RestaurantSearchOutboxEntity();
    event.setAggregateType(aggregateType);
    event.setAggregateId(aggregateId);
    entityManager.persist(event);
  }

  private Batch loadBatch() {
    return QuarkusTransaction.requiringNew().call(() -> {
      List<RestaurantSearchOutboxEntity> events = entityManager.createQuery(
          "from RestaurantSearchOutboxEntity order by id", RestaurantSearchOutboxEntity.class)
          .setMaxResults(BATCH_SIZE)
          .getResultList();
      if (events.isEmpty()) {
        return new Batch(List.of(), "", 0, "", 0);
      }
      List<Long> restaurantIds = idsFor(events, RestaurantSearchAggregateType.RESTAURANT);
      List<Long> menuItemIds = idsFor(events, RestaurantSearchAggregateType.MENU_ITEM);
      Map<Long, RestaurantEntity> currentRestaurants = new HashMap<>();
      if (!restaurantIds.isEmpty()) {
        restaurants.findRestaurantsByIds(restaurantIds)
            .forEach(restaurant -> currentRestaurants.put(restaurant.getId(), restaurant));
      }
      Map<Long, List<RestaurantOpeningPeriodEntity>> periodsByRestaurant =
          openingPeriods.listForRestaurants(restaurantIds).stream()
              .collect(Collectors.groupingBy(period -> period.getRestaurantId()));
      Map<Long, MenuItemEntity> currentMenuItems = new HashMap<>();
      if (!menuItemIds.isEmpty()) {
        menuItems.findItemsWithIds(menuItemIds)
            .forEach(item -> currentMenuItems.put(item.getId(), item));
      }

      StringBuilder restaurantOperations = new StringBuilder(restaurantIds.size() * 200);
      for (Long restaurantId : restaurantIds) {
        RestaurantEntity restaurant = currentRestaurants.get(restaurantId);
        try {
          if (restaurant == null) {
            restaurantOperations.append("{\"delete\":{\"_id\":\"").append(restaurantId).append("\"}}\n");
          } else {
            restaurantOperations.append("{\"index\":{\"_id\":\"").append(restaurantId).append("\"}}\n")
                .append(objectMapper.writeValueAsString(RestaurantSearchDocument.from(
                    restaurant,
                    periodsByRestaurant.getOrDefault(restaurant.getId(), List.of()))))
                .append('\n');
          }
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Could not serialize restaurant for search indexing", exception);
        }
      }
      StringBuilder menuOperations = new StringBuilder(menuItemIds.size() * 200);
      for (Long menuItemId : menuItemIds) {
        MenuItemEntity item = currentMenuItems.get(menuItemId);
        try {
          if (item == null) {
            menuOperations.append("{\"delete\":{\"_id\":\"").append(menuItemId).append("\"}}\n");
          } else {
            menuOperations.append("{\"index\":{\"_id\":\"").append(menuItemId).append("\"}}\n")
                .append(objectMapper.writeValueAsString(MenuItemSearchDocument.from(item)))
                .append('\n');
          }
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Could not serialize menu item for search indexing", exception);
        }
      }
      return new Batch(
          events.stream().map(event -> event.getId()).toList(),
          restaurantOperations.toString(),
          restaurantIds.size(),
          menuOperations.toString(),
          menuItemIds.size());
    });
  }

  private List<Long> idsFor(
      List<RestaurantSearchOutboxEntity> events, RestaurantSearchAggregateType aggregateType) {
    return events.stream()
        .filter(event -> aggregateType.equals(event.getAggregateType()))
        .map(event -> event.getAggregateId())
        .distinct()
        .toList();
  }

  private void acknowledge(List<Long> eventIds) {
    QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery(
        "delete from RestaurantSearchOutboxEntity e where e.id in :ids")
        .setParameter("ids", eventIds)
        .executeUpdate());
  }

  private boolean tryLock(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "select pg_try_advisory_lock(?)")) {
      statement.setLong(1, LOCK_ID);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getBoolean(1);
      }
    }
  }

  private void unlock(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "select pg_advisory_unlock(?)")) {
      statement.setLong(1, LOCK_ID);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        if (!result.getBoolean(1)) {
          throw new IllegalStateException("Restaurant outbox lock was not held");
        }
      }
    }
  }

  private record Batch(
      List<Long> eventIds,
      String restaurantOperations,
      int restaurantOperationCount,
      String menuOperations,
      int menuOperationCount) {
  }
}
