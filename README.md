
# SOC: CONTROL ROOM

**A multiplayer cybersecurity simulation built with Java.**

SOC: CONTROL ROOM is a multiplayer project inspired by the operations of a Security Operations Center (SOC).

The application simulates an environment in which players take on different roles, investigate suspicious activities and collaborate to identify a potential insider within an organization.

Built with Java, Swing and TCP socket communication, the project combines cybersecurity concepts, network programming and interactive gameplay.

> **Project status:** Work in progress.

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

A game session is designed to last approximately **5–6 minutes**.

The planned gameplay cycle consists of the following phases:

### 1. Connection

Players launch the client application and connect to the central server using its IP address and their chosen nickname.

### 2. Lobby

Connected players enter a shared waiting room where they can see the other participants.

### 3. Role Assignment

The server assigns each player a role.

Role information is private and must only be disclosed to the appropriate client.

### 4. Investigation

SOC Analysts examine simulated information and investigate suspicious activity.

### 5. Events

Unexpected events can occur during the session, introducing new information or changing the investigation.

### 6. Voting

Players submit their suspicions and vote to identify the potential insider.

### 7. Results

The server evaluates the outcome and determines which side achieved its objective.

*Gameplay mechanics are currently under development.*

---

## Features

### Currently Implemented

- Java desktop application.
- Graphical connection interface.
- TCP client-server communication.
- Centralized server accepting multiple connections.
- Player registration and connection management.
- Shared JSON communication protocol.
- Maven multi-module project structure.
- Protocol unit tests.
- Graphical lobby interface.

### Planned Features

- Multiplayer game session management.
- Secret role assignment.
- Dedicated Analyst and Insider interfaces.
- Investigation mechanics.
- Simulated security events.
- Evidence collection.
- Player voting.
- Game result calculation.
- Administrative control panel.

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

                  +-------------+
                  |    SERVER   |
                  |             |
                  | TCP Server  |
                  | Connection  |
                  | Management  |
                  +------+------+
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
- Serialize and deserialize JSON messages.
- Provide a common communication protocol.

Main classes:

| Class | Description |
|---|---|
| `Message` | Represents a communication message. |
| `MessageType` | Defines the available message types. |
| `Protocol` | Handles JSON encoding and decoding. |

### `soc-server`

The server is the central component of the application.

Responsibilities:

- Accept incoming TCP connections.
- Register connected clients.
- Manage player connections.
- Receive and process client messages.
- Broadcast updates to connected clients.
- Coordinate future game sessions.

Main classes:

| Class | Description |
|---|---|
| `ServerMain` | Starts the TCP server. |
| `ClientHandler` | Handles communication with an individual client. |
| `ConnectionManager` | Tracks connected clients and registered players. |

### `soc-client`

The client is the desktop application used by players.

Responsibilities:

- Provide the graphical interface.
- Establish a connection with the server.
- Send messages to the server.
- Receive and process server messages.
- Display lobby and game information.

Main classes:

| Class | Description |
|---|---|
| `ClientMain` | Application entry point. |
| `ConnectionWindow` | Connection and login interface. |
| `NetworkClient` | Manages TCP communication. |
| `LobbyWindow` | Displays the multiplayer lobby. |

---

## Project Structure

```text
soc-control-room/
│
├── pom.xml
│
├── soc-common/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   └── java/it/soc/common/
│       │       ├── Message.java
│       │       ├── MessageType.java
│       │       └── Protocol.java
│       │
│       └── test/
│           └── java/it/soc/common/
│               └── ProtocolTest.java
│
├── soc-server/
│   ├── pom.xml
│   └── src/
│       └── main/
│           └── java/it/soc/server/
│               ├── ServerMain.java
│               ├── ClientHandler.java
│               └── ConnectionManager.java
│
└── soc-client/
    ├── pom.xml
    └── src/
        └── main/
            └── java/it/soc/client/
                ├── ClientMain.java
                ├── ConnectionWindow.java
                ├── NetworkClient.java
                └── LobbyWindow.java
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
| Maven | Build and dependency management |
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
| `content` | Contains the message payload. |

### Available Message Types

The shared protocol currently defines the following message types:

| Message | Description |
|---|---|
| `CONNECT` | Connection request |
| `DISCONNECT` | Client disconnection |
| `JOIN_LOBBY` | Request to join the lobby |
| `LOBBY_UPDATE` | Lobby information update |
| `ROLE_ASSIGN` | Role assignment |
| `GAME_START` | Game start notification |
| `GAME_UPDATE` | Game state update |
| `GAME_OVER` | Game completion notification |
| `REQUEST_EVIDENCE` | Request investigation information |
| `SUBMIT_VOTE` | Submit a player vote |
| `ERROR` | Error notification |
| `PING` | Connection check |
| `PONG` | Connection check response |

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

- JDK 21 or later.
- Apache Maven.
- Git.

### Clone the Repository

```bash
git clone https://github.com/liukseek/SOC-Control-Room.git
cd SOC-Control-Room
```

### Build the Project

Run the following command from the project root directory:

```bash
mvn clean install
```

Maven will:

1. Clean previous build artifacts.
2. Compile the project modules.
3. Run the available tests.
4. Resolve dependencies.
5. Install the modules into the local Maven repository.

A successful build should finish with:

```text
BUILD SUCCESS
```

---

## Running the Application

SOC: CONTROL ROOM currently consists of two independently launched components.

### 1. Start the Server

Run the `ServerMain` class located in:

```text
soc-server/src/main/java/it/soc/server/ServerMain.java
```

The server listens for incoming TCP connections on port:

```text
1234
```

Keep the server running while players connect.

### 2. Start a Client

Run the `ClientMain` class located in:

```text
soc-client/src/main/java/it/soc/client/ClientMain.java
```

The connection interface allows the player to specify:

- A player nickname.
- The server IP address.

The default server port is `1234`.

### 3. Connect Multiple Players

Launch additional client instances and connect them to the same server.

For clients running on different computers, use the server computer's network IP address rather than `localhost`.

Make sure the server port is reachable through the local network and that firewall rules allow the connection.

---

## Development Status

**SOC: CONTROL ROOM is actively under development.**

| Component | Status |
|---|---|
| Maven multi-module structure | Completed |
| Shared communication protocol | Completed |
| JSON serialization and deserialization | Completed |
| Protocol unit tests | Implemented |
| TCP server | Implemented |
| Client connection handling | Implemented |
| Player registration | Implemented |
| TCP client | Implemented |
| Connection GUI | Implemented |
| Lobby GUI | Implemented |
| Lobby integration | In progress |
| Real-time lobby synchronization | In progress |
| Game management | Planned |
| Role assignment | Planned |
| Analyst interface | Planned |
| Insider interface | Planned |
| Investigation mechanics | Planned |
| Evidence collection | Planned |
| Random events | Planned |
| Voting system | Planned |
| Game results | Planned |
| Administrative interface | Planned |

---

## Roadmap

The development roadmap is divided into several stages.

### Phase 1 — Core Infrastructure

- [x] Create Maven multi-module structure.
- [x] Implement shared JSON protocol.
- [x] Implement TCP server.
- [x] Implement client networking.
- [x] Create connection interface.
- [x] Create initial lobby interface.
- [ ] Complete lobby synchronization.

### Phase 2 — Game Infrastructure

- [ ] Implement game session management.
- [ ] Implement player state management.
- [ ] Implement role assignment.
- [ ] Implement game phases.
- [ ] Implement server-side game validation.

### Phase 3 — Gameplay

- [ ] Develop Analyst interface.
- [ ] Develop Insider interface.
- [ ] Implement investigation mechanics.
- [ ] Implement evidence collection.
- [ ] Add simulated security events.
- [ ] Implement voting mechanics.
- [ ] Implement victory conditions.

### Phase 4 — Administration and Polish

- [ ] Develop administrative control panel.
- [ ] Add session configuration.
- [ ] Improve error handling.
- [ ] Improve network reliability.
- [ ] Add complete game statistics.
- [ ] Finalize graphical interfaces.

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

No license has been specified yet.

Until a license is added to the repository, all rights remain with the copyright holder.

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
| Status | In Development |

---

**SOC: CONTROL ROOM**

*Investigate. Analyze. Identify.*
