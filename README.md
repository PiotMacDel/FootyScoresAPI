# FootyScores API

A CLI tool that generates the **expected FootyScores API endpoints and reference payloads** for every
football match of the Paris 2024 Olympic Games, and **serves them via a local HTTP mock server** for automated API testing.

For each of the matches it produces:

* an **endpoint** - the URL the FootyScores API is expected to expose for that match;
* a **reference payload** - a JSON document with exactly the structure of [`assignment/example.json`](assignment/example.json).

The original assignment brief is preserved in [`assignment/ASSIGNMENT.md`](assignment/ASSIGNMENT.md).

---

## Quick start

### 1. Build
```bash
# Build (runs the test suite)
./mvnw clean package          # Windows: .\mvnw.cmd clean package
```
### 2. Generate data (offline or online)
```bash
# Fetches the data from the official Olympic feed and generates into ./out
# It will also create a snapshot of the data in ./snapshot folder.
./footyscores generate --snapshot-dir snapshot

# Generate into ./out without hitting the network (uses the committed snapshot)
./footyscores generate --snapshot-dir snapshot --offline

# Just print the generated endpoints
./footyscores generate --snapshot-dir snapshot --offline --quiet --endpoints-only

# Print the reference payload for a specific match (by team name or date)
./footyscores generate --snapshot-dir snapshot --offline --print  # prints all matches
./footyscores generate --snapshot-dir snapshot --offline --print=FBLMTEAM11------------GPB-000100--
./footyscores generate --snapshot-dir snapshot --offline --print=spain
./footyscores generate --snapshot-dir snapshot --offline --print=2024-08-09
```

### 3. Serve the API (Mock Server)
```bash
# Serve the endpoints locally on default port 8080 based on the files in ./out
./footyscores serve

# You can now access your API, e.g.:
# http://localhost:8080/api/v1/paris-2024/football/men/matches/2024-08-09/france-vs-spain
```

Requirements: **JDK 21+** (developed on JDK 25). Maven is supplied via the wrapper - no local install needed.
If the wrapper reports `JAVA_HOME not found`, set it first, e.g. on Windows:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"
```

---

## Output

```
out/
├── endpoints.json                                   # ordered index of all generated endpoints
└── matches/
    ├── all.json                                     # array of all match payloads
    ├── men
    |    ├── 2024-07-24-argentina-vs-morocco.json    # payload in example.json shape
    |   ...
    └── women
         ├── 2024-08-10-brazil-vs-united-states-of-america.json
        ...
```

`endpoints.json`:

```json
{
  "competition": "Olympic Games Paris 2024",
  "discipline": "Football",
  "source": "https://stacy.olympics.com/en/paris-2024/competition-schedule",
  "ordering": "Ascending by kickoff instant (UTC), then by Olympic RSC code as a stable tie-breaker.",
  "matchCount": 58,
  "collectionEndpoint": "/api/v1/paris-2024/football/matches",
  "collectionFile": "matches/all.json",
  "endpoints": [
    {
       "matchId": "FBLMTEAM11------------GPB-000100--",
       "endpoint": "/api/v1/paris-2024/football/men/matches/2024-07-24/argentina-vs-morocco", 
       "file": "matches/men/2024-07-24-argentina-vs-morocco.json"
    }
  ]
}
```

### Endpoint structure

```
{base-url}/api/v1/paris-2024/football/{gender}/matches/{yyyy-MM-dd}/{home}-vs-{away}
```

| Segment | Source | Example |
|---|---|---|
| `{gender}` | RSC code character 4 (`M`/`W`) | `men`, `women` |
| `{yyyy-MM-dd}` | Local match date from `kickoff` | `2024-08-09` |
| `{home}` / `{away}` | Team name, slugified | `france`, `united-states-of-america` |

Slugs are lowercase ASCII: diacritics are stripped and runs of non-alphanumerics collapse to a single hyphen.

A team plays at most once per day, so `(gender, date, home, away)` identifies exactly one match. 
It is designed this way instead of using match ID or RSC code in the URL so that the endpoint is 
**human-readable** for testing. The tool **fails loudly** if two matches ever produce the same endpoint. 
Both `--base-url` and `--endpoint-prefix` are configurable.

To return all match payloads, use the collection endpoint:

```
{base-url}/api/v1/paris-2024/football/matches
```

### Ordering (deterministic)

Matches are sorted **ascending by kickoff instant (UTC)**, then by **Olympic RSC code** as a stable
tie-breaker for simultaneous kickoffs (the group stage has many). Output is byte-for-byte identical
across runs: field order follows the record declarations, the JSON printer is pinned to two-space
indentation with LF newlines, and **no timestamps are emitted**.

---

## Data source

The assignment names the official
[Paris 2024 competition schedule](https://stacy.olympics.com/en/paris-2024/competition-schedule)
as the source of truth. That page is a React single-page app, so the tool reads the **same static JSON documents the page itself fetches**:

| Purpose | Document |
|---|---|
| Football schedule | `/OG2024/data/SCH_StartList~comp=OG2024~disc=FBL~lang=ENG.json` |
| Per-match detail | `/OG2024/data/RES_ByRSC_H2H~comp=OG2024~disc=FBL~rscResult={rsc}~lang=ENG.json` |

Base host: `https://stacy.olympics.com`. A browser-like `User-Agent` is required - the CDN answers
`403` otherwise.

### Identifying football matches

The schedule feed returns **60 units** for discipline `FBL`. Two are **victory ceremonies**
(phase `VICT`, one participant). The tool keeps only units that are football, non-ceremony, and have
exactly two participants, giving **58 matches**:

| | Group | Knockout | Total |
|---|---|---|---|
| Men (16 teams, 4 groups) | 24 | 8 | **32** |
| Women (12 teams, 3 groups) | 18 | 8 | **26** |
| | | | **58** |

### RSC codes

Match identity comes from the Olympic RSC code, e.g. `FBLMTEAM11------------FNL-000100--`:

| Offset | Meaning | Values |
|---|---|---|
| `[0,3)` | Discipline | `FBL` |
| `[3,4)` | Gender | `M`, `W` |
| `[22,26)` | Phase | `GPA-`…`GPD-`, `QFNL`, `SFNL`, `FNL-`, `VICT` |
| `[26,32)` | Unit number | `000100`, `000200`, … |

`competition.round` is derived from this: `Group A`…`Group D`, `Quarter-final`, `Semi-final`, and for
phase `FNL-`, unit `000100` → `Gold medal match`, `000200` → `Bronze medal match`.

### Field mapping

| Output field | Derived from |
|---|---|
| `competition.round` | RSC phase + unit number |
| `venue.name` / `venue.city` | Schedule `venue.description` / `location.description` |
| `kickoff` | Schedule `startDate` (ISO-8601 with `+02:00` offset, verbatim) |
| `status` | `PEN` if a shootout was played, `AET` after extra time, otherwise `FT` |
| `teams.home` / `away` | `HOME_AWAY` entry on the result document |
| `score.home` / `away` | Official `resultData` per team |
| `score.halfTime` | Official `results.periods` entry with `p_code = H1` |
| `scorers[]` | `playByPlay` actions with `pbpa_Result = GOAL`, ordered by period then event order |
| `scorers[].type` | `SHOT` → `open_play`, `PEN` → `penalty`, `FRD` → `free_kick`, `OG` → `own_goal` |
| `scorers[].assist` | Athlete with role `ASSIST` (omitted when absent) |
| `lineups.*.formation` | `FORMATION` entry on the team result item |
| `lineups.*.coach` | Team coach with function `COACH` |
| `lineups.*.startingXI` / `bench` | Team athletes split on the `STARTER` flag, ordered by squad order |

Player names are normalised to `Given Family`. Where the feed supplies only an all-caps display name
(common for mononyms such as `ADRIANA`), it is title-cased so the whole output uses one convention.

---

## Usage
```
./footyscores [COMMAND=generate|serve]
```

`generate` - creates the reference JSON payloads and endpoint index.
```
./footyscores generate [-hqV] [--endpoints-only] [--offline] [--print[=FILTER]]
                                          [--base-url=URL] [--endpoint-prefix=PATH] [--lang=CODE]
                                          [-o=DIR] [--snapshot-dir=DIR] [--source-url=URL]
```

| Option | Description | Default |
|---|---|---|
| `-o, --output=DIR` | Output directory | `out` |
| `--base-url=URL` | Prepended to every endpoint | *(empty - relative paths)* |
| `--endpoint-prefix=PATH` | Endpoint path prefix | `api/v1/paris-2024/football` |
| `--source-url=URL` | Origin of the Olympic data | `https://stacy.olympics.com` |
| `--snapshot-dir=DIR` | Read-through cache of raw upstream JSON | *(none)* |
| `--offline` | Fail rather than hit the network; requires `--snapshot-dir` | `false` |
| `--lang=CODE` | Upstream language code | `ENG` |
| `--endpoints-only` | Print endpoints to stdout, skip payload files | `false` |
| `--print=FILTER` | Print matching reference payload(s) as JSON to stdout instead of writing files.<br/>For example: `--print=spain` or `--print=2024-08-09`. |  |
| `-q, --quiet` | Suppress progress output | `false` |
| `-h, --help` /<br/> `-V, --version` | Help / version | |

`serve` - starts a local HTTP WireMock server serving the generated JSON files as real REST endpoints.
```
./footyscores serve [-hV] [-d=DIR] [-p=PORT]
```

| Option | Description | Default |
|---|---|---|
| `-d, --dir=DIR` | Directory containing the generated `endpoints.json` | `out` |
| `-p, --port=PORT` | HTTP port to listen on | `8080` |
| `-h, --help` /<br/> `-V, --version` | Help / version | |

### Snapshots and reproducibility

`--snapshot-dir` is a read-through cache: existing files are reused, missing ones are downloaded and
stored. The repository ships a committed `snapshot/` captured from the
official feed, so the tool and its tests run **fully offline** and produce identical results on any
machine. To refresh against live data, delete `snapshot/` and re-run without `--offline`.

---

## Testing

```bash
./mvnw test   # Windows: .\mvnw.cmd test
```

13 tests run against the committed snapshot and assert the acceptance criteria directly.
Dependencies: JUnit 5 + AssertJ. The test suite is **fully offline** and does not hit the network.

The test suite features robust API Integration Tests that dynamically spin up a WireMock HTTP server. 
Designed to be completely self-contained, the suite generates test data on the fly into a temporary directory—running entirely offline if the snapshot exists, or self-healing via a live fetch if it is missing. 
It then queries all match endpoints, validating HTTP interactions, headers, and strict JSON schema compliance against the Java domain models.

---

## Project layout

```
src/main/java/org/internship/footyscores/
├── Main.java                       # picocli entry point
├── EndpointGenerator.java          # filtering, ordering, uniqueness checks
├── cli/
│   ├── GenerateCommand.java        # CLI options and deterministic JSON writing
│   └── ServeCommand.java           # WireMock local server implementation
├── mapping/MatchMapper.java        # upstream JSON -> example.json shape
├── model/ 
│   ├── MatchFixture.java           # example.json shape
│   ├── EndpointIndex.java          # endpoints.json shape
│   └── Require.java                # precondition checks
├── output/
│   ├── EndpointBuilder.java        # endpoint + file naming, slugs
│   └── Json.java                   # deterministic JSON writer
└── stacy/
    ├── StacyClient.java            # HTTP access with snapshot cache
    └── RscCode.java                # Olympic RSC code parsing
```

Dependencies: picocli (CLI), Jackson (JSON), WireMock (Mock HTTP Server), JUnit 5 + AssertJ (tests).

---

## Deployment

`./mvnw package` produces a self-contained executable JAR at
`target/footyscores.jar` (dependencies shaded in), runnable anywhere with a JDK 21+ runtime.
