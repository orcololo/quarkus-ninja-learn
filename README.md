# db-service

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

Start the local Keycloak and Elasticsearch services:

```shell
docker compose up -d
```

The Compose setup imports the `ninja` realm at
`http://localhost:8180/realms/ninja`. The Keycloak administration console is
at <http://localhost:8180> (`admin` / `admin`). For local API testing, the
realm includes a Ninja user (`1` / `ninja123`) and an administrator
(`mission-admin` / `admin123`). Create the first Ninja through the API after
starting the app; the development database starts its Ninja IDs at `1`, which
matches the demo user's OIDC subject. These credentials are for local
development only. Keycloak stores its data in a separate PostgreSQL database
and the `keycloak_db_data` volume, independent of the application's database.
`docker compose down` preserves this data; `docker compose down -v` deletes it.
The realm file is imported only when the realm does not already exist, so
changing it after the first startup does not update the persisted realm.
Elasticsearch is available at `http://localhost:9200`. The local Compose
configuration disables Elasticsearch security and is for development only.

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

Flyway applies versioned SQL migrations from `src/main/resources/db/migration`.
Development and test profiles clean and recreate their schema at startup; do not
use those profiles or the clean setting with persistent data.

For an existing database created before Flyway was introduced, verify that its
schema matches migration V1 and baseline it at version 1 before starting the
application. New databases run V1 and subsequent migrations automatically.

## Missions and points

Villages support create/list/get/update/delete at `/villages`. Set or clear a
leader with `PUT` or `DELETE /villages/{id}/leader`; add/move or remove a Ninja
with `PUT` or `DELETE /villages/{id}/ninjas/{ninjaId}`, and list members at
`GET /villages/{id}/ninjas`. A village leader is also a member and counts
toward population. Clearing the leader does not remove them from the village;
deleting a village detaches its Ninjas without deleting their records.

Missions are administered through `POST/GET /missions`, `GET/PUT /missions/{id}`,
and `POST /missions/{id}/activate` or `/deactivate`. An administrator assigns an
active mission with `POST /ninjas/{ninjaId}/missions/{missionId}`. The Ninja
submits with `POST /ninja-missions/{id}/submit`; an administrator can list
submissions at `GET /ninja-missions/submitted`, then approve or reject them with
`POST /ninja-missions/{id}/approve` or `/reject`. A Ninja can inspect their
balance and paged point history at `GET /ninjas/{ninjaId}/points?page=0&size=20`.
The endpoint returns the full balance, current page, page size, and total
transaction count; size is limited to 100.

Mission assignment is non-repeatable for the same Ninja. Approval creates a
single point transaction in the same database transaction as the status change;
repeating approval returns the existing transaction without awarding points
again. A rejected assignment cannot be approved.

Protected endpoints use OIDC. In production, configure
`QUARKUS_OIDC_AUTH_SERVER_URL` with the issuer URL; this is the environment-variable
form of Quarkus's `quarkus.oidc.auth-server-url` property. Quarkus Dev Services
provides a local identity provider during tests. Tokens must include a
`groups` claim containing `ninja` and/or `mission-admin`; a Ninja token's `sub`
identifies the Keycloak identity linked to the game account that owns the Ninja.
Mission creation/editing, assignment, and approval/rejection require
`mission-admin`; submission and viewing one's own missions/points require
`ninja`. Creating a Ninja through `POST /ninjas` and all village changes require
`mission-admin`; account owners can create Ninjas through
`POST /users/me/ninjas`. Listing and reading ninjas/villages remain public.

## Game accounts

`POST /users/register` provisions the username, email, and password in Keycloak
and creates a BASIC game account. Passwords are stored only by Keycloak. An
authenticated account can use `GET /users/me` and `POST /users/me/ninjas`;
BASIC accounts may own one Ninja, PREMIUM accounts may own two, and ADMIN
accounts have no account-level limit. Only `mission-admin` can list accounts,
change levels, or link/unlink existing Ninjas. `GET /users?page=0&size=20` returns a page of accounts, including
`page`, `size`, and `total`; the size is limited to 100. Existing Ninjas are backfilled
to synthetic legacy accounts by migration V5 and must be linked by an
administrator to a real account before that account can submit missions or
read their points.

The Keycloak import does not update a realm that already exists. For an
existing local realm, create a confidential `ninja-provisioner` client with
service accounts enabled and the configured client secret (`local-provisioner-secret`
in dev). Grant its service account the `manage-users` and `view-realm` client
roles from `realm-management` before registering accounts. Do not delete the
Keycloak database volume unless its stored users can be discarded.

Production must also configure the application's PostgreSQL database with
`QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, and
`QUARKUS_DATASOURCE_PASSWORD`, plus `KEYCLOAK_ADMIN_URL` and
`KEYCLOAK_PROVISIONER_CLIENT_SECRET` for the Keycloak service account with
permission to manage users. The PostgreSQL service in Compose is reserved for
Keycloak and is not the application's database.

## Restaurant locator

Restaurants and reviews are stored in PostgreSQL. Restaurant create, update,
delete, and review writes also add a search-indexing event in the same database
transaction. A scheduled backend worker sends events to Elasticsearch in bulk,
without holding a database transaction during network calls; failed batches
remain queued for retry. Reindexing queues restaurants in bounded database
pages. Search criteria, including coordinates and radius in kilometers, are
sent to Elasticsearch by the backend; results are then loaded from PostgreSQL,
which remains the source of truth. Restaurant search keeps exact result totals,
so broad searches still require Elasticsearch to count every match.

For the full API, data model, indexing/retry behavior, configuration, and test
guide, see the [restaurant locator documentation](docs/restaurant-locator.md).

Start Elasticsearch with `docker compose up -d elasticsearch` and configure
`ELASTICSEARCH_URL` and `ELASTICSEARCH_API_KEY` in production. Restaurant
management and reindexing require `mission-admin`; search, restaurant reads, and
review reads are public. Any authenticated OIDC identity can submit one review
per restaurant. Use `POST /restaurants/reindex` to queue current restaurants
for indexing again.

The restaurant integration tests start Elasticsearch 8.17.4 with Testcontainers;
Docker must be available when running `./mvnw test`. The first run downloads the
Elasticsearch image.

The API supports `POST /restaurants`, `GET/PUT/DELETE /restaurants/{id}`,
and `GET /restaurants/search?q=...&city=...&cuisine=...&latitude=...&longitude=...&radiusKm=...`.
The search page uses `page` and `size` (maximum size 100). Create reviews with
`POST /restaurants/{id}/reviews` using a 1-5 `rating` and optional `comment`;
read reviews with `GET /restaurants/{id}/reviews`.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/db-service-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): Build RESTful web services and APIs using Jakarta REST (formerly JAX-RS)
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplified JPA/Hibernate data access layer with active record and repository patterns
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC

## Provided Code

### Hibernate ORM

Create your first JPA entity

[Related guide section...](https://quarkus.io/guides/hibernate-orm)


[Related Hibernate with Panache section...](https://quarkus.io/guides/hibernate-orm-panache)


### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
