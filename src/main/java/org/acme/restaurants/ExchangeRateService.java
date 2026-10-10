package org.acme.restaurants;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ServiceUnavailableException;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ExchangeRateService {
  private static final Logger LOG = Logger.getLogger(ExchangeRateService.class);
  private static final long SNAPSHOT_ID = 1L;
  private static final String PRIMARY_URL =
      "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json";
  private static final String FALLBACK_URL =
      "https://latest.currency-api.pages.dev/v1/currencies/usd.json";
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private final ExchangeRateSnapshotRepository snapshots;
  private final EntityManager entityManager;
  private final ObjectMapper objectMapper;

  ExchangeRateService(
      ExchangeRateSnapshotRepository snapshots,
      EntityManager entityManager,
      ObjectMapper objectMapper) {
    this.snapshots = snapshots;
    this.entityManager = entityManager;
    this.objectMapper = objectMapper;
  }

  void onStartup(@Observes io.quarkus.runtime.StartupEvent ignored) {
    if (io.quarkus.runtime.LaunchMode.current() == io.quarkus.runtime.LaunchMode.TEST) {
      return;
    }
    ExchangeRateSnapshotEntity snapshot = snapshots.findById(SNAPSHOT_ID);
    if (snapshot == null || snapshot.getFetchedAt()
        .atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now(ZoneOffset.UTC))) {
      refresh();
    }
  }

  @Scheduled(cron = "0 15 4 * * ?", timeZone = "UTC")
  void refreshDaily() {
    refresh();
  }

  public PriceConversion conversionTo(String targetCurrency) {
    String target = normalizeCurrency(targetCurrency);
    ExchangeRateSnapshotEntity snapshot = snapshots.findById(SNAPSHOT_ID);
    if (snapshot == null) {
      throw new ServiceUnavailableException("Exchange rates are not available yet");
    }
    try {
      JsonNode json = objectMapper.readTree(snapshot.getRatesJson());
      if (json == null || !json.isObject()) {
        throw new IllegalStateException("Saved exchange-rate snapshot must be an object");
      }
      Map<String, BigDecimal> rates = new HashMap<>();
      json.properties().forEach(entry -> {
        if (!entry.getValue().isNumber() || entry.getValue().decimalValue().signum() <= 0) {
          throw new IllegalStateException("Saved exchange-rate snapshot contains an invalid rate");
        }
        rates.put(entry.getKey(), entry.getValue().decimalValue());
      });
      if (!rates.containsKey("usd") || !rates.containsKey(target.toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException("Saved exchange-rate snapshot is missing a currency");
      }
      return new PriceConversion(target, snapshot.getRateDate(), rates);
    } catch (IOException exception) {
      throw new IllegalStateException("Saved exchange-rate snapshot is invalid", exception);
    } catch (IllegalStateException exception) {
      throw new ServiceUnavailableException(
          jakarta.ws.rs.core.Response.status(503)
              .entity("Saved exchange-rate snapshot is invalid")
              .build(),
          exception);
    }
  }

  private void refresh() {
    try {
      SnapshotData data;
      try {
        data = fetch(PRIMARY_URL);
      } catch (RuntimeException primaryFailure) {
        try {
          data = fetch(FALLBACK_URL);
        } catch (RuntimeException fallbackFailure) {
          fallbackFailure.addSuppressed(primaryFailure);
          throw new IllegalStateException("Both exchange-rate providers failed", fallbackFailure);
        }
      }
      persist(data);
    } catch (RuntimeException exception) {
      LOG.warn("Could not refresh exchange rates; the last saved snapshot is retained", exception);
    }
  }

  private SnapshotData fetch(String url) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .header("Accept", "application/json")
        .GET()
        .build();
    try {
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Exchange-rate provider returned HTTP " + response.statusCode());
      }
      JsonNode root = objectMapper.readTree(response.body());
      LocalDate rateDate = LocalDate.parse(root.path("date").asText());
      JsonNode usdRates = root.path("usd");
      if (!usdRates.isObject()) {
        throw new IllegalStateException("Exchange-rate response does not contain USD rates");
      }
      Map<String, BigDecimal> rates = new HashMap<>();
      usdRates.properties().forEach(entry -> {
        String code = entry.getKey().toLowerCase(Locale.ROOT);
        JsonNode value = entry.getValue();
        if (!code.matches("[a-z]{3}") || !value.isNumber() || value.decimalValue().signum() <= 0) {
          throw new IllegalStateException("Exchange-rate response contains an invalid rate");
        }
        rates.put(code, value.decimalValue());
      });
      if (!rates.containsKey("usd")) {
        throw new IllegalStateException("Exchange-rate response does not contain the USD rate");
      }
      return new SnapshotData(rateDate, objectMapper.writeValueAsString(rates));
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read exchange-rate response", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while fetching exchange rates", exception);
    }
  }

  private void persist(SnapshotData data) {
    Instant fetchedAt = Instant.now();
    QuarkusTransaction.requiringNew().run(() -> {
      entityManager.createNativeQuery("""
          insert into restaurant_exchange_rate_snapshot (id, rate_date, fetched_at, rates_json)
          values (1, ?1, ?2, ?3)
          on conflict (id) do update
          set rate_date = excluded.rate_date,
              fetched_at = excluded.fetched_at,
              rates_json = excluded.rates_json
          """)
          .setParameter(1, data.rateDate())
          .setParameter(2, fetchedAt)
          .setParameter(3, data.ratesJson())
          .executeUpdate();
    });
  }

  private String normalizeCurrency(String code) {
    String normalized = code.trim().toUpperCase(Locale.ROOT);
    try {
      Currency.getInstance(normalized);
    } catch (IllegalArgumentException exception) {
      throw new jakarta.ws.rs.BadRequestException("currency must be a supported ISO 4217 code", exception);
    }
    return normalized;
  }

  private record SnapshotData(LocalDate rateDate, String ratesJson) {
  }

  public record DisplayPrice(BigDecimal amount, String currency, LocalDate rateDate) {
  }

  public record PriceConversion(
      String targetCurrency, LocalDate rateDate, Map<String, BigDecimal> usdRates) {
    public DisplayPrice convert(BigDecimal amount, String sourceCurrency) {
      String source = sourceCurrency.toLowerCase(Locale.ROOT);
      String target = targetCurrency.toLowerCase(Locale.ROOT);
      BigDecimal sourceRate = usdRates.get(source);
      BigDecimal targetRate = usdRates.get(target);
      if (sourceRate == null || targetRate == null) {
        throw new ServiceUnavailableException(
            "Exchange-rate snapshot does not include " + sourceCurrency + " or " + targetCurrency);
      }
      int configuredDigits = Currency.getInstance(targetCurrency).getDefaultFractionDigits();
      int fractionDigits = configuredDigits < 0 ? 2 : configuredDigits;
      BigDecimal converted = amount.multiply(targetRate)
          .divide(sourceRate, MathContext.DECIMAL128)
          .setScale(fractionDigits, RoundingMode.HALF_UP);
      return new DisplayPrice(converted, targetCurrency, rateDate);
    }
  }
}
