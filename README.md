# AgriFlight Farm Dashboard

AgriFlight is a desktop dashboard for laying out a farm. You add buildings, rooms, livestock, equipment and crops. Each one has a location, dimensions and a price. The dashboard draws the farm on an 800 × 600 map and animates a drone flying over it.

The project is written in Java with Swing. It implements two design patterns:

- **Singleton**: only one dashboard window can exist.
- **Composite**: items and item-containers are managed through one common interface.

## Repository

GitHub: <https://github.com/NoahSellsCellars/project-pineapple>

## Requirements

- Java Development Kit (JDK) 17 or newer, because the code uses records and pattern matching for `instanceof`.
- [Apache Maven](https://maven.apache.org/) 3.6 or newer, to build and test the project.
- A desktop environment that can open windows. The app won't run on a headless server.

The app itself has no runtime dependencies, because Swing ships with the JDK. JUnit is used only for the tests.

Check your versions with:

```bash
java -version
```

```bash
mvn -version
```

## How to run

The project uses the standard Maven layout. The main class is `com.agriflight.FarmDashboard`.

### Option 1: build with Maven, then run the jar

From the repository root:

```bash
mvn package
```

```bash
java -jar target/agriflight-1.0.0.jar
```

`mvn package` compiles the code, runs the unit tests and builds the jar in `target/`.

### Option 2: compile with `javac` (no Maven)

```bash
javac -d out src/main/java/com/agriflight/*.java
```

```bash
java -cp out com.agriflight.FarmDashboard
```

### Option 3: from an IDE

Open the project folder in IntelliJ IDEA, Eclipse or VS Code. It's detected as a Maven project from `pom.xml`. Then run the `main` method in `FarmDashboard`.

### Running the tests

```bash
mvn test
```

The tests in `FarmItemTest` cover the Singleton, the Composite classes (including `totalPrice()`), property validation, the preloaded test scenario and the drone's scan route.

## Using the dashboard

The window has four areas:

| Area | Purpose |
| --- | --- |
| **Summary bar** (top) | Shows the selected element's name, its own price and its total value (its price plus everything inside it). |
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

## Design patterns

### Singleton: `FarmDashboard`

- `FarmDashboard` is `final` and its constructor is `private`, so no other class can create one.
- A single eager instance is held in `private static final FarmDashboard INSTANCE`.
- `FarmDashboard.getInstance()` is the only way to get the dashboard, and it always returns that same instance.
- `getInstance().open()` creates the `JFrame` the first time it's called. Later calls reuse the same window and bring it to the front. All window work runs on the Swing event dispatch thread.

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

- `FarmMap` is an 800 × 600 `JPanel` that paints the composite tree recursively.
- It listens for changes to the tree model and repaints whenever the farm changes.
- A Swing `Timer` moves the drone along a list of waypoints at a constant speed. A visit flies home → target center → home. A scan flies home, sweeps the farm in rows 50 px apart, then returns home.

## Project structure

```
project-pineapple/
├── pom.xml                                  # Maven build (Java 17, JUnit 5+ tests, runnable jar)
├── README.md
└── src/
    ├── main/java/com/agriflight/
    │   ├── FarmDashboard.java               # Singleton: the dashboard window and controls, entry point
    │   ├── FarmMap.java                     # 800 × 600 map rendering and drone animation
    │   ├── FarmItem.java                    # Composite: component interface
    │   ├── FarmElement.java                 # Shared base class for leaf and composite
    │   ├── Item.java                        # Composite: leaf
    │   ├── ItemContainer.java               # Composite: composite
    │   └── FarmProperties.java              # Element properties and validation
    └── test/java/com/agriflight/
        └── FarmItemTest.java                # Unit tests
```
