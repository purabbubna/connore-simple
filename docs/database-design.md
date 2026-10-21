# Connore database design

Connore uses 13 MySQL tables defined in [schema.sql](../sql/schema.sql). The
model follows the supplied developer guide. The editable paired ER and EER
diagrams are in [Connore_ER_EER.drawio](diagrams/Connore_ER_EER.drawio); both use
the same entities, with Connection at the centre. The EER specializes Connection
into its four states.

## Relational ER reference

The editable diagram is also available in [connore-er.mmd](connore-er.mmd).

```mermaid
erDiagram
    ACCOUNT ||--o| STUDENT_PROFILE : has
    STUDENT_PROFILE ||--o{ SOCIAL_LINK : shares
    CATEGORY ||--o{ INTEREST : groups
    STUDENT_PROFILE ||--o{ STUDENT_INTEREST : lists
    INTEREST ||--o{ STUDENT_INTEREST : chosen_as
    INTEREST ||--o| COMMUNITY : has
    STUDENT_PROFILE ||--o{ COMMUNITY_MEMBERSHIP : joins
    COMMUNITY ||--o{ COMMUNITY_MEMBERSHIP : has
    STUDENT_PROFILE ||--o{ CONNECTION : sends
    STUDENT_PROFILE ||--o{ CONNECTION : receives
    INTEREST |o--o{ CONNECTION : context_of
    CONNECTION ||--o| PENDING_REQUEST : is
    CONNECTION ||--o| ACCEPTED_CONNECTION : is
    CONNECTION ||--o| CLOSED_REQUEST : is
    CONNECTION ||--o| BLOCK : is
    STUDENT_PROFILE ||--o{ BLOCK : blocked_by
    ACCOUNT {
        int account_id PK
        string college_email UK
        string password_hash
        boolean email_verified
datetime created_at
    }
    STUDENT_PROFILE {
        int student_id PK "also FK to ACCOUNT"
        string full_name
        string department
        int year_of_study
        string bio
        boolean show_department
        boolean show_year
        boolean suggestible
    }
    SOCIAL_LINK {
        int link_id PK
        int student_id FK
        string platform
        string handle
        boolean shared
    }
    CATEGORY {
        int category_id PK
        string name UK
    }
    INTEREST {
        int interest_id PK
        string name UK
        int category_id FK
        boolean is_approved
    }
    STUDENT_INTEREST {
        int student_id PK "also FK"
        int interest_id PK "also FK"
        datetime added_at
    }
    COMMUNITY {
        int community_id PK
        int interest_id FK "unique"
        string description
        datetime created_at
    }
    COMMUNITY_MEMBERSHIP {
        int student_id PK "also FK"
        int community_id PK "also FK"
datetime joined_at
    }
    CONNECTION {
        int connection_id PK
        int requester_id FK
        int receiver_id FK
        int context_interest_id FK "optional"
        string status "PENDING ACCEPTED CLOSED BLOCKED"
        datetime created_at
        int student_low_id "derived"
        int student_high_id "derived"
    }
    PENDING_REQUEST {
        int connection_id PK "also FK"
        datetime expires_at
    }
    ACCEPTED_CONNECTION {
        int connection_id PK "also FK"
        datetime connected_at
    }
    CLOSED_REQUEST {
        int connection_id PK "also FK"
        string outcome "DECLINED or EXPIRED"
        datetime closed_at
    }
    BLOCK {
        int connection_id PK "also FK"
        int blocked_by FK
        datetime blocked_at
    }
```

## Mapping to the application

| Feature | Tables |
| --- | --- |
| Account registration and login | `account` |
| Student introduction and visibility settings | `student_profile` |
| Private or shared social handles | `social_link` |
| Interest catalog and selected interests | `category`, `interest`, `student_interest` |
| Communities and membership | `community`, `community_membership` |
| Requests, connections, and blocking | `connection`, `pending_request`, `accepted_connection`, `closed_request`, `block` |

The simple frontend calls a single Java API and uses the same database for
profiles, matching, communities and connections.

## How to read the guide alongside the implementation

The following details explain the SQL cardinalities and business rules:

- **Account and profile:** the diagram shows one-to-one. The guide’s page 27 says the profile is created on the first profile save, so a newly registered account temporarily has no profile. The shared primary/foreign key permits at most one profile per account.
- **Interest and community:** the diagram shows one-to-one. The unique foreign key on `community.interest_id` permits at most one community per interest. The guide’s catalog setup rule calls for one community for each interest; the SQL alone does not force every interest to already have a community. The explicit interest catalog loader creates a community for each approved catalog interest and preserves existing community records.
- **Connection states:** each connection is intended to have exactly one subtype row matching its status. The separate zero-or-one subtype edges do not, by themselves, enforce this rule. `ConnectionDao` changes the subtype and status together through transactions used by `ConnectionService`.
- **Student pairs:** generated low/high student IDs and their unique key represent both request directions as one connection pair. The check constraint prevents a student connecting to themselves.
- **Interest context:** a connection may have no context interest; `context_interest_id` is nullable.
- **Memberships and selected interests:** composite primary keys prevent a student joining the same community or selecting the same interest twice.

The compact active catalog contains 48 interests across eight categories.
`InterestCatalog --sync` marks interests outside that list inactive, retaining
their IDs, student selections, memberships and connection references. Queries
for the current catalog, matches and community browser include only approved
interests.
