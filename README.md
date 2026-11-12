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
- Record expenses (who paid, who shares it), split equally, by percentages or by exact
  amounts. Every split adds up to the exact total.
- Record payments between members. A payment recorded by the sender is pending until the
  receiver confirms it; a payment recorded by the receiver is confirmed at once.
- See the balance of every member and suggested transfers that bring all balances to zero.
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

You need Java 17 and a PostgreSQL 16 server. Create a database (for example
`createdb splitapp`), copy `.env.example` to `.env` and adjust `DB_URL`, `DB_USERNAME` and
`DB_PASSWORD` to your server, then run `./mvnw spring-boot:run`. Flyway creates the schema
on start.

### Tests

```
./mvnw test
```

Docker has to be running: the tests start PostgreSQL 16.15 with Testcontainers.

## Trying it

The ids below (users 1 and 2, group 1, invitation 1, payment 1) assume a fresh database.

Register two users:

```
curl -X POST localhost:8080/users -H 'Content-Type: application/json' \
  -d '{"firstName":"Jan","lastName":"Kowalski","email":"jan@example.com","password":"secret123"}'

curl -X POST localhost:8080/users -H 'Content-Type: application/json' \
  -d '{"firstName":"Anna","lastName":"Nowak","email":"anna@example.com","password":"secret123"}'
```

Jan creates a group and invites Anna, and Anna accepts:

```
curl -u jan@example.com:secret123 -X POST localhost:8080/groups \
  -H 'Content-Type: application/json' -d '{"groupName":"Trip"}'

curl -u jan@example.com:secret123 -X POST localhost:8080/groups/1/invitations \
  -H 'Content-Type: application/json' -d '{"email":"anna@example.com"}'

curl -u anna@example.com:secret123 -X POST localhost:8080/invitations/1/accept \
  -H 'Content-Type: application/json'
```

Jan paid 60.00 for a dinner shared equally, so Anna owes him 30.00:

```
curl -u jan@example.com:secret123 -X POST localhost:8080/groups/1/expenses \
  -H 'Content-Type: application/json' \
  -d '{"amount":60.00,"category":"FOOD","description":"Dinner","paidById":1,"splitType":"EQUAL","participantIds":[1,2]}'

curl -u jan@example.com:secret123 localhost:8080/groups/1/balances
```

Anna pays Jan back. The payment counts once Jan confirms it:

```
curl -u anna@example.com:secret123 -X POST localhost:8080/groups/1/payments \
  -H 'Content-Type: application/json' -d '{"senderId":2,"receiverId":1,"amount":30.00}'

curl -u jan@example.com:secret123 -X POST localhost:8080/groups/1/payments/1/confirm \
  -H 'Content-Type: application/json'

curl -u jan@example.com:secret123 localhost:8080/groups/1/balances
```

Every `POST` must be sent with `Content-Type: application/json`, also the ones without a
body (accept, decline, confirm, leave), otherwise the answer is 415 (or 401 when an endpoint
that needs credentials is called without them).

## API

"Member" means a member of the group in the path. Every endpoint except `POST /users` and the
API description (OpenAPI and Swagger UI) needs HTTP Basic credentials.

| Method and path | Who | What |
|---|---|---|
| `POST /users` | anyone | Register |
| `GET /users/me` | user | The current user |
| `POST /groups` | user | Create a group (the creator becomes its owner) |
| `GET /groups` | user | The user's groups |
| `GET /groups/{groupId}` | member | One group with its members |
| `POST /groups/{groupId}/invitations` | owner | Invite someone by email |
| `GET /groups/{groupId}/invitations` | owner | The group's pending invitations |
| `DELETE /groups/{groupId}/invitations/{invitationId}` | owner | Cancel an invitation |
| `GET /invitations` | user | The user's pending invitations |
| `POST /invitations/{invitationId}/accept` | invited user | Accept and join the group |
| `POST /invitations/{invitationId}/decline` | invited user | Decline |
| `POST /groups/{groupId}/expenses` | member | Record an expense |
| `GET /groups/{groupId}/expenses` | member | List expenses, newest first |
| `DELETE /groups/{groupId}/expenses/{expenseId}` | author or owner | Delete an expense |
| `POST /groups/{groupId}/payments` | sender or receiver | Record a payment between two members (the caller is one of them) |
| `GET /groups/{groupId}/payments` | member | List payments, newest first |
| `POST /groups/{groupId}/payments/{paymentId}/confirm` | receiver | Confirm a pending payment |
| `DELETE /groups/{groupId}/payments/{paymentId}` | author, owner, or receiver of a pending payment | Delete a payment |
| `GET /groups/{groupId}/balances` | member | Balances of the current members and suggested settlement transfers |
| `POST /groups/{groupId}/owner` | owner | Transfer ownership to another member |
| `POST /groups/{groupId}/leave` | member | Leave the group (not the owner; balance must be zero and no pending payments) |

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
themselves, one pending invitation per email and group, lower-case unique emails, and at
most one owner per group (a partial unique index; the application makes sure there is always
one). Hibernate runs with `ddl-auto=validate`, so the entities must match the Flyway
migrations (V1 to V12).

**Locking.** Every operation that changes a group (membership, ownership, expenses, payments,
invitations) takes a pessimistic write lock on the group row (`SELECT ... FOR UPDATE`) after
checking membership, and checks membership again once it holds the lock. Accepting or
declining an invitation is the exception: the user is not a member yet, so it reads the
invitation, locks the group and reads the invitation again. Such operations on one group run
one after another. I chose a lock over `@Version` because these operations are short,
conflicts are expected on a busy group, and several rules span more than one entity (for
example "a member who is leaving must not be charged at the same time"). With a version
check one of two concurrent requests would be rejected and the client would have to retry.
A `lock_timeout` of 5 seconds turns a stuck wait into a 409 instead of tying up a connection.
Changing ownership demotes the old owner and flushes before promoting the new one, so the
database never sees two owners.

**A member who left keeps a frozen balance.** Leaving requires a zero balance and no pending
payments, and entries that involve a former member cannot be deleted (not even by the
owner). So the history of someone who left stays consistent.

**Errors.** Every error the application produces is an `application/problem+json`
(RFC 9457), including the ones produced before Spring MVC (401, 415). Database errors are
mapped by SQLState and never expose the conflicting values: a unique violation is a 409, a
value the database cannot store is a 400, and any other integrity violation (`CHECK`,
foreign key) means a bug in the code and gives a 500. Convention: `409` is a duplicate
registration, a unique-constraint conflict or a concurrent change (retrying may help);
`422` is any other business rule the request breaks, including a state that does not allow
it (for example an invitation that is already pending or a payment that is already
confirmed) and rules that tie several fields together (for example a split type that does
not match the shares); `400` is a malformed request or an invalid value in a single field.
Messages are always in English (`spring.web.locale-resolver=fixed`).

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

**CI.** GitHub Actions runs the tests and builds the Docker image on every pull request to
`develop` or `main` and on every push to them, with actions pinned by commit SHA and
Dependabot keeping them (and the Maven dependencies) up to date. The Maven wrapper verifies
the SHA-256 of the Maven distribution.

## Settlement suggestions

`GET /groups/{groupId}/balances` suggests transfers that bring every balance to zero. The
algorithm is greedy: it sorts the debtors and the creditors by the size of their balance,
biggest first, and pairs them in that order. It always settles everything and uses at most
n - 1 transfers for n people with a non-zero balance, but the result is not guaranteed to be
the minimum number of transfers (finding the minimum is NP-hard). Ties are broken by user id,
so the same balances always give the same suggestions. Pending payments are not taken into
account until the receiver confirms them.

## Known limitations and trade-offs

- **Trust model.** Like in Splitwise, any member can record an expense for any other members;
  there is no approval step. In the same spirit, a payment recorded by its receiver is
  confirmed at once, so a receiver can reduce a sender's debt without the sender's consent.
- **Invitations.** An invitation goes to an email address that is not verified and does not
  expire, and `POST /users` reveals whether an email is already registered. Fine for a small
  private group app, not for a public service.
- **No rate limiting.** Every request is authenticated with Basic auth and one BCrypt check.
  The API must be served over HTTPS.
- **Owner powers.** The owner invites people and can delete any expense or payment, except
  entries that involve a former member. There is no way to delete a group.
- **404 and 403.** A group that does not exist answers 404 and one that exists but you do not
  belong to answers 403, so a response reveals which group ids exist.
- **User ids in a request body.** An id that does not exist (in `paidById`,
  `participantIds`, `shares`, `senderId`, `receiverId`) answers 404, while an existing user
  who is not in the group answers 422, so a group member can find out which user ids exist.
- **Odd URLs.** Requests that Tomcat or Spring Security's URL firewall refuse before the
  application sees them are not `problem+json`: a path with an encoded slash (`%2F`) gets
  Tomcat's HTML 400 page, and a path with a semicolon is reported as a 401. Normal clients
  never send such URLs.
- **Payments.** The amount of a payment is not capped at the debt. Pending payments do not
  affect balances or suggestions. `paidAt` is the time the payment was recorded, not a date
  the user supplies.
- **`createdById` is not the owner.** After an ownership transfer the group's creator and its
  owner differ.
- **Balances show current members only.** A member who left had a zero balance, so nothing is
  lost.
- **No pagination.** Lists return everything, and balances are computed in memory from the
  group's whole history.
- **Concurrent registration.** When two requests register the same email at the same time,
  the second one gets a generic 409 instead of "Email is already in use".
- **Migrations assume data created by the application.** For example, V10 normalises emails
  and would stop on two addresses that differ only in letter case.
- **`lock_timeout` is 5 seconds for every connection**, also the one Flyway uses.
- **Updates.** The PostgreSQL image is pinned to an exact version and is updated by hand,
  together with the Testcontainers image. The project targets Java 17.

## License

MIT, see [LICENSE](LICENSE).
