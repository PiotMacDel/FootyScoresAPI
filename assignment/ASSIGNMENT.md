# Take-Home Assignment

You are working on a pipeline responsible for validating the API implementation of the FootyScores application. FootyScores provides football (soccer) match data through a public API.

The goal of this task is to design a solution that supports automated API testing using real-world data from the Paris 2024 Olympic Games. To enable this, the testing pipeline requires a reference definition of how the API endpoints should look for each football match played during the Olympic Games.

## Your task is to:

* Design and implement a CLI tool that generates the expected API endpoint for every football match.

* The generated endpoints will serve as reference values for automated tests validating the FootyScores API.

* Use the official Olympic Games competition schedule as the source of truth for match data:
  https://stacy.olympics.com/en/paris-2024/competition-schedule

## Acceptance Criteria

### The solution will be considered complete when the following criteria are met:

#### CLI Functionality

* The tool can be executed from the command line.
* Running the tool produces a list of API endpoints, one per football match played during the Paris 2024 Olympic Games.

#### Data Source

* Match data is derived from the official Olympic Games competition schedule.
* The tool correctly identifies and processes only football (soccer) matches.

#### Endpoint Generation

* Each generated endpoint follows a consistent and well-defined structure, exactly the same as in example.json
* The endpoint uniquely represents a single football match.
* All matches in the schedule are covered with no duplicates or omissions.

#### Deterministic Output

* Given the same input data, the tool always produces the same output.
* The output order is predictable and documented (e.g. sorted by date and kickoff time).

## Submission Requirements

Please create a repository containing your solution and include clear instructions on how to install, run, and deploy the code. Please submit your work by sharing a GitHub repository link.