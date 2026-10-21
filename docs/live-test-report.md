# Connore Simple live testing

Date: 22 October 2026 (Asia/Kolkata)

Target: [Connore Simple](http://localhost:8081/)

Application: `E:\Connore-Simple`

## Result

Passed using seven temporary accounts against the running Java application and
its real MySQL database: four accounts with independent HTTP sessions and three
accounts exercised through the browser. No mock API or sample-data preview was
used for these live checks.

| Verification | Result |
| --- | --- |
| Live API tests | 16 groups passed; 211 HTTP/data assertions |
| Browser checks | 16 checks passed after fixing the reproduced request-display issue |
| Frontend regression tests | 29 passed |
| Test-data cleanup | Seven accounts and four test connections removed |
| Existing database rows | All 13 table row counts and SHA-256 fingerprints match the baseline |

## Coverage

- Registration, college-email/password validation, duplicate accounts, login and logout.
- Empty first profiles, saved profile persistence and isolation between accounts.
- The 48-interest catalog, selection/removal, matching and suggestion opt-out.
- Department/year privacy and private social handles.
- Community joins/leaves, membership and connection requests from member lists.
- Pending requests, duplicate sends, acceptance, decline, reopening and reciprocal requests.
- Simultaneous reverse requests producing one accepted connection.
- Shared handles visible only after acceptance and explicit sharing; revocation reflected on navigation.
- Self-request rejection, unauthorized responses/handle changes, blocking, session ownership and CSRF checks.

## Fixes made and retested

1. Closed outgoing requests previously appeared as “Awaiting reply” and prevented
   sending another request. They now show “Request closed” with a working send
   control. Closed requests no longer offer the invalid block action. Historical
   inactive interest contexts are omitted when sending again.
2. Navigation previously reused cached data from other accounts. Entering a page
   now reloads current data, including acceptance, community members and handle
   sharing changes. Unsaved profile edits are retained for the same account;
   obsolete responses cannot restore logged-out or different-account state.

The live browser retest successfully resent the closed request, observed its
acceptance from a separate session without a page reload, and confirmed both
sharing and revocation of the owner's test handle.

## Data preservation

A baseline snapshot was taken before creating test accounts. Cleanup verified
exact test identities and refused links to existing accounts. It removed only
the owned test connections and accounts, with their dependent rows. After the
cleanup transaction, all 13 table row counts and row-content fingerprints were
identical to the baseline. Test sessions were logged out. The original project
at `E:\Connore` was not modified.

Sanitized API, browser and cleanup evidence is saved locally in
`E:\Connore-Simple\out\live-qa`. Passwords and session cookies are excluded.

The 48-hour expiry interval was not waited out in this live session.
