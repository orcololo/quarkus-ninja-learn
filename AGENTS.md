# Repository guidance

## Architecture and implementation

- Keep feature code grouped by domain under `src/main/java/org/acme/<domain>/`.
- Follow the existing flow: JAX-RS `*Resource` for HTTP, `*Service` for business rules and transactions, Panache `*Repository` for persistence, and request/response DTOs with a mapper between DTOs and entities. Keep the design smaller when a layer is not needed; do not expose persistence entities as API responses.
- Validate request DTOs at the HTTP boundary with Jakarta Bean Validation and `@Valid`. Preserve clear HTTP status codes and explicit not-found behavior.
- Put authorization on protected endpoints using the established Quarkus OIDC role pattern. Test both allowed and denied/anonymous access when adding or changing protected behavior.
- Put schema changes in a new, sequential Flyway migration under `src/main/resources/db/migration/`. Never edit a migration that may already have run; keep entity mappings consistent with the migration and the ORM schema-validation setting.
- Use transactions for writes and keep related state changes atomic. Preserve database constraints that enforce uniqueness and other domain invariants.
- Reuse project dependencies and conventions; do not add a dependency or abstraction without a concrete need.

## Every feature

- Add or update focused tests under `src/test/java/` for changed behavior. Follow the existing Quarkus/RestAssured resource-test pattern for HTTP behavior and cover validation, not-found, authorization, and important state transitions where applicable.
- For every feature that adds or changes an HTTP operation or its request/response contract, add or update the corresponding Bruno request(s) in `bruno/Quarkus App/`. Include each affected method and path, a representative valid request body when needed, and the appropriate inherited authentication. Keep requests usable with the `Dev` environment and its `{{baseUrl}}` variable; use descriptive request names and maintain unique `info.seq` values. Do not leave placeholder requests.
- Keep tests, migrations, and Bruno examples in sync with the behavior and API contract being changed. A feature with no HTTP surface does not need a Bruno request.

## Validation

- Run the relevant tests after implementation; use `./mvnw test` for the project test suite when a focused test command is not available.
- For API changes, verify that the corresponding Bruno requests still represent the implemented contract.
