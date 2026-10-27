package com.ilooner.tables4j;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * One sequential stream of table batches. The caller retains ownership of the output stream.
 * Layout is calculated for each batch using its contents and the current terminal dimensions.
 * A session must be used by one thread at a time.
 *
 * @param <T> the type of bean rendered in each row
 */
public final class TableSession<T> {
  private final TableSchema<T> schema;
  private final TableRenderer renderer;
  private final PrintStream output;
  private int printedRows;

  TableSession(TableSchema<T> schema, TableRenderer renderer, PrintStream output) {
    this.schema = Objects.requireNonNull(schema, "schema");
    this.renderer = Objects.requireNonNull(renderer, "renderer");
    this.output = Objects.requireNonNull(output, "output");
  }

  /**
   * Prints the first nonempty batch with headers and appends subsequent batches.
   * Empty batches produce no output and leave the row count unchanged.
   *
   * @param rows the beans to append in iteration order
   */
  public void append(List<? extends T> rows) {
    Objects.requireNonNull(rows, "rows");
    if (rows.isEmpty()) return;
    int nextRowCount = Math.addExact(printedRows, rows.size());
    String rendered = renderer.render(schema.model(rows),
        printedRows == 0 ? OptionalInt.empty() : OptionalInt.of(printedRows));
    output.println(rendered);
    output.flush();
    printedRows = nextRowCount;
  }
}
