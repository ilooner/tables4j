# tables4j

**Readable terminal tables from ordinary Java objects.**

Build inventory reports, command-line dashboards, and streaming event logs without
hand-aligning strings. Define your columns once, pass in your beans, and let tables4j
handle layout, formatting, and row numbering.

- **Reusable schemas.** Discover columns from bean getters, including nested objects.
- **Two layouts.** Wrap cell contents or split wide tables into groups of columns.
- **Useful formatting.** Named headers, decimal precision, alignment, thousands
  separators, and green/red backgrounds for signed values.
- **Streaming output.** Append batches with continuous row numbers and optional repeated headers.
- **Terminal awareness.** Detect the current dimensions or supply a fixed size.

[Demos](#see-it-in-action) · [Install](#build-and-install) · [Quick start](#quick-start) ·
[Formatting](#format-your-columns) · [Streaming](#append-rows-as-they-arrive)

## See it in action

These GIFs are recordings of the included Java CLIs running in a terminal. All data
is illustrative; the demos run locally without connecting to external services.
You need **Java 21**; the included Gradle wrapper handles the build.

### Inventory report

Annotated headers, right-aligned numbers, two decimal places, and signed color coding
in a single snapshot.

![Inventory CLI rendering product stock, color-coded changes, and formatted prices](docs/media/inventory.gif)

```sh
./gradlew -q --console=plain demoInventory
```

[Read the example →](examples/src/main/java/com/ilooner/tables4j/examples/InventoryCli.java)

### Streaming deployment log

Events arrive one at a time. A session appends each row to the same table and keeps
the numbering continuous. Fixed column minimums keep this example aligned between batches.

![Deployment CLI appending six events to a table as they arrive](docs/media/streaming.gif)

```sh
./gradlew -q --console=plain demoStreaming
```

[Read the example →](examples/src/main/java/com/ilooner/tables4j/examples/DeploymentCli.java)

### Narrow terminal layouts

The same data at a width of 44 characters: first with wrapped cell contents, then
with columns split into smaller tables. Column groups retain the row-number column.

![CLI comparing cell wrapping and column wrapping at 44 characters wide](docs/media/wrapping.gif)

```sh
./gradlew -q --console=plain demoWrapping
```

[Read the example →](examples/src/main/java/com/ilooner/tables4j/examples/WrappingCli.java)

## Build and install

Build with Java 21. No separate Gradle installation is needed:

```sh
./gradlew clean build
```

This runs the tests, compiles the examples, and generates the library, source, and
Javadoc JARs in `build/libs/`. The examples are separate from the published library.
On Windows, use `gradlew.bat` in place of `./gradlew`.

The project configures Maven coordinates `com.ilooner:tables4j:0.1.0`, but does not
configure a remote publishing repository. To use this checkout in another local project,
publish it to your local Maven repository:

```sh
./gradlew publishToMavenLocal
```

Then add it to a Java 21+ Gradle project:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'com.ilooner:tables4j:0.1.0'
}
```

For Maven, after the same local publication step:

```xml
<dependency>
    <groupId>com.ilooner</groupId>
    <artifactId>tables4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick start

Create a bean, discover its columns, and render a list. This complete example uses
a fixed width so its layout is predictable:

```java
import com.ilooner.tables4j.AsciiTableColumn;
import com.ilooner.tables4j.RenderOptions;
import com.ilooner.tables4j.TableRenderer;
import com.ilooner.tables4j.TableSchema;
import java.util.List;

public class Example {
    public static final class Task {
        @AsciiTableColumn(name = "Task", minimumWidth = 18)
        private final String name;

        @AsciiTableColumn(name = "Attempts", minimumWidth = 8)
        private final int attempts;

        public Task(String name, int attempts) {
            this.name = name;
            this.attempts = attempts;
        }

        public String getName() { return name; }
        public int getAttempts() { return attempts; }
    }

    public static void main(String[] args) {
        var schema = TableSchema.from(Task.class);
        var renderer = TableRenderer.forWidth(80, RenderOptions.cellWrapping());
        var tasks = List.of(new Task("Import orders", 1), new Task("Send receipts", 3));

        System.out.println(renderer.render(schema, tasks));
    }
}
```

Schemas inspect **declared instance fields with public `getX()` or boolean `isX()`
getters**. Static fields and fields without a matching getter are skipped. Use
ordinary beans with these getters; Java record accessors such as `name()` are not
discovered automatically. Null cell values render as empty cells.

Keep a schema around and reuse it for later renders. Rendering a snapshot does not
modify the schema or your rows.

## Format your columns

Place `@AsciiTableColumn` on a bean's **field**:

```java
@AsciiTableColumn(
    name = "Change",
    decimalPlaces = 2,
    alignment = AsciiTableColumn.Alignment.RIGHT,
    colorCode = true,
    minimumWidth = 12,
    padding = 2
)
private final BigDecimal change;
```

| Option | Default | Effect |
| --- | --- | --- |
| `name` | Field name | Changes the column header. |
| `decimalPlaces` | `-1` | Sets the scale of `BigDecimal` values with half-up rounding; `-1` uses default formatting. |
| `alignment` | `DEFAULT` | Uses the type's default alignment, or explicitly selects `LEFT` or `RIGHT`. |
| `colorCode` | `false` | Gives positive values a light green background and negative values a light red background; zero is unchanged. |
| `minimumWidth` | `0` | Sets the minimum column width in character cells. |
| `padding` | `0` | Adds width before the table is fitted to the available space. |

Numbers use thousands separators. `BigDecimal` values omit trailing zeros unless
you specify `decimalPlaces`. Joda Money `BigMoney` values render right-aligned with
two decimal places, without a currency symbol; `CurrencyUnit` renders its currency
code. Joda Time `DateTime` values use the machine's default time zone. Other values
use their string representation.

Bean tables include a `#` column and alternating row backgrounds automatically.

### Nested beans

Use the expanded discovery strategy to turn child properties into columns:

```java
var schema = TableSchema.from(Order.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS);
```

For example, an `Order` with a `customer` bean containing `name` and `city` fields
produces headers such as `customer.name` and `customer.city`. Both levels need
public bean getters. Leaf types stay in a single column, cycles stop expansion,
and a null child produces empty cells for its discovered columns.

## Choose a layout and terminal size

```java
// Keep columns together; wrap long values within their cells.
var compact = TableRenderer.forWidth(60, RenderOptions.cellWrapping());

// Split columns into groups, keeping the row number in each group.
var grouped = TableRenderer.forWidth(60, RenderOptions.columnWrapping());

// Detect dimensions for each render and repeat headers based on terminal height.
var automatic = TableRenderer.forTerminal(RenderOptions.cellWrapping());
```

You can set renderer-wide sizing preferences:

```java
var options = RenderOptions.cellWrapping()
    .withMinimumColumnWidth(8)
    .withColumnWidthPadding(2)
    .withMinimumRowNumberWidth(4)
    .withTerminalHeight(24);
```

`RenderOptions` is immutable; each `with...` method returns a new value. A fixed-width
renderer repeats headers only when you explicitly set a terminal height. Widths are
measured in character cells; configured minimums and table borders can require more
space than a very narrow terminal provides.

Automatic detection checks, in order, the `tables4j.terminalWidth` and
`tables4j.terminalHeight` system properties, the `COLUMNS` and `LINES` environment
variables, and `stty`. It falls back to **80 × 24**. For custom size detection or
tests, pass a `TerminalSizeProvider` to the constructor:

```java
var renderer = new TableRenderer(
    () -> new TerminalSize(100, 30),
    RenderOptions.cellWrapping()
);
```

## Append rows as they arrive

With the `Task` bean from the quick start:

```java
var schema = TableSchema.from(Task.class);
var renderer = TableRenderer.forWidth(80, RenderOptions.cellWrapping());
var session = renderer.openSession(schema, System.out);

session.append(List.of(new Task("Import orders", 1)));
// Later, when another batch arrives:
session.append(List.of(new Task("Send receipts", 3)));
```

The first nonempty batch prints the headers. Subsequent batches append with
continuous row numbering; empty batches do nothing. Use each session from one
thread at a time. The session leaves your output stream open.

Layout is recalculated for every batch. Set column minimum widths large enough for
the expected values when you need stable alignment, as the deployment demo does.
Keep unrelated log messages out of the session's output stream between appends:
appending uses cursor controls to replace the previous table's bottom border.

Output includes ANSI styling, and sessions also use ANSI cursor controls. Use an
ANSI-capable terminal to see the intended output; redirected files contain those
escape sequences.

## Render a manually built table

Already using `de.vandermeer.asciitable.AsciiTable`? Render it directly:

```java
var table = new AsciiTable();
table.addRule();
table.addRow("Service", "Status");
table.addRule();
table.addRow("api", "healthy");
table.addRule();

var renderer = TableRenderer.forWidth(60, RenderOptions.cellWrapping());
System.out.println(renderer.render(table));
```

The renderer adapts the table without mutating the caller's table. The AsciiTable
dependency is exposed through the library's public API.

## Development

```sh
./gradlew clean build --warning-mode all   # Tests, examples, and all JARs
./gradlew test                            # Tests only
./gradlew javadoc                         # API docs in build/docs/javadoc/
./gradlew tasks --group examples          # List the demo CLIs
```

To run the animated demos without pauses, add `-PdemoDelay=0` to their Gradle command.
The default delay between deployment events is 900 ms.

<details>
<summary>Regenerate the terminal recordings and GIFs</summary>

The recorder runs the actual Gradle demo commands in a pseudoterminal, saves their
output and timing as [asciicast v2](https://docs.asciinema.org/manual/asciicast/v2/)
files in [`docs/recordings/`](docs/recordings/), and renders the terminal state with
pyte and Pillow into [`docs/media/`](docs/media/). It preserves the library's ANSI
colors and cursor movement.

On macOS or Linux with Bash, Java 21, and Python 3.10+:

```sh
python3 -m venv build/recording-venv
build/recording-venv/bin/pip install -r scripts/recording-requirements.txt
build/recording-venv/bin/python scripts/record-demos.py
```

The script detects Menlo, DejaVu Sans Mono, or Liberation Mono. To use another
monospace font, pass `--font /path/to/font.ttf`. Python dependencies are only needed
to regenerate the GIFs, not to build or use the Java library.

</details>

## License

Licensed under the [Apache License, Version 2.0](LICENSE) (SPDX: `Apache-2.0`).

Third-party dependencies and the Gradle wrapper retain their original licenses and notices.
