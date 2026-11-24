# AgriFlight Farm Dashboard

AgriFlight is a desktop dashboard for laying out a farm. You add buildings, rooms, livestock, equipment and crops. Each one has a location, dimensions and a price. The dashboard draws the farm on an 800 × 600 map and animates a drone flying over it.

The project is written in Java with JavaFX. It implements two design patterns:

- **Singleton**: only one dashboard window can exist.
- **Composite**: items and item-containers are managed through one common interface.

## Repository

GitHub: <https://github.com/NoahSellsCellars/project-pineapple>

## Requirements

- Java Development Kit (JDK) 17 or newer, because the code uses records and pattern matching for `instanceof`.
- [Apache Maven](https://maven.apache.org/) 3.6 or newer, to build and test the project.
- A desktop environment that can open windows. The app won't run on a headless server.

Maven downloads JavaFX (version 21) for you, so you don't need to install it separately. JUnit is used only for the tests.

Check your versions with:

```bash
java -version
```

```bash
mvn -version
```

## How to run

The project uses the standard Maven layout. The main class is `com.agriflight.FarmDashboard`.

### Option 1: run with Maven

From the repository root:

```bash
mvn javafx:run
```

### Option 2: build a jar, then run it

```bash
mvn package
```

```bash
java -jar target/agriflight-1.0.0.jar
```

`mvn package` compiles the code, runs the unit tests and builds a jar in `target/` that includes JavaFX for your operating system. Build it on the computer you'll run it on.

### Option 3: from an IDE

Open the project folder in IntelliJ IDEA, Eclipse or VS Code. It's detected as a Maven project from `pom.xml`. Then run the `main` method in `FarmDashboard`, or the Maven goal `javafx:run`.

### Running the tests

```bash
mvn test
```

The tests in `FarmItemTest` cover the Singleton, the Composite classes (including `totalPrice()`), property validation, the preloaded test scenario, keeping the tree in step with the farm, and the drone's flights. The tests don't start the JavaFX toolkit, so they run without a display. `FarmApiServerTest` starts the drone API on a free port and checks every endpoint.

## Using the dashboard

The window has four areas:

| Area | Purpose |
| --- | --- |
| **Summary bar** (top) | Shows the selected element's name, its own price and its total value (its price plus everything inside it). The right side shows the drone API's address. |
| **Hierarchy tree** (left) | Shows the farm as a tree of item-containers and items. Select a node to act on it. |
| **Farm map** (right, 800 × 600 px) | Draws every item and item-container at its location and scaled to its length and width. Containers are drawn as outlines, items are filled, and the selected element is highlighted in blue. |
| **Controls** (bottom) | Buttons for managing items and flying the drone. |

### Controls

| Button | Use case | What it does |
| --- | --- | --- |
| **Add Item** | Manage farm items | Adds an item (for example a cow or a tractor) to the selected item-container. |
| **Add Container** | Manage farm items / Create farm layout | Adds an item-container (for example a barn or a room) to the selected item-container. |
| **Edit ▾** | Manage farm items | Opens a menu with a separate command for each property of the selected element: **Change Name**, **Change Price**, **Change Location-X**, **Change Location-Y**, **Change Length**, **Change Width** and **Change Height**. Each command asks for the new value and rejects invalid input. The same commands are in the window's **Edit** menu. |
| **Delete** | Manage farm items | Deletes the selected item, or the selected item-container and everything inside it. The root `Farm` can't be deleted. |
| **Visit Selected** | Visit item | Animates the drone flying from its home position to the selected item or item-container and back. |
| **Scan Farm** | Scan farm | Animates the drone flying back and forth across the entire farm, then returning home. |

The **Add Item** and **Add Container** buttons are enabled only when an item-container is selected.

### Drone setup

Drone flights start and end at the **Drone** item inside the **Command Center** item-container, which sits directly under the root. If either one is missing, the flight buttons show a message asking you to add them. Any change to the farm while the drone is flying cancels the flight.

### Coordinate system

The map uses screen coordinates. The origin `(0, 0)` is the **top-left** corner. **x** increases to the right and **y** increases **downward**. An element's `location-x` and `location-y` give its top-left corner. Its `length` is drawn horizontally and its `width` vertically. `height` is stored but not drawn, because the map is a top-down view.

## Test scenario

The dashboard opens with the assignment's test scenario already loaded:

```
Farm (root)
├── Barn                      (item-container)
│   ├── Livestock Area        (item-container)
│   │   └── Cow               (item)
│   └── Milk Storage          (item)
├── Storage Building          (item-container)
│   ├── Tractor               (item)
│   └── Tiller                (item)
├── Command Center            (item-container)
│   └── Drone                 (item)
└── Soy Crop                  (item)
```

The sample elements have example prices, so the whole farm has a total value of $197,500.00. The map shows all of these as soon as the window opens. To build the scenario by hand instead, delete the sample elements, then use **Add Container** and **Add Item** with the root or a container selected.

## Drone API

The dashboard runs a small HTTP + JSON API so other programs, such as the code that flies the physical drone, can read and change the farm while the dashboard is open. Changes made through the API show up in the tree and on the map right away, and changes made in the dashboard show up in the API.

- Address: `http://127.0.0.1:8080/api`. The top-right corner of the window shows the address, and the app prints it when it starts.
- It listens on this computer only (`127.0.0.1`), so other devices on the network can't reach it.
- To use a different port, run with `-Dagriflight.api.port=9090`. To turn it off, run with `-Dagriflight.api=off`:

```bash
java -Dagriflight.api.port=9090 -jar target/agriflight-1.0.0.jar
```

- If the port is already taken, the dashboard still opens, and the top bar says the API is unavailable.
- The API is not part of the graded design patterns. It uses the same `FarmItem` classes and validation as the dashboard.

### Endpoints

| Method and path | What it does |
| --- | --- |
| `GET /api` | Lists the endpoints. |
| `GET /api/farm` | The whole farm as a tree. Each element has `id`, `type` (`item` or `container`), `name`, `price`, `totalPrice`, `x`, `y`, `length`, `width`, `height` and `children`. |
| `GET /api/items/{id}` | One element and everything inside it. |
| `PATCH /api/items/{id}` | Changes any of `name`, `price`, `x`, `y`, `length`, `width`, `height`. Fields you leave out stay the same. |
| `DELETE /api/items/{id}` | Deletes an element and everything inside it. The root farm can't be deleted. |
| `POST /api/items/{id}/children` | Adds an element to a container. Requires `type` (`item` or `container`) and `name`. Other fields default to `price` 0, `x` 0, `y` 0, `length` 40, `width` 30, `height` 1. |
| `GET /api/drone` | The drone's state: `flying`, `status`, `home`, `position` (the simulated drone), `reportedPosition` (the real drone, see below), `route` (the waypoints of the current or last flight) and `nextWaypoint`. |
| `PUT /api/drone/position` | Reports where the real drone is, as `{"x": …, "y": …}` in map coordinates. The map shows it as an orange "Live drone" marker. |
| `DELETE /api/drone/position` | Removes the "Live drone" marker. |
| `POST /api/drone/visit` | Starts a visit flight to an element, as `{"id": …}`, and returns the drone's state including the route. |
| `POST /api/drone/scan` | Starts a scan of the whole farm and returns the drone's state including the route. |

Ids are numbered depth-first starting at 1 when the dashboard opens (in the test scenario: 1 Farm, 2 Barn, 3 Livestock Area, 4 Cow, …). They stay the same while the dashboard is running, and new elements get the next free number. Get them from `GET /api/farm` rather than hard-coding them.

Coordinates use the map's system: the origin is the top-left corner, x increases to the right and y increases downward, in map pixels (0–800 by 0–600).

### Responses and errors

Successful requests return `200` (or `201` after adding an element, `204` after deleting one) with JSON. Errors return a JSON object with an `error` message, using the same validation messages as the dashboard:

| Status | Meaning |
| --- | --- |
| `400` | Invalid JSON, an unknown field, or an invalid value (for example `{"error": "Price can't be negative."}`) |
| `404` | No element with that id, or no such endpoint |
| `405` | Wrong method for the endpoint |
| `409` | The request conflicts with the farm's state, such as deleting the root, adding to an item, or starting a flight while one is running |

### Examples

```bash
curl http://127.0.0.1:8080/api/farm
```

```bash
curl -X PATCH http://127.0.0.1:8080/api/items/4 -d '{"name": "Bessie", "price": 1750}'
```

```bash
curl -X POST http://127.0.0.1:8080/api/items/2/children -d '{"type": "item", "name": "Hay Bales", "price": 300, "x": 40, "y": 240, "length": 120, "width": 60}'
```

```bash
curl -X PUT http://127.0.0.1:8080/api/drone/position -d '{"x": 560, "y": 420}'
```

```bash
curl -X POST http://127.0.0.1:8080/api/drone/scan
```

The same calls from Python, using only the standard library:

```python
import json
from urllib.request import Request, urlopen

API = "http://127.0.0.1:8080/api"

def call(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    with urlopen(Request(API + path, data=data, method=method)) as response:
        text = response.read()
        return json.loads(text) if text else None

route = call("POST", "/drone/scan")["route"]   # waypoints to fly the real drone along
for point in route:
    # ... fly the real drone to point["x"], point["y"] ...
    call("PUT", "/drone/position", {"x": point["x"], "y": point["y"]})
```

An error status raises `urllib.error.HTTPError`. Its `.read()` returns the JSON with the `error` message.

## Design patterns

### Singleton: `FarmDashboard`

- `FarmDashboard` is `final` and its constructor is `private`, so no other class can create one.
- A single eager instance is held in `private static final FarmDashboard INSTANCE`.
- `FarmDashboard.getInstance()` is the only way to get the dashboard, and it always returns that same instance.
- `getInstance().open()` creates the JavaFX `Stage` the first time it's called. Later calls reuse the same window and bring it to the front. All window work runs on the JavaFX application thread.
- JavaFX creates its `Application` object itself through a public constructor, so the singleton can't be that class. `FarmApp extends Application` is a two-line launcher whose `start(...)` calls `FarmDashboard.getInstance().open()`. `FarmDashboard.main` starts JavaFX with `Application.launch(FarmApp.class)`.

### Composite: `FarmItem`, `Item`, `ItemContainer`

| Class | Role in the pattern | Responsibility |
| --- | --- | --- |
| `FarmItem` (interface) | Component | Common interface: `properties()`, `update(...)`, `children()`, `totalPrice()` |
| `FarmElement` (abstract) | Shared base | Stores the `FarmProperties` and implements property access and updates for both leaves and composites |
| `Item` | Leaf | A single farm item. `children()` always returns an empty list, and `totalPrice()` returns its own price. |
| `ItemContainer` | Composite | Holds a list of `FarmItem` children, with `add(...)` and `remove(...)`. `totalPrice()` returns its own price plus the `totalPrice()` of each child. |
| `FarmProperties` (record) | Value object | `name`, `price`, `x`, `y`, `length`, `width`, `height`, plus input validation |

`totalPrice()` is the pattern's key operation. A client calls it the same way on any element, and a container answers by delegating to its children, recursively, without the client knowing whether it holds a leaf or a whole subtree. The summary bar uses it to show the value of whatever is selected.

The tree view, the change commands and the map rendering all work through the `FarmItem` interface. The map draws any element and then recurses into its `children()`, so items and containers are handled the same way.

### Visualization and animation: `FarmMap`

- `FarmMap` is an 800 × 600 JavaFX `Canvas` that draws the composite tree recursively.
- `FarmEditor` notifies it after every change, and it redraws whenever the farm changes.
- A JavaFX `AnimationTimer` moves the drone along a list of waypoints at a constant speed. A visit flies home → target center → home. A scan flies home, sweeps the farm in rows 50 px apart, then returns home.

## Project structure

```
project-pineapple/
├── pom.xml                                  # Maven build (Java 17, JavaFX 21, JUnit tests, runnable jar)
├── README.md
└── src/
    ├── main/java/com/agriflight/
    │   ├── FarmDashboard.java               # Singleton: the dashboard window and controls, entry point
    │   ├── FarmApp.java                     # JavaFX Application that opens the singleton dashboard
    │   ├── FarmMap.java                     # 800 × 600 map rendering and drone animation
    │   ├── FarmItem.java                    # Composite: component interface
    │   ├── FarmElement.java                 # Shared base class for leaf and composite
    │   ├── Item.java                        # Composite: leaf
    │   ├── ItemContainer.java               # Composite: composite
    │   ├── FarmProperties.java              # Element properties and validation
    │   ├── FarmEditor.java                  # Applies changes to the farm and the tree view together
    │   ├── FarmApiServer.java               # Drone API (local HTTP + JSON)
    │   └── Json.java                        # Small JSON reader and writer for the API
    └── test/java/com/agriflight/
        ├── FarmItemTest.java                # Pattern, validation and scenario tests
        └── FarmApiServerTest.java           # Drone API tests
```
