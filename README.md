# SplitApp

A REST API for splitting shared expenses within a group: record who paid for what, track who
owes whom, record payments, and get suggested transfers that settle everything.

Built with Java 17, Spring Boot 4.1, PostgreSQL 16 and Flyway. The interesting parts are
data integrity (constraints and locking in the database, not only in the code) and tests that
run against a real PostgreSQL, including deterministic tests for race conditions.

## Features

- Register and authenticate with email and password (HTTP Basic).
- Create groups; the creator is the owner. The owner invites people by email, an invited
  user accepts or declines.
- Record expenses (who paid, who shares it), split equally or by percentages. Every split
  adds up to the exact total.
- Record payments between members. A payment is pending until the receiver confirms it.
- See every member's balance and suggested transfers that bring all balances to zero.
- The owner can hand the group over to another member. A member can leave once settled up.

## Running it

### With Docker

```
cp .env.example .env
```

Set `DB_PASSWORD` in `.env`, then:

```
docker compose up --build
```

The API is on http://localhost:8080 and Swagger UI on http://localhost:8080/swagger-ui.html
(use "Authorize" with the email and password of a registered user). The database is not
published to the host, so it cannot clash with a PostgreSQL you already run.
`docker compose down -v` also removes its data.

### Without Docker

You need Java 17 and a PostgreSQL 16 database. Put the connection in `.env` (see
`.env.example`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), then run `./mvnw spring-boot:run`.
Flyway creates the schema on start.

### Tests

```
./mvnw test
```

Docker has to be running: the tests start PostgreSQL 16.15 with Testcontainers.

## Trying it

```
curl -X POST localhost:8080/users -H 'Content-Type: application/json' \
  -d '{"firstName":"Jan","lastName":"Kowalski","email":"jan@example.com","password":"secret123"}'

curl -u jan@example.com:secret123 -X POST localhost:8080/groups \
  -H 'Content-Type: application/json' -d '{"groupName":"Trip"}'

curl -u jan@example.com:secret123 localhost:8080/groups
```

Every `POST` must be sent with `Content-Type: application/json`, otherwise the answer is 415.

## API

"Member" means a member of the group in the path. Every endpoint except `POST /users`
needs HTTP Basic credentials.

| Method and path | Who | What |
|---|---|---|
| `POST /users` | anyone | Register |
| `GET /users/me` | user | The current user |
| `POST /groups` | user | Create a group (the creator becomes its owner) |
| `GET /groups` | user | The user's groups |
| `GET /groups/{groupId}` | member | One group with its members |
| `POST /groups/{groupId}/invitations` | owner | Invite someone by email |
| `GET /groups/{groupId}/invitations` | owner | The group's invitations |
| `DELETE /groups/{groupId}/invitations/{invitationId}` | owner | Cancel an invitation |
| `GET /invitations` | user | The user's pending invitations |
| `POST /invitations/{invitationId}/accept` | invited user | Accept and join the group |
| `POST /invitations/{invitationId}/decline` | invited user | Decline |
| `POST /groups/{groupId}/expenses` | member | Record an expense |
| `GET /groups/{groupId}/expenses` | member | List expenses, newest first |
| `DELETE /groups/{groupId}/expenses/{expenseId}` | author or owner | Delete an expense |
| `POST /groups/{groupId}/payments` | sender or receiver | Record a payment between two members (the caller is one of them) |
| `GET /groups/{groupId}/payments` | member | List payments, newest first |
| `POST /groups/{groupId}/payments/{paymentId}/confirm` | receiver | Confirm a payment |
| `DELETE /groups/{groupId}/payments/{paymentId}` | author, owner or receiver of a pending payment | Delete a payment |
| `GET /groups/{groupId}/balances` | member | Balances and suggested settlement transfers |
| `POST /groups/{groupId}/owner` | owner | Transfer ownership to another member |
| `POST /groups/{groupId}/leave` | member | Leave the group (not the owner, not with a debt) |

The full description (request and response schemas) is in Swagger UI, generated from the
code.

## Design decisions

**Money.** Amounts are `numeric(10,2)` in the database and are computed in cents as `long`,
so there is no floating point and no rounding drift. When a split does not divide evenly,
the remaining cents are handed out one by one (to the first participants for an equal split,
to the largest fractional parts for a percentage split), so the parts always add up to the
total.

**Rules live in the database too.** Besides the checks in the code, the schema enforces them:
`CHECK` constraints on amounts and enum values, a payment cannot go from a user to
themselves, one pending invitation per user and group, lower-case unique emails, and exactly
one owner per group (a partial unique index). Hibernate runs with `ddl-auto=validate`, so
the entities must match the Flyway migrations (V1 to V12).

**Locking.** Every operation that changes a group (membership, ownership, expenses, payments,
invitations) takes a pessimistic write lock on the group row (`SELECT ... FOR UPDATE`) after
checking membership, and checks membership again once it holds the lock. Such operations on
one group run one after another. I chose a lock over `@Version` because these operations are
short, conflicts are expected on a busy group, and several rules span more than one entity
(for example "a member who is leaving must not be charged at the same time"). With a version
check one of two concurrent requests would be rejected and the client would have to retry.
A `lock_timeout` of 5 seconds turns a stuck wait into a 409 instead of tying up a connection.
Changing ownership demotes the old owner and flushes before promoting the new one, so the
database never sees two owners.

**A member who left keeps a frozen balance.** Leaving requires a zero balance and no pending
payments, and entries that involve a former member cannot be deleted. So the history of
someone who left stays consistent.

**Errors.** Every error is an `application/problem+json` (RFC 9457), including the ones
produced before Spring MVC (401, 415). Database errors are mapped by SQLState and never
expose the conflicting values. Convention: `409` is a conflict with current data or a
concurrent change (retrying may help), `422` is a business rule that the request breaks,
`400` is a malformed or invalid request. Messages are always in English
(`spring.web.locale-resolver=fixed`).

**CSRF is off, deliberately.** The API is stateless and uses Basic auth, which browsers cache
and resend, so a cross-site form post would be authenticated. It is stopped by a filter that
requires `Content-Type: application/json` on every `POST`, which an HTML form cannot send.
Do not add permissive CORS or endpoints that accept form data.

**Passwords.** Hashes are stored as `{bcrypt}$2a$...` through `DelegatingPasswordEncoder`, so
the algorithm can be changed later without locking existing users out. Passwords are limited
to 72 bytes (the BCrypt limit) and NUL characters are rejected in text fields.

**Tests.** The tests run against a real PostgreSQL 16.15 (Testcontainers), not an in-memory
database, because the behaviour under test is the database's: constraints, locks, SQLState
values. Race conditions are tested deterministically: the test holds a row lock, starts the
requests one by one, waits (through `pg_stat_activity`) until each is blocked, and then
releases the lock, so the outcome never depends on timing. Each of these tests was checked
to fail when the lock in the code is removed.

**CI.** GitHub Actions runs the tests on every pull request, with actions pinned by commit
SHA and Dependabot keeping them (and the Maven dependencies) up to date. The Maven wrapper
verifies the SHA-256 of the Maven distribution.

## Settlement suggestions

`GET /groups/{groupId}/balances` suggests transfers that bring every balance to zero. The
algorithm is greedy: it pairs the biggest debtor with the biggest creditor and repeats. It
always settles everything and uses at most n - 1 transfers for n people with a non-zero
balance, but the result is not guaranteed to be the minimum number of transfers (finding the
minimum is NP-hard). Ties are broken by user id, so the same balances always give the same
suggestions. Pending payments are not taken into account until the receiver confirms them.

## Known limitations and trade-offs

- **Trust model.** Like in Splitwise, any member can record an expense for any other members;
  there is no approval step.
- **Invitations.** An invitation goes to an email address that is not verified, it does not
  expire, and `POST /users` reveals whether an email is already registered. Fine for a small
  private group app, not for a public service.
- **No rate limiting.** Every request is authenticated with Basic auth and one BCrypt check.
  The API must be served over HTTPS.
- **Owner powers.** The owner invites people and can delete any expense or payment. There is
  no way to delete a group.
- **404 and 403.** A group you do not belong to answers 403 on some paths and 404 on others,
  depending on whether the id was found first.
- **Payments.** The amount of a payment is not capped at the debt. Pending payments do not
  affect balances or suggestions. A receiver can delete a payment they confirmed.
  `paidAt` is the time the payment was recorded, not a date the user supplies.
- **`createdById` is not the owner.** After an ownership transfer the group's creator and its
  owner differ.
- **No pagination.** Lists return everything, and balances are computed in memory from the
  group's whole history.
- **Migrations assume data created by the application.** For example, V10 normalises emails
  and would stop on two addresses that differ only in letter case.
- **`lock_timeout` is 5 seconds for every connection**, also the one Flyway uses.
- **Updates.** The PostgreSQL image is pinned to an exact version and is updated by hand,
  together with the Testcontainers image. The project targets Java 17.
