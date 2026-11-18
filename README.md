
# SOC: CONTROL ROOM

**A multiplayer cybersecurity simulation built with Java.**

SOC: CONTROL ROOM is a multiplayer project inspired by the operations of a Security Operations Center (SOC).

The application simulates an environment in which players take on different roles, investigate suspicious activities and collaborate to identify a potential insider within an organization.

Built with Java, Swing and TCP socket communication, the project combines cybersecurity concepts, network programming and interactive gameplay.

> **Project status:** Playable — complete game loop from lobby to results.

---

## Overview

SOC: CONTROL ROOM is designed around an asymmetric multiplayer experience.

Players connect to a central server and participate in a simulated security incident. Each participant receives a role with different objectives and information.

The game revolves around two opposing sides:

| Role | Objective |
|---|---|
| **SOC Analyst** | Investigate suspicious activity and identify the insider. |
| **Insider** | Remain undetected while attempting to interfere with the investigation. |

The experience focuses on teamwork, observation, decision-making and information analysis.

All scenarios and actions are simulated. The application does not perform attacks against real systems.

## Gameplay

A game session lasts approximately **5–6 minutes** (briefing 12 s, investigation 4 min, voting 1 min — all configurable).

### 1. Connection

Players launch the client application and connect to the central server using its IP address and their chosen nickname.

### 2. Lobby

Connected players enter a shared waiting room where they can see the other participants. The first player to join is the **host** and can start the session once the minimum number of operators (default 3) is reached. The session can also be started from the server's administrative panel.

### 3. Role Assignment

The server secretly picks one **Insider** and assigns every player a fictional workstation (`WS-xx`). Each client receives only its own role through a private `ROLE_ASSIGN` message, revealed with an animated briefing.

### 4. Investigation

The corporate network is shown as an animated map with six systems (firewall, VPN gateway, mail server, file server, customer database, HR portal). Every system keeps a simulated log that the server fills with normal background activity.

| Action | Who | Effect | Cooldown |
|---|---|---|---|
| **Analyze** | Everyone | Extracts up to 4 log entries from the selected system and adds them to the player's private evidence board. | 6 s |
| **Isolate** | Everyone | Isolates a system for 20 s: exfiltration attempts on it are blocked and leave a trace. | 35 s |
| **Exfiltrate** | Insider | Steals +10% of the company data. Leaves a log trace that may reveal the workstation. | 18 s |
| **Wipe** | Insider | Deletes the most recent trace from a system's log (a "manual log rotation" entry may remain). | 25 s |
| **Frame** | Insider | Plants a fake exfiltration trace that points to another operator. | 35 s |

Evidence is private: players must **share** it in the operational channel, where it appears as a *verified* card. The team chat is shared by everyone — including the Insider, who can lie.

The Insider wins immediately if exfiltration reaches **100%**.

### 5. Events

Every 35–50 seconds the server triggers a random event:

| Event | Effect |
|---|---|
| SIEM report | Reveals the system used for the last exfiltration. |
| Backup restore | Restores wiped log entries on a system. |
| Traffic spike | Floods every log with background noise. |
| Badge audit | Names two operators who entered the server room — one of them is the Insider (once per game). |
| Maintenance | A system becomes unavailable for 20 s. |
| Honeypot | Decoy data is placed on a system for 30 s. Only analysts are told; an Insider who exfiltrates from it is recorded with certainty. |

### 6. Voting

Players discuss in the channel and vote for the suspected Insider (or abstain). Votes can be changed until the timer ends; the phase closes early when everyone has voted.

### 7. Results

The player with the most votes is expelled. If the Insider is expelled, the analysts win; a tie, a majority of abstentions or the expulsion of an innocent operator gives the victory to the Insider. The results screen reveals the Insider, the vote tally, game statistics and the complete timeline of the Insider's actions.

---

## Features

### Implemented

- Java desktop application with animated Swing interfaces.
- TCP client-server communication with a shared JSON protocol.
- Centralized, server-authoritative game logic.
- Real-time lobby synchronization with host management and duplicate nickname checks.
- Secret role assignment with animated briefing.
- Dedicated Analyst and Insider interfaces (different accent colours, actions and visible information).
- Animated network map with live traffic, alerts, isolation, maintenance and honeypot states.
- Investigation mechanics, private evidence board and verified evidence sharing.
- Insider actions: exfiltration, trace wiping and framing.
- Simulated security events.
- Operational channel (team chat, events, shared evidence) and toast notifications.
- Voting system with live progress.
- Results screen with Insider reveal, vote tally, statistics and Insider timeline.
- Return to the lobby for a new session.
- Administrative control panel on the server (live session state, operators, log, configuration, start/stop).
- Maven multi-module project structure and protocol unit tests.

---

## Architecture

The application follows a **client-server architecture**.

The server is responsible for managing connected clients and coordinating communication. Clients provide the graphical interface and send requests to the server.

The project is divided into three Maven modules:

| Module | Responsibility |
|---|---|
| `soc-common` | Shared classes and communication protocol |
| `soc-server` | Server-side networking and connection management |
| `soc-client` | Player application and graphical interface |

### Architecture Diagram

```text
                 SOC: CONTROL ROOM

                  +----------------+
                  |     SERVER     |
                  |                |
                  |  TCP Server    |
                  |  Connection    |
                  |  Management    |
                  |  Game Manager  |
                  |  Admin Panel   |
                  +-------+--------+
                         |
              TCP / JSON communication
                         |
          +--------------+--------------+
          |              |              |
     +----+----+    +----+----+    +----+----+
     | Client  |    | Client  |    | Client  |
     |    01   |    |    02   |    |    03   |
     +---------+    +---------+    +---------+
```

### `soc-common`

Contains the classes shared between the client and server.

Responsibilities:

- Define communication message types.
- Represent messages exchanged between components.
- Serialize and deserialize JSON messages and payloads.
- Provide a common communication protocol and game data model.

Main classes:

| Class | Description |
|---|---|
| `Message` | Represents a communication message. |
| `MessageType` | Defines the available message types. |
| `Protocol` | Handles JSON encoding and decoding of messages and payloads. |
| `model.*` | Payload records (`LobbyState`, `RoleInfo`, `GameState`, `Evidence`, `GameEvent`, `GameResult`, ...) and enums (`Role`, `Phase`, `ActionType`, `SystemStatus`). |

### `soc-server`

The server is the central component of the application and the single source of truth for the game.

Responsibilities:

- Accept incoming TCP connections.
- Register connected clients and manage the lobby.
- Run the game state machine (briefing, investigation, voting, results).
- Validate every player action (role, phase, cooldown, target).
- Send each client only the information its role is allowed to see.
- Provide the administrative control panel.

Main classes:

| Class | Description |
|---|---|
| `ServerMain` | Starts the TCP server and the admin panel. |
| `ClientHandler` | Handles communication with an individual client and routes requests. |
| `ConnectionManager` | Tracks connected clients, player names, join order and host. |
| `GameConfig` | Session configuration (minimum players, phase durations). |
| `ServerLog` | Server log shared by console and admin panel. |
| `game.GameManager` | Game state machine, actions, events, voting and results. |
| `game.Scenario` | Network systems, role briefings and simulated log templates. |
| `game.PlayerState` / `SystemState` / `LogEntry` | Server-side game state. |
| `admin.AdminWindow` | Administrative control panel. |

### `soc-client`

The client is the desktop application used by players.

Responsibilities:

- Provide the graphical interface.
- Establish a connection with the server.
- Send player requests to the server.
- Receive server messages and update the interface.

Main classes:

| Class | Description |
|---|---|
| `ClientMain` | Application entry point. |
| `ConnectionWindow` | Connection and login interface. |
| `NetworkClient` | Manages TCP communication. |
| `ClientSession` | Shared session state and message dispatching between windows. |
| `LobbyWindow` | Multiplayer lobby with host controls. |
| `GameWindow` | Main game interface for Analysts and the Insider. |
| `NetworkMapPanel` | Animated, interactive network map. |
| `EvidencePanel` / `FeedPanel` / `VotePanel` | Evidence board, operational channel and voting screen. |
| `BriefingOverlay` / `ResultsOverlay` | Animated role reveal and results screen. |
| `ActionButton` / `TimerRing` / `ThreatMeter` / `ToastLayer` / `PhaseBanner` | Animated UI components. |
| `Theme` / `UiKit` / `Anim` | Shared colours, components and animation helpers. |

---

## Project Structure

```text
SOC-Control-Room/
│
├── pom.xml
├── compila.bat
├── avvia-server.bat
├── avvia-client.bat
├── avvia-test-locale.bat
├── launcher/
│   └── src/Launcher.java         (one-click launcher window for Windows and macOS)
│
├── soc-common/
│   └── src/
│       ├── main/java/it/soc/common/
│       │   ├── Message.java
│       │   ├── MessageType.java
│       │   ├── Protocol.java
│       │   └── model/            (payload records and enums)
│       └── test/java/it/soc/common/
│           └── ProtocolTest.java
│
├── soc-server/
│   └── src/main/java/it/soc/server/
│       ├── ServerMain.java
│       ├── ClientHandler.java
│       ├── ConnectionManager.java
│       ├── GameConfig.java
│       ├── ServerLog.java
│       ├── game/                 (GameManager, Scenario, state classes)
│       └── admin/                (AdminWindow)
│
└── soc-client/
    └── src/main/java/it/soc/client/
        ├── ClientMain.java
        ├── ConnectionWindow.java
        ├── NetworkClient.java
        ├── ClientSession.java
        ├── LobbyWindow.java
        ├── GameWindow.java
        └── ...                   (map, panels, overlays, UI components)
```

---

## Technologies

| Technology | Purpose |
|---|---|
| Java 21 | Main programming language |
| Java Swing | Graphical user interface |
| TCP Sockets | Network communication |
| JSON | Message serialization format |
| Jackson | JSON processing |
| Maven (with the Shade plugin) | Build, dependency management and runnable jars |
| JUnit 5 | Unit testing |
| Git | Version control |

---

## Communication Protocol

Client-server communication is implemented using TCP sockets.

Messages are exchanged as JSON objects, with each message terminated by a newline character.

This allows the receiver to process messages individually using a line-based input stream.

### Message Structure

Each message follows a shared structure:

```json
{
  "type": "JOIN_LOBBY",
  "sender": "player",
  "content": "Operator01"
}
```

| Field | Description |
|---|---|
| `type` | Identifies the message type. |
| `sender` | Identifies the message sender. |
| `content` | Contains the message payload: plain text or a JSON-encoded payload record. |

Structured payloads (lobby state, game state, evidence, ...) are encoded with `Protocol.toContent` and decoded with `Protocol.fromContent`, so the message envelope stays the same for every type.

### Available Message Types

| Message | Direction | Content |
|---|---|---|
| `CONNECT` | Client → Server | Connection request |
| `PONG` | Server → Client | Connection confirmation / ping response |
| `PING` | Client → Server | Connection check |
| `JOIN_LOBBY` | Client → Server | Nickname |
| `LOBBY_UPDATE` | Server → All | `LobbyState` (players, host, minimum players, game in progress) |
| `START_GAME` | Client → Server | Start request (host only) |
| `ROLE_ASSIGN` | Server → One | `RoleInfo` (private role, workstation, briefing, objectives) |
| `GAME_START` | Server → One | Initial `GameState` |
| `GAME_UPDATE` | Server → One | `GameState` tailored to the player's role (every second) |
| `PLAYER_ACTION` | Client → Server | `PlayerAction` (action, target system, framed player) |
| `REQUEST_EVIDENCE` | Client → Server | System id to analyze |
| `EVIDENCE_RESULT` | Server → One | `EvidenceResult` (extracted log entries) |
| `ACTION_RESULT` | Server → One | `ActionResult` (outcome, message, cooldown) |
| `SHARE_EVIDENCE` | Client → Server | Evidence id |
| `CHAT` | Both | Text from the client; `ChatMessage` (optionally with verified evidence) from the server |
| `GAME_EVENT` | Server → Players | `GameEvent` (phase changes, simulated security events) |
| `SUBMIT_VOTE` | Client → Server | Voted player name, or empty to abstain |
| `GAME_OVER` | Server → Players | `GameResult` |
| `ERROR` | Server → One | Error description |
| `DISCONNECT` | Client → Server | Client disconnection |

### Information Isolation

The server builds a different `GameState` for each player:

- Only analysts see honeypots and isolations made by other players.
- The Insider only sees the isolations it performed itself.
- Honeypot alerts are sent to analysts only.
- Insider action results are sent privately to the Insider.

---

## Security Design

SOC: CONTROL ROOM follows a server-authoritative design.

The server is intended to remain the single source of truth for all game-related information.

Important design principles include:

- **Server-side game state:** Game state must be controlled by the server.
- **Private information:** Secret roles and confidential information must not be exposed to unauthorized clients.
- **Action validation:** Player actions must be validated by the server.
- **Client isolation:** Clients should only receive the information required for their role.
- **Controlled simulation:** All cybersecurity scenarios are fictional and take place within the application.

These principles help prevent clients from manipulating game state or accessing information they should not receive.

---

## Installation

### Requirements

Before building the project, make sure the following software is installed:

- JDK 21 or later (for example Temurin from https://adoptium.net).
- Apache Maven 3.9 or later. On Windows it is optional: `compila.bat` downloads Maven 3.9.9 into the `.maven` folder when it is missing.
- Git, only to clone the repository.

### Clone the Repository

```bash
git clone https://github.com/liukseek/SOC-Control-Room.git
cd SOC-Control-Room
```

### Build the Project

On Windows, double-click `compila.bat`, or run the following command from the project root directory:

```bash
mvn clean package
```

Maven will:

1. Clean previous build artifacts.
2. Resolve dependencies.
3. Compile the project modules.
4. Run the available tests (`compila.bat` skips them with `-DskipTests`).
5. Create the runnable jars with the Maven Shade plugin:

| Jar | Description |
|---|---|
| `soc-server/target/soc-server-1.0.0-SNAPSHOT.jar` | Server (includes `soc-common` and Jackson). |
| `soc-client/target/soc-client-1.0.0-SNAPSHOT.jar` | Client (includes `soc-common` and Jackson). |

A successful build should finish with:

```text
BUILD SUCCESS
```

To run only the tests (protocol serialization tests in `soc-common`):

```bash
mvn test
```

---

## Running the Application

### Windows launchers

The repository root contains ready-to-use batch files:

| File | Description |
|---|---|
| `compila.bat` | Builds the project with Maven (`mvn clean package -DskipTests`), creating the runnable jars. If Maven is not installed, it downloads it automatically into the `.maven` folder (only a JDK 21+ is required). |
| `avvia-server.bat` | Starts the server (builds first if needed). Accepts the server arguments, e.g. `avvia-server.bat --min=4`. |
| `avvia-client.bat` | Starts a client window (builds first if needed). |
| `avvia-test-locale.bat` | Starts the server (with the admin panel) and three clients on the same PC for a quick local test. |

After changing the code, run `compila.bat` again to rebuild the jars.

### One-click launcher (Windows and macOS)

`launcher/src/Launcher.java` is a small Swing window with three buttons: play (client), start the server, and local test (server plus three clients). Compile it into a `SOC-Control-Room.jar` placed in the project root, or next to `soc-server.jar` and `soc-client.jar`:

```bash
javac --release 21 -d out launcher/src/Launcher.java
jar cfe SOC-Control-Room.jar Launcher -C out .
```

Double-click the jar on Windows or macOS (JDK/JRE 21+ required). It looks for `soc-server.jar` and `soc-client.jar` in its own folder, then for the jars in `soc-server/target` and `soc-client/target`.

### Running without the launchers

Any platform with Java 21+ can start the two components directly from the jars.

SOC: CONTROL ROOM consists of two independently launched components.

### 1. Start the Server

```bash
java -jar soc-server/target/soc-server-1.0.0-SNAPSHOT.jar
```

During development you can also run the `ServerMain` class located in `soc-server/src/main/java/it/soc/server/ServerMain.java` from your IDE.

The server listens for incoming TCP connections on port:

```text
1234
```

On start it prints `Server in ascolto sulla porta 1234`.

When a graphical environment is available, the **administrative control panel** opens automatically. It shows the session phase and timer, the exfiltration level, the connected operators (roles can be revealed with *Mostra ruoli*), the server log and the configuration; it can also start or stop a session.

Optional arguments:

| Argument | Description |
|---|---|
| `--nogui` | Run without the admin panel (console only). |
| `--min=N` | Minimum number of players (default 3). |
| `--investigation=S` | Investigation duration in seconds (default 240). |
| `--voting=S` | Voting duration in seconds (default 60). |

Keep the server running while players connect.

### 2. Start a Client

```bash
java -jar soc-client/target/soc-client-1.0.0-SNAPSHOT.jar
```

During development you can also run the `ClientMain` class located in `soc-client/src/main/java/it/soc/client/ClientMain.java` from your IDE.

The connection interface allows the player to specify:

- A player nickname.
- The server IP address.

The default server port is `1234`.

### 3. Connect Multiple Players

Launch additional client instances and connect them to the same server. At least three players are needed to start a session. The first player in the lobby is the host and starts the session with **AVVIA SESSIONE**.

For clients running on different computers, use the server computer's network IP address rather than `localhost`. Copy `soc-client-1.0.0-SNAPSHOT.jar` to the other computers and run it with `java -jar` (JDK/JRE 21+).

Make sure the server port is reachable through the local network and that firewall rules allow the connection.

---

## Development Status

| Component | Status |
|---|---|
| Maven multi-module structure | Completed |
| Shared communication protocol | Completed |
| JSON serialization and deserialization | Completed |
| Protocol unit tests | Implemented |
| TCP server | Completed |
| Client connection handling | Completed |
| Player registration | Completed |
| TCP client | Completed |
| Connection GUI | Completed |
| Lobby GUI | Completed |
| Lobby integration | Completed |
| Real-time lobby synchronization | Completed |
| Game management | Completed |
| Role assignment | Completed |
| Analyst interface | Completed |
| Insider interface | Completed |
| Investigation mechanics | Completed |
| Evidence collection | Completed |
| Random events | Completed |
| Voting system | Completed |
| Game results | Completed |
| Administrative interface | Completed |

---

## Roadmap

### Phase 1 — Core Infrastructure

- [x] Create Maven multi-module structure.
- [x] Implement shared JSON protocol.
- [x] Implement TCP server.
- [x] Implement client networking.
- [x] Create connection interface.
- [x] Create initial lobby interface.
- [x] Complete lobby synchronization.

### Phase 2 — Game Infrastructure

- [x] Implement game session management.
- [x] Implement player state management.
- [x] Implement role assignment.
- [x] Implement game phases.
- [x] Implement server-side game validation.

### Phase 3 — Gameplay

- [x] Develop Analyst interface.
- [x] Develop Insider interface.
- [x] Implement investigation mechanics.
- [x] Implement evidence collection.
- [x] Add simulated security events.
- [x] Implement voting mechanics.
- [x] Implement victory conditions.

### Phase 4 — Administration and Polish

- [x] Develop administrative control panel.
- [x] Add session configuration.
- [x] Improve error handling.
- [x] Improve network reliability.
- [x] Add complete game statistics.
- [x] Finalize graphical interfaces.

---

## Contributing

Contributions, suggestions and bug reports are welcome.

If you want to contribute:

1. Fork the repository.
2. Create a feature branch.
3. Implement your changes.
4. Test the project using Maven.
5. Submit a pull request.

Please keep changes consistent with the existing Maven module structure and coding conventions.

---

## License

This project is released under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## Project Information

| | |
|---|---|
| Project | SOC: CONTROL ROOM |
| Repository | [liukseek/SOC-Control-Room](https://github.com/liukseek/SOC-Control-Room) |
| Language | Java |
| Build system | Maven |
| Architecture | Client-Server |
| Communication | TCP / JSON |
| Status | Playable |

---

**SOC: CONTROL ROOM**

*Investigate. Analyze. Identify.*
