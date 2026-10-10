package org.acme.restaurants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class RestaurantSearchOutboxWorker {
  private static final int BATCH_SIZE = 100;
  private static final int MAX_BATCHES_PER_RUN = 10;
  private static final long LOCK_ID = 5_389_012L;
  private final DataSource dataSource;
  private final EntityManager entityManager;
  private final RestaurantRepository restaurants;
  private final RestaurantSearchIndex searchIndex;
  private final ObjectMapper objectMapper;

  RestaurantSearchOutboxWorker(
      DataSource dataSource,
      EntityManager entityManager,
      RestaurantRepository restaurants,
      RestaurantSearchIndex searchIndex,
      ObjectMapper objectMapper) {
    this.dataSource = dataSource;
    this.entityManager = entityManager;
    this.restaurants = restaurants;
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
        boolean indexReady = false;
        for (int batchNumber = 0; batchNumber < MAX_BATCHES_PER_RUN; batchNumber++) {
          Batch batch = loadBatch();
          if (batch.eventIds().isEmpty()) {
            return;
          }
          if (!indexReady) {
            searchIndex.ensureIndex();
            indexReady = true;
          }
          searchIndex.bulk(batch.operations(), batch.operationCount());
          acknowledge(batch.eventIds());
        }
      } finally {
        unlock(connection);
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Could not acquire or release the restaurant outbox lock", exception);
    }
  }

  private Batch loadBatch() {
    return QuarkusTransaction.requiringNew().call(() -> {
      List<RestaurantSearchOutboxEntity> events = entityManager.createQuery(
          "from RestaurantSearchOutboxEntity order by id", RestaurantSearchOutboxEntity.class)
          .setMaxResults(BATCH_SIZE)
          .getResultList();
      if (events.isEmpty()) {
        return new Batch(List.of(), "", 0);
      }

      List<Long> restaurantIds = events.stream()
          .map(RestaurantSearchOutboxEntity::getRestaurantId)
          .distinct()
          .toList();
      Map<Long, RestaurantEntity> currentRestaurants = new HashMap<>();
      restaurants.findRestaurantsByIds(restaurantIds)
          .forEach(restaurant -> currentRestaurants.put(restaurant.getId(), restaurant));

      StringBuilder operations = new StringBuilder(restaurantIds.size() * 200);
      for (Long restaurantId : restaurantIds) {
        RestaurantEntity restaurant = currentRestaurants.get(restaurantId);
        try {
          if (restaurant == null) {
            operations.append("{\"delete\":{\"_id\":\"").append(restaurantId).append("\"}}\n");
          } else {
            operations.append("{\"index\":{\"_id\":\"").append(restaurantId).append("\"}}\n")
                .append(objectMapper.writeValueAsString(RestaurantSearchDocument.from(restaurant)))
                .append('\n');
          }
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Could not serialize restaurant for search indexing", exception);
        }
      }
      return new Batch(events.stream().map(RestaurantSearchOutboxEntity::getId).toList(),
          operations.toString(), restaurantIds.size());
    });
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

  private record Batch(List<Long> eventIds, String operations, int operationCount) {
  }
}
