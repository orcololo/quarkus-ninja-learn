# Restaurant locator

This document describes the restaurant and review API, its PostgreSQL and
Elasticsearch data model, indexing lifecycle, configuration, and tests.

## Architecture and consistency

PostgreSQL is the source of truth for restaurants, reviews, and review
aggregates. Elasticsearch is a derived index used for text, city, cuisine, and
geographic search. The application sends all search criteria to Elasticsearch;
it does not run geographic search in PostgreSQL.

Restaurant create, update, delete, and review operations write PostgreSQL state
and an outbox row in the same transaction. After commit, the scheduled worker
delivers pending outbox changes to Elasticsearch. A successful write response
does not wait for indexing, so search can briefly return stale results.

The worker runs every two seconds and processes up to ten batches of 100
outbox events per run. For each batch it:

1. Reads the events and the current PostgreSQL rows for their restaurant IDs in
   a short transaction.
2. Coalesces events for the same restaurant and builds one Elasticsearch bulk
   operation per distinct ID, indexing the latest row or deleting its document
   if that restaurant no longer exists.
3. Sends the bulk request without a database transaction.
4. Deletes the selected outbox rows in a new transaction only after the bulk
   response confirms that each item succeeded. Deleting a missing Elasticsearch
   document is considered successful.

A PostgreSQL session advisory lock on a dedicated borrowed connection prevents
multiple application instances from delivering the same batch concurrently.
The lock is released in a `finally` block and also when PostgreSQL closes that
connection. If delivery fails or the process stops before acknowledgement, the
events remain queued. A batch may be delivered again if Elasticsearch accepted
it but the process stopped before acknowledgement; indexing uses restaurant IDs
as document IDs, making these retries idempotent.

The worker's 100-event limit also bounds the number of distinct restaurant
operations in a bulk request. It retains failed batches for retry rather than
acknowledging individual successful items from a partially failed bulk request.
Retries happen on a later scheduled run; there is no dead-letter queue or
automatic event deletion after repeated failures. If Elasticsearch is
unavailable, restaurant writes can still commit because the outbox is in
PostgreSQL, while restaurant search returns `503 Service Unavailable`.

Search first gets matching document IDs, order, and exact total from
Elasticsearch, then loads the corresponding restaurant rows from PostgreSQL.
The response therefore uses current PostgreSQL fields, not copied index fields.
While the index is catching up, a result page can omit a restaurant that was
deleted after indexing, or have a total that briefly differs from the number of
returned PostgreSQL rows.

## HTTP API

All routes are under `/restaurants`. JSON request bodies are validated at the
HTTP boundary.

| Method and path | Access | Behavior |
| --- | --- | --- |
| `POST /restaurants` | `mission-admin` | Create a restaurant; returns `201 Created` and its representation. |
| `GET /restaurants/{id}` | Public | Get a restaurant; returns `404` if absent. |
| `PUT /restaurants/{id}` | `mission-admin` | Replace restaurant fields; returns `404` if absent. |
| `DELETE /restaurants/{id}` | `mission-admin` | Delete the restaurant and its reviews; returns `204 No Content`, or `404` if absent. |
| `GET /restaurants/search` | Public | Search and page restaurants using Elasticsearch. |
| `GET /restaurants/{id}/reviews` | Public | List a restaurant's paged reviews; returns `404` if the restaurant is absent. |
| `GET /restaurants/{id}/reviews/{reviewId}` | Public | Get one review; returns `404` if either record is absent or the review belongs to another restaurant. |
| `POST /restaurants/{id}/reviews` | Authenticated OIDC identity | Create one review per identity per restaurant; returns `201 Created`, `404` for an absent restaurant, or `409 Conflict` for a duplicate. |
| `POST /restaurants/reindex` | `mission-admin` | Queue all current restaurants for reindexing in bounded PostgreSQL transactions; returns `202 Accepted` and the number queued. |

### Restaurant request and response

Create and update accept these fields:

| Field | Required | Constraints |
| --- | --- | --- |
| `name` | Yes | Non-blank; up to 160 characters. |
| `description` | No | Up to 2,000 characters. |
| `address` | Yes | Non-blank; up to 255 characters. |
| `city` | Yes | Non-blank; up to 120 characters. |
| `cuisine` | Yes | Non-blank; up to 100 characters. |
| `latitude` | Yes | From -90 to 90. |
| `longitude` | Yes | From -180 to 180. |

The response includes `id`, all restaurant fields, `averageRating`, and
`reviewCount`. A new restaurant starts with an average rating and review count
of zero.

Example request:

```json
{
  "name": "Example Bistro",
  "description": "Seasonal neighborhood cooking",
  "address": "10 Main Street",
  "city": "Example City",
  "cuisine": "Italian",
  "latitude": 40.7128,
  "longitude": -74.006
}
```

### Search

`GET /restaurants/search` accepts:

| Query parameter | Constraints and meaning |
| --- | --- |
| `q` | Optional text, up to 100 characters; searches name, cuisine, description, address, and city. Name and cuisine have higher relevance weights. |
| `city` | Optional exact city filter, up to 120 characters; comparison is case-insensitive. |
| `cuisine` | Optional exact cuisine filter, up to 100 characters; comparison is case-insensitive. |
| `latitude` | Optional center latitude from -90 to 90. |
| `longitude` | Optional center longitude from -180 to 180. |
| `radiusKm` | Optional radius from 0.1 to 1,000 kilometers. |
| `page` | Zero-based page, default `0`, range 0-99. |
| `size` | Page size, default `20`, range 1-100. |

At least one of `q`, `city`, `cuisine`, or the complete geographic tuple
(`latitude`, `longitude`, `radiusKm`) is required. Geographic parameters must
be supplied together. Invalid combinations return `400 Bad Request`.

Geographic searches sort by distance from the supplied point, then restaurant
ID. Other searches sort by text relevance, then restaurant ID. Search returns
`restaurants`, `page`, `size`, and an exact `total`; exact totals require
Elasticsearch to count every matching document.

Example:

```text
GET /restaurants/search?q=pizza&city=Example%20City&page=0&size=20
GET /restaurants/search?latitude=40.7128&longitude=-74.006&radiusKm=10
```

### Reviews

Review creation accepts:

| Field | Required | Constraints |
| --- | --- | --- |
| `rating` | Yes | Integer from 1 to 5. |
| `comment` | No | Up to 2,000 characters. |

The authenticated OIDC principal name identifies the reviewer. The database
enforces one review per restaurant and reviewer, and the restaurant row is
locked while duplicate checks, review creation, aggregate updates, and outbox
insertion run in one transaction.

Review listing accepts `page` (default `0`, range 0-1,000,000) and `size`
(default `20`, range 1-100), and returns `reviews`, `page`, `size`, and `total`.
Reviews are ordered newest first, with review ID as the stable tie-breaker.
Each review response includes `id`, `restaurantId`, `rating`, `comment`, and
`createdAt`.

## PostgreSQL data model

Flyway migration V6 creates the restaurant, review, and outbox tables. Migration
V7 adds persisted review aggregates, backfills existing restaurants from their
reviews, and removes the old outbox snapshot columns. Applied migrations must
not be edited; use a new sequential migration for future schema changes.

### `restaurant`

Stores the restaurant's editable fields, coordinates, `average_rating`, and
`review_count`. Database checks constrain latitude and longitude. The aggregate
columns avoid recalculating review averages and counts for each restaurant
read.

### `restaurant_review`

Stores the restaurant ID, OIDC reviewer subject, rating, optional comment, and
creation timestamp. A unique constraint on `(restaurant_id, reviewer_subject)`
enforces the one-review rule. Deleting a restaurant cascades to its reviews.
The restaurant/timestamp index supports paged review listing.

### `restaurant_search_outbox`

Stores an ID and restaurant ID for each change that must be reflected in
Elasticsearch. Events do not contain a document snapshot: the worker reads the
restaurant's current state when it processes the event. This makes older or
coalesced events safe even when generated sequence IDs do not correspond to
commit order.

### Review aggregates

On review creation, the service updates the average incrementally using the
previous average and count, and increments the count in the same transaction as
the review and outbox event. The per-restaurant write lock serializes reviews
for that restaurant. Restaurant deletion removes its aggregate row and cascades
to its reviews.

## Elasticsearch index

The index is named `restaurants` and is created lazily by the outbox worker.
Its mapping contains:

- `id` as a long document identifier.
- `name`, `description`, and `address` as searchable text.
- `city` and `cuisine` as searchable text plus normalized lowercase `keyword`
  fields used by exact filters.
- `latitude`, `longitude`, `averageRating`, and `reviewCount` as numeric fields.
- `location` as a `geo_point` for radius filtering and distance sorting.

Elasticsearch stores a derived restaurant document, but API result details are
loaded from PostgreSQL. Do not use Elasticsearch as the authoritative record.

## Configuration and local development

The local Compose service runs Elasticsearch 8.17.4 on port 9200 with security
disabled and a 512 MB heap. This setup is for local development only:

```shell
docker compose up -d elasticsearch
./mvnw quarkus:dev
```

The development application points to `http://localhost:9200`. Production
configuration uses:

| Environment variable | Purpose |
| --- | --- |
| `ELASTICSEARCH_URL` | Elasticsearch base URL. |
| `ELASTICSEARCH_API_KEY` | API key sent as `Authorization: ApiKey ...` when configured. |
| `QUARKUS_DATASOURCE_JDBC_URL` | Application PostgreSQL JDBC URL. |
| `QUARKUS_DATASOURCE_USERNAME` | Application PostgreSQL user. |
| `QUARKUS_DATASOURCE_PASSWORD` | Application PostgreSQL password. |

Keep production Elasticsearch protected and use TLS where appropriate. Never
reuse the local no-auth settings in production. The PostgreSQL service in the
repository's Compose file is for Keycloak; it is not the application's
restaurant database.

The Bruno collection has a `Restaurants` folder with ready-to-run requests.
Use the `Dev` environment and its `{{baseUrl}}`. Restaurant mutations and
reindexing use the collection's `mission-admin` authentication; review creation
uses an authenticated token. Create a restaurant first to populate
`restaurantId`, then create a review to populate `reviewId`. Reindexing queues
every current restaurant before returning; the outbox worker delivers the
changes to Elasticsearch asynchronously afterward.

## Testing

Restaurant integration tests start a real Elasticsearch 8.17.4 container with
Testcontainers. Docker must be available for `./mvnw test`; the first run
downloads the Elasticsearch image. The tests cover text/geo search, exact
totals above 10,000, outbox delivery and stale events, deletion, review
authorization and uniqueness, validation, aggregate updates, and paged
reindexing.

Run the focused tests or the full suite:

```shell
./mvnw -Dtest=RestaurantResourceTest test
./mvnw test
```
