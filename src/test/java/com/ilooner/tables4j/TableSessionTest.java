package com.ilooner.tables4j;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableSessionTest {
  private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]");
  private static final String APPEND = "\u001B[1A\r\u001B[2K";

  @Test
  void countsRowsAcrossBatchesAndIgnoresEmptyBatches() {
    for (var wrapping : RenderOptions.Wrapping.values()) {
      var bytes = new ByteArrayOutputStream();
      var session = TableRenderer.forWidth(80, RenderOptions.of(wrapping))
          .openSession(TableSchema.from(Row.class), new PrintStream(bytes));

      session.append(List.of());
      assertEquals("", bytes.toString(StandardCharsets.UTF_8));

      session.append(List.of(new Row("first"), new Row("second")));
      assertEquals(List.of(1, 2), rowNumbers(bytes.toString(StandardCharsets.UTF_8)));
      String initial = bytes.toString(StandardCharsets.UTF_8);
      session.append(List.of());
      assertEquals(initial, bytes.toString(StandardCharsets.UTF_8));

      bytes.reset();
      session.append(List.of(new Row("third"), new Row("fourth")));
      String appended = bytes.toString(StandardCharsets.UTF_8);
      assertTrue(appended.startsWith(APPEND));
      assertEquals(List.of(3, 4), rowNumbers(appended));
      assertFalse(appended.contains("#"));
      assertTrue(appended.contains("\u001B[48;5;238m"));
    }
  }

  @Test
  void sessionsFromTheSameRendererHaveIndependentRowNumbers() {
    var renderer = TableRenderer.forWidth(80, RenderOptions.cellWrapping());
    var schema = TableSchema.from(Row.class);
    var firstOutput = new ByteArrayOutputStream();
    var secondOutput = new ByteArrayOutputStream();
    var first = renderer.openSession(schema, new PrintStream(firstOutput));
    var second = renderer.openSession(schema, new PrintStream(secondOutput));

    first.append(List.of(new Row("first"), new Row("second")));
    second.append(List.of(new Row("other")));
    firstOutput.reset();
    first.append(List.of(new Row("third")));

    assertEquals(List.of(3), rowNumbers(firstOutput.toString(StandardCharsets.UTF_8)));
    assertEquals(List.of(1), rowNumbers(secondOutput.toString(StandardCharsets.UTF_8)));
    assertFalse(secondOutput.toString(StandardCharsets.UTF_8).contains(APPEND));
  }

  @Test
  void failedBatchDoesNotAdvanceSessionOrWritePartialOutput() {
    var bytes = new ByteArrayOutputStream();
    var session = TableRenderer.forWidth(80, RenderOptions.cellWrapping())
        .openSession(TableSchema.from(Row.class), new PrintStream(bytes));
    session.append(List.of(new Row("first")));
    bytes.reset();

    assertThrows(RuntimeException.class, () -> session.append(List.of(new Row(null))));
    assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    session.append(List.of(new Row("second")));
    assertEquals(List.of(2), rowNumbers(bytes.toString(StandardCharsets.UTF_8)));
  }

  @Test
  void repeatsHeadersAndRowNumbersInEachWrappedColumnGroup() {
    var bytes = new ByteArrayOutputStream();
    var session = TableRenderer.forWidth(20, RenderOptions.columnWrapping().withTerminalHeight(6))
        .openSession(TableSchema.from(WideRow.class), new PrintStream(bytes));
    session.append(List.of(new WideRow("a", "b"), new WideRow("c", "d"), new WideRow("e", "f")));
    bytes.reset();
    session.append(List.of(new WideRow("g", "h")));

    String appended = bytes.toString(StandardCharsets.UTF_8);
    assertTrue(appended.startsWith(APPEND));
    assertEquals(List.of(4, 4), rowNumbers(appended));
    assertEquals(2, appended.chars().filter(c -> c == '#').count());
  }

  private List<Integer> rowNumbers(String rendered) {
    return ANSI.matcher(rendered).replaceAll("").lines()
        .map(TableText::rowNumber)
        .filter(number -> number.isPresent())
        .map(number -> number.getAsInt())
        .toList();
  }

  private static final class Row {
    private final String value;

    private Row(String value) { this.value = value; }

    public String getValue() {
      if (value == null) throw new IllegalStateException("Unavailable value");
      return value;
    }
  }

  private static final class WideRow {
    @AsciiTableColumn(minimumWidth = 6)
    private final String first;
    @AsciiTableColumn(minimumWidth = 6)
    private final String second;

    private WideRow(String first, String second) {
      this.first = first;
      this.second = second;
    }

    public String getFirst() { return first; }
    public String getSecond() { return second; }
  }
}
