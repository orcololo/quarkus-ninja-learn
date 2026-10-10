# Local API collection

Start Keycloak and the Quarkus application as described in the project README,
then select the `Local` environment. Run `Get Admin Token` and/or `Get Ninja
Token` once; their post-response scripts save `access_token` into the matching
variable, which the protected requests use automatically. No token copy/paste
is needed. Bruno keeps script-set values for the current session; after
restarting Bruno or when a token expires, run the matching token request again.
These sample logins are for local development only.

Requests are grouped into `Authentication`, `Users`, `Ninjas`, `Villages`,
`Missions`, and `Restaurants` folders. The environment files remain at the
collection root.

`mission-admin` is required for `Create Ninja` and all village changes.
Village and Ninja read endpoints are public. Use `ninjaToken` for the points
request; it may only read the authenticated account's Ninja points. The points
response contains the full `balance` and a page of `transactions`; `page`
starts at 0 and `size` is limited to 100.

For the mission workflow, use `Get Admin Token` before creating/updating or
activating a mission, assigning it, listing submissions, or approving/rejecting
it. Use `Get Ninja Token` to inspect or submit the assigned mission. Set
`missionId`, `ninjaId`, and `assignmentId` in the `Local` environment from the
IDs returned by the preceding requests. `Get Ninja Points` shows the balance
after approval.

To create a game account, set unique `accountUsername` and `accountEmail`
values and an `accountPassword` of at least 12 characters in the `Local`
environment, then send `Register Account`. Registration creates a BASIC
account in the API database and a corresponding Keycloak identity. Sign in
with the same username and password using `Get Account Token`; the request
stores the token as `accountToken` automatically. Use that token for `Get My
Account` and `Create Owned Ninja`. BASIC accounts can own one Ninja and
PREMIUM accounts can own two. `mission-admin` can list accounts, change
account levels, and link or unlink existing Ninjas.

If registration returns HTTP 502, Keycloak did not accept the provisioning
client credentials or permissions. For a realm that already existed before
the updated import, configure the confidential `ninja-provisioner` client
with service accounts enabled, secret `local-provisioner-secret` for local
development, and the `manage-users` and `view-realm` roles from `realm-management`.
Updating the import JSON does not change an existing realm.

Restaurant management and reindexing require `mission-admin`. Search and
restaurant/review reads are public; creating a review requires an authenticated
identity. Run `Create Restaurant` first to save its returned ID as
`restaurantId`. Restaurant indexing is asynchronous, so search may briefly lag
after writes. Run `Get Ninja Token` before `Create Review`; the create request
saves its returned ID as `reviewId` for `Get Review`.
