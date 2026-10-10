# Restaurant locator

This document describes the restaurant and review API, its PostgreSQL and
Elasticsearch data model, indexing lifecycle, configuration, and tests.

## Architecture and consistency

PostgreSQL is the source of truth for restaurants, reviews, and review
aggregates. Elasticsearch is a derived index used for text, city, cuisine, and
geographic search. The application sends all search criteria to Elasticsearch;
it does not run geographic search in PostgreSQL.

Restaurant create, update, delete, review, and menu item mutations write
PostgreSQL state and a typed outbox row in the same transaction. After commit,
the scheduled worker delivers pending outbox changes to Elasticsearch. A
successful write response does not wait for indexing, so search can briefly
return stale results.

`POST /restaurants/reindex` creates a durable reindex job and returns
immediately with `202 Accepted`, a job `id`, and a `Location` header. The worker
scans up to 500 restaurants and 500 menu items per run, adds their IDs to the
outbox in a short transaction, and updates the job counters. Poll
`GET /restaurants/reindex/{jobId}` to check progress and whether the scan is
complete. Queued events may still be waiting for Elasticsearch delivery after
the scan completes.

The worker runs every two seconds and processes up to ten batches of 100
outbox events per run. For each batch it:

1. Reads the events and the current PostgreSQL rows for their restaurant or
   menu item IDs in a short transaction.
2. Coalesces events by entity type and ID, then builds separate bulk requests
   for restaurant and menu-item indexes, indexing the latest PostgreSQL row or
   deleting its document if that entity no longer exists.
3. Sends each required bulk request without a database transaction.
4. Deletes the selected outbox rows in a new transaction only after the bulk
   response confirms that each item succeeded. Deleting a missing Elasticsearch
   document is considered successful.

A PostgreSQL session advisory lock on a dedicated borrowed connection prevents
multiple application instances from delivering the same batch concurrently.
The lock is released in a `finally` block and also when PostgreSQL closes that
connection. If delivery fails or the process stops before acknowledgement, the
events remain queued. A batch may be delivered again if Elasticsearch accepted
it but the process stopped before acknowledgement; indexing uses restaurant IDs
as document IDs, making these retries idempotent. Deleting a restaurant queues
deletions for its menu item documents before PostgreSQL cascades their rows.

The worker's 100-event limit also bounds the number of distinct operations per
index in a bulk request. It retains failed batches for retry rather than
acknowledging individual successful items from a partially failed bulk request.
Retries happen on a later scheduled run; there is no dead-letter queue or
automatic event deletion after repeated failures. If Elasticsearch is
unavailable, restaurant writes can still commit because the outbox is in
PostgreSQL, while restaurant search returns `503 Service Unavailable`.

Restaurant and menu item search first gets matching document IDs, order, and
exact total from Elasticsearch, then loads the matching PostgreSQL rows.
Restaurant search returns current restaurant fields, while menu search returns
current item fields and attaches the current restaurant response. While an index
is catching up, a result page can omit a deleted entity or have a total that
briefly differs from the number of returned PostgreSQL rows.

## HTTP API

All routes are under `/restaurants`. JSON request bodies are validated at the
HTTP boundary.

| Method and path | Access | Behavior |
| --- | --- | --- |
| `POST /restaurants` | `mission-admin` | Create a restaurant; returns `201 Created` and its representation. |
| `GET /restaurants/{id}` | Public | Get a restaurant; returns `404` if absent. |
| `PUT /restaurants/{id}` | `mission-admin` | Replace restaurant fields; returns `404` if absent. |
| `DELETE /restaurants/{id}` | Owner or `mission-admin` | Delete the restaurant and its reviews; returns `204 No Content`, `403` if owned by another identity, or `404` if absent. |
| `GET /restaurants/search` | Public | Search and page restaurants using Elasticsearch. |
| `GET /restaurants/{id}/reviews` | Public | List a restaurant's paged reviews; returns `404` if the restaurant is absent. |
| `GET /restaurants/{id}/reviews/{reviewId}` | Public | Get one review; returns `404` if either record is absent or the review belongs to another restaurant. |
| `POST /restaurants/{id}/reviews` | Authenticated OIDC identity | Create one review per identity per restaurant; returns `201 Created`, `404` for an absent restaurant, or `409 Conflict` for a duplicate. |
| `GET /restaurants/{id}/menu-items` | Public | Page the restaurant's menu items in category/name order. |
| `POST /restaurants/{id}/menu-items` | `mission-admin` | Add an item; returns `201 Created` and the item representation. |
| `GET /restaurants/{id}/menu-items/{itemId}` | Public | Get an item belonging to the restaurant; returns `404` if either ID does not match. |
| `PUT /restaurants/{id}/menu-items/{itemId}` | `mission-admin` | Replace an item's fields. |
| `DELETE /restaurants/{id}/menu-items/{itemId}` | Owner or `mission-admin` | Delete an item; returns `204 No Content`, `403` if owned by another identity, or `404` if absent. |
| `GET /restaurants/menu-items/search` | Public | Search items across restaurants using Elasticsearch; results include current item and restaurant data from PostgreSQL. |
| `POST /restaurants/reindex` | `mission-admin` | Start a durable asynchronous reindex job; returns `202 Accepted` with the job ID. |
| `GET /restaurants/reindex/{jobId}` | `mission-admin` | Get scan progress and whether the job has queued all current rows. |

### Restaurant request and response

Create and update accept these fields:

| Field | Required | Constraints |
| --- | --- | --- |
| `name` | Yes | Non-blank; up to 160 characters. |
| `description` | No | Up to 2,000 characters. |
| `address` | Yes | Non-blank; up to 255 characters. |
| `city` | Yes | Non-blank; up to 120 characters. |
| `cuisine` | Yes | One of the `CuisineType` values below. |
| `latitude` | Yes | From -90 to 90. |
| `longitude` | Yes | From -180 to 180. |
| `openingHours` | No | Weekly schedule with an IANA timezone; omitted on update to clear the schedule. |

Cuisine values are `AFRICAN`, `AMERICAN`, `ARGENTINIAN`, `BRAZILIAN`,
`CARIBBEAN`, `CHINESE`, `ETHIOPIAN`, `FILIPINO`, `FRENCH`, `GREEK`, `INDIAN`,
`INDONESIAN`, `ITALIAN`, `JAPANESE`, `KOREAN`, `MEDITERRANEAN`, `MEXICAN`,
`MIDDLE_EASTERN`, `PERUVIAN`, `SPANISH`, `THAI`, `TURKISH`, `VIETNAMESE`, and
`OTHER`. The migration maps unrecognized stored cuisine strings to `OTHER`.

Restaurant and menu-item ownership is recorded from the authenticated OIDC
subject that creates the resource. An authenticated owner can delete their
resource; `mission-admin` can also delete resources owned by another identity
and legacy rows without an owner. Creation and updates remain restricted to
`mission-admin`.

The response includes `id`, all restaurant fields, `averageRating`, and
`reviewCount`, plus `openingHours` when the restaurant has a schedule. A new
restaurant starts with an average rating and review count of zero.

Example request:

```json
{
  "name": "Example Bistro",
  "description": "Seasonal neighborhood cooking",
  "address": "10 Main Street",
  "city": "Example City",
  "cuisine": "ITALIAN",
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
| `cuisine` | Optional exact filter using a `CuisineType` value; comparison is case-insensitive. |
| `latitude` | Optional center latitude from -90 to 90. |
| `longitude` | Optional center longitude from -180 to 180. |
| `radiusKm` | Optional radius from 0.1 to 1,000 kilometers. |
| `openNow` | Optional boolean. `true` finds restaurants currently open; `false` finds restaurants with known schedules that are currently closed. Restaurants without a schedule are excluded. |
| `page` | Zero-based page, default `0`, range 0-99. |
| `size` | Page size, default `20`, range 1-100. |

At least one of `q`, `city`, `cuisine`, `openNow`, or the complete geographic
tuple (`latitude`, `longitude`, `radiusKm`) is required. Geographic parameters
must be supplied together. Invalid combinations return `400 Bad Request`.

Geographic searches sort by distance from the supplied point, then restaurant
ID. Other searches sort by text relevance, then restaurant ID. Search returns
`restaurants`, `page`, `size`, and an exact `total`; exact totals require
Elasticsearch to count every matching document.

Restaurants may have an optional `openingHours` object with an IANA `timeZone`
and up to 56 weekly periods. Each period has a `dayOfWeek`, `opensAt`, and
`closesAt` in `HH:mm` format. Multiple periods per day are supported; periods
must not overlap. When the close time is earlier than the open time, the period
continues after midnight and belongs to its start day. The schedule is evaluated
in each restaurant's timezone, so daylight-saving transitions use the timezone's
rules. Schedules do not include holiday exceptions. Omitting `openingHours`
means the schedule is unknown; those restaurants do not match `openNow=true`.
On update, `openingHours` is replaced along with the other restaurant fields;
omitting it clears the existing schedule.

Example:

```text
GET /restaurants/search?q=pizza&city=Example%20City&page=0&size=20
GET /restaurants/search?latitude=40.7128&longitude=-74.006&radiusKm=10
GET /restaurants/search?city=Example%20City&openNow=true
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

### Menu items

Menu items belong directly to a restaurant. Listing is public and returns the
items ordered by category, name, and ID; the `available` flag is included so
clients can display unavailable items without removing them from the menu.
The listing accepts `page` (default `0`, range 0-1,000,000) and `size` (default
`20`, range 1-100), and returns `items`, `page`, `size`, and `total`.
Restaurant administrators can create, replace, and delete items. Menu mutations
queue updates for the separate menu item search index.

Create and update use the same JSON fields:

| Field | Required | Constraints |
| --- | --- | --- |
| `name` | Yes | Non-blank; up to 160 characters. |
| `description` | No | Up to 2,000 characters. |
| `category` | No | Up to 100 characters. |
| `price` | Yes | Non-negative decimal; up to 9 integer digits and 3 fractional digits. |
| `currency` | Yes | Uppercase, supported ISO 4217 currency code, such as `USD` or `CAD`. |
| `available` | Yes | Boolean. |

Prices are stored per item with their original currency. Menu search can add an
exchange-rate display price when a target currency is requested, but never
rewrites stored prices. The system does not calculate taxes or process
orders/payments.

Example:

```json
{
  "name": "Shoyu Ramen",
  "description": "Pork broth ramen with noodles",
  "category": "Mains",
  "price": 14.5,
  "currency": "USD",
  "available": true
}
```

### Menu item search

`GET /restaurants/menu-items/search` searches indexed item names,
descriptions, and categories across all restaurants. It accepts:

| Query parameter | Constraints and meaning |
| --- | --- |
| `q` | Optional text, up to 100 characters. |
| `category` | Optional exact category filter, up to 100 characters; comparison is case-insensitive. |
| `available` | Optional boolean filter, `true` or `false`. |
| `currency` | Optional ISO 4217 target currency, e.g. `BRL`; adds a converted `displayPrice` to each result. |
| `page` | Zero-based page, default `0`, range 0-99. |
| `size` | Page size, default `20`, range 1-100. |

At least one of `q`, `category`, or `available` is required. The response
contains `results`, `page`, `size`, and exact `total`. Each result contains an
`item` and its full current `restaurant` response. Elasticsearch supplies match
IDs and ordering; item and restaurant fields are batch-loaded from PostgreSQL.
Restaurant metadata is current even if an index result is briefly stale.

When `currency` is provided, each result retains the menu item's original
`item.price` and `item.currency` and adds `displayPrice.amount`,
`displayPrice.currency`, and `displayPrice.rateDate`. The API fetches USD-base
rates daily from the [fawazahmed0 exchange API](https://github.com/fawazahmed0/exchange-api),
uses its Cloudflare fallback, and stores the last successful snapshot in
PostgreSQL. Conversion is derived from the USD-base rates, rounded half up to
the target currency's standard fraction digits, and never changes stored menu
prices. If no snapshot is available, a request with `currency` returns `503`;
without the parameter, search does not depend on exchange rates.

Example:

```text
GET /restaurants/menu-items/search?q=ramen&category=Mains&available=true&page=0&size=20
GET /restaurants/menu-items/search?q=ramen&currency=BRL
```

## PostgreSQL data model

Flyway migration V6 creates the restaurant, review, and outbox tables. Migration
V7 adds persisted review aggregates, backfills existing restaurants from their
reviews, and removes the old outbox snapshot columns. Migration V8 creates the
restaurant menu item table and its ordered-list index. Migration V9 changes
outbox rows to typed aggregate events for restaurant and menu item indexing.
Migration V10 adds durable reindex job progress. Migration V11 indexes
submitted mission listings for paged admin access.
Migration V14 stores the latest daily exchange-rate snapshot used for display
price conversion.
Applied migrations must not be edited; use a new sequential migration for future
schema changes.

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

Stores an ID, aggregate type (`RESTAURANT` or `MENU_ITEM`), and aggregate ID for
each change reflected in Elasticsearch. Events do not contain a document
snapshot: the worker reads the entity's current state when it processes the
event. This makes older or coalesced events safe even when generated sequence
IDs do not correspond to commit order.

### `restaurant_menu_item`

Stores name, optional description and category, non-negative price, ISO currency
code, and availability. A foreign key cascades item deletion when its restaurant
is deleted. The `(restaurant_id, category, name, id)` index supports stable
menu listing order.

### `restaurant_exchange_rate_snapshot`

Stores the current provider publication date, fetch timestamp, and USD-base rate
map as a single replaceable snapshot. The fixed row ID prevents multiple active
snapshots; menu prices remain stored in each item's original currency.

### Review aggregates

On review creation, the service updates the average incrementally using the
previous average and count, and increments the count in the same transaction as
the review and outbox event. The per-restaurant write lock serializes reviews
for that restaurant. Restaurant deletion removes its aggregate row and cascades
to its reviews.

## Elasticsearch index

The restaurant index is named `restaurants`; the menu index is
`restaurant-menu-items`. Both are created lazily by the outbox worker.

The `restaurants` mapping contains:

- `id` as a long document identifier.
- `name`, `description`, and `address` as searchable text.
- `city` and `cuisine` as searchable text plus normalized lowercase `keyword`
  fields used by exact filters.
- `latitude`, `longitude`, `averageRating`, and `reviewCount` as numeric fields.
- `location` as a `geo_point` for radius filtering and distance sorting.

The `restaurant-menu-items` mapping contains menu item ID, restaurant ID,
searchable name/description/category, decimal price, currency, and availability.
Category has a normalized keyword field for exact case-insensitive filtering.
The menu index provides search matches only; result data is loaded from
PostgreSQL.

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
`restaurantId`, then create a review to populate `reviewId` or a menu item to
populate `menuItemId`. Menu reads are public; menu mutations use the inherited
`mission-admin` authentication. Reindexing returns a job ID immediately. Poll
`Get Reindex Status` to view progress; the background worker scans and queues
bounded pages before delivering outbox events to Elasticsearch.

## Testing

Restaurant integration tests start a real Elasticsearch 8.17.4 container with
Testcontainers. Docker must be available for `./mvnw test`; the first run
downloads the Elasticsearch image. The tests cover text/geo search, exact
totals above 10,000, menu item search with attached restaurant metadata, outbox
delivery and stale events, restaurant/menu item deletion and asynchronous
reindex job progress, review authorization and uniqueness, menu CRUD and access
control, validation, and aggregate updates.

Run the focused tests or the full suite:

```shell
./mvnw -Dtest=RestaurantResourceTest test
./mvnw test
```
