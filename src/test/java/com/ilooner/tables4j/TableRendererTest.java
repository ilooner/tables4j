package com.ilooner.tables4j;


import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableRendererTest {
  private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]");
  private static final String LIGHT_RED_VALUE_BACKGROUND = "\u001B[30;48;5;217m";
  private static final String LIGHT_GREEN_VALUE_BACKGROUND = "\u001B[30;48;5;157m";
  private static final String ZEBRA_ROW_BACKGROUND = "\u001B[48;5;238m";
  private static final String ANSI_RESET = "\u001B[0m";

  @Test
  void renderingDoesNotChangeManualTableContentsOrConfiguration() {
    for (Object value : List.of("long description that wraps", new BigDecimal("1234567.8900"))) {
      AsciiTable table = new AsciiTable();
      table.addRule();
      table.addRow("Name", "Value");
      table.addRule();
      var row = table.addRow("sample", value);
      table.addRule();
      table.getContext().setWidth(123);
      var cell = row.getCells().get(1);
      cell.getContext().setTextAlignment(TextAlignment.CENTER);
      cell.getContext().setPaddingLeft(2);
      var originalCalculator = table.getRenderer().getCWC();

      var narrow = TableRenderer.forWidth(24, RenderOptions.cellWrapping());
      String first = narrow.render(table);
      TableRenderer.forWidth(80, RenderOptions.columnWrapping()).render(table);

      assertEquals(first, narrow.render(table));
      assertEquals(123, table.getContext().getWidth());
      assertSame(originalCalculator, table.getRenderer().getCWC());
      assertSame(value, cell.getContent());
      assertEquals(TextAlignment.CENTER, cell.getContext().getTextAlignment());
      assertEquals(2, cell.getContext().getPaddingLeft());
    }
  }

  @Test
  void usesOneInjectedTerminalSizeSnapshotPerRender() {
    AtomicInteger calls = new AtomicInteger();
    var renderer = new TableRenderer(() -> {
      calls.incrementAndGet();
      return new TerminalSize(40, 6);
    }, RenderOptions.cellWrapping());

    String rendered = renderer.render(TableSchema.from(WideRow.class), wideRows(8));

    assertEquals(1, calls.get());
    assertAllVisibleLinesAtMost(rendered, 40);
    assertHeaderVisibleWithinScrollWindow(rendered, 6);
  }

  @Test
  void tableFromObjectsTreatsCurrencyUnitAsLeafAndDisplaysCurrencyCode() {
    TableModel table = TableSchema.from(CurrencyRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new CurrencyRow(CurrencyUnit.USD)));

    assertEquals(List.of("#", "currency"), rowValues(table, 0));
    assertEquals(CurrencyUnit.USD, rowValues(table, 1).get(1));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);
    assertEquals("USD", cellText(lineWithFirstCell(rendered, "1"), 1).trim());
  }

  @Test
  void renderForWidthFormatsBigMoneyAsRoundedAmountWithoutCurrencyCode() {
    TableModel table = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12.345")))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertTrue(rendered.contains("12.35"), rendered);
    assertTrue(!rendered.contains("USD"), rendered);
    assertTrue(!rendered.contains("12.345"), rendered);
    assertEquals(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12.345")), rowValues(table, 1).get(1));
  }

  @Test
  void renderForWidthFormatsBigMoneyWithThousandsSeparators() {
    TableModel table = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12345.678")))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertEquals("12,345.68", cellText(lineWithFirstCell(rendered, "1"), 1).trim());
  }

  @Test
  void renderForWidthRightAlignsBigMoneyAndPreservesZeroCents() {
    TableModel table = TableSchema.from(AnnotatedMoneyRow.class).model(List.of(new AnnotatedMoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12")))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertEquals("   12.00", cellText(lineWithFirstCell(rendered, "1"), 1));
  }

  @Test
  void renderForWidthFormatsBigDecimalWithoutTrailingZeros() {
    TableModel table = TableSchema.from(DecimalRow.class).model(List.of(new DecimalRow(new BigDecimal("123.4500"))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertTrue(rendered.contains("123.45"), rendered);
    assertTrue(!rendered.contains("123.4500"), rendered);
    assertEquals(new BigDecimal("123.4500"), rowValues(table, 1).get(1));
  }

  @Test
  void renderForWidthFormatsBigDecimalWithThousandsSeparators() {
    TableModel table = TableSchema.from(DecimalRow.class).model(List.of(new DecimalRow(new BigDecimal("1234567.8900"))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertEquals("1,234,567.89", cellText(lineWithFirstCell(rendered, "1"), 1).trim());
  }

  @Test
  void renderForWidthFormatsOtherNumberTypesWithThousandsSeparators() {
    TableModel table = TableSchema.from(NumberRow.class).model(List.of(new NumberRow(
            1234567,
            -9876543210L,
            12345.625,
            new BigInteger("12345678901234567890")
        )));

    String rendered = TableRenderer.forWidth(100, RenderOptions.cellWrapping()).render(table);
    String row = lineWithFirstCell(rendered, "1");

    assertEquals("1,234,567", cellText(row, 1).trim());
    assertEquals("-9,876,543,210", cellText(row, 2).trim());
    assertEquals("12,345.625", cellText(row, 3).trim());
    assertEquals("12,345,678,901,234,567,890", cellText(row, 4).trim());
  }

  @Test
  void tableFromObjectsUsesAnnotatedColumnName() {
    TableModel table = TableSchema.from(AnnotatedDecimalRow.class).model(List.of(new AnnotatedDecimalRow(new BigDecimal("12.345"))));

    assertEquals(List.of("#", "value"), rowValues(table, 0));
  }

  @Test
  void renderForWidthUsesAnnotatedBigDecimalPlacesAndRightAlignment() {
    TableModel table = TableSchema.from(AnnotatedDecimalRow.class).model(List.of(
            new AnnotatedDecimalRow(new BigDecimal("12.345")),
            new AnnotatedDecimalRow(new BigDecimal("12.3"))
        ));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertEquals("   12.35", cellText(lineWithFirstCell(rendered, "1"), 1));
    assertEquals("   12.30", cellText(lineWithFirstCell(rendered, "2"), 1));
  }

  @Test
  void renderForWidthColorCodesAnnotatedPositiveAndNegativeValues() {
    TableModel table = TableSchema.from(AnnotatedDecimalRow.class).model(List.of(
            new AnnotatedDecimalRow(new BigDecimal("1234.345")),
            new AnnotatedDecimalRow(new BigDecimal("-4000")),
            new AnnotatedDecimalRow(BigDecimal.ZERO)
        ));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);
    String positiveLine = lineWithFirstCell(rendered, "1");
    String negativeLine = lineWithFirstCell(rendered, "2");
    String zeroLine = lineWithFirstCell(rendered, "3");

    assertTrue(
        positiveLine.contains(LIGHT_GREEN_VALUE_BACKGROUND + " 1,234.35" + ANSI_RESET),
        positiveLine
    );
    assertTrue(
        negativeLine.contains(LIGHT_RED_VALUE_BACKGROUND + "-4,000.00" + ANSI_RESET),
        negativeLine
    );
    assertTrue(negativeLine.contains(ANSI_RESET + ZEBRA_ROW_BACKGROUND), negativeLine);
    assertTrue(!zeroLine.contains(LIGHT_RED_VALUE_BACKGROUND), zeroLine);
    assertTrue(!zeroLine.contains(LIGHT_GREEN_VALUE_BACKGROUND), zeroLine);
  }

  @Test
  void renderForWidthUsesAnnotatedLeftAlignmentInsteadOfBigMoneyDefault() {
    TableModel table = TableSchema.from(LeftAlignedMoneyRow.class).model(List.of(new LeftAlignedMoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12")))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertEquals("12.00   ", cellText(lineWithFirstCell(rendered, "1"), 1));
  }

  @Test
  void annotatedFormattingIsPreservedForColumnWrappingAndAppendedRows() {
    TableModel table = TableSchema.from(AnnotatedDecimalRow.class).model(List.of(new AnnotatedDecimalRow(new BigDecimal("12.345"))));

    String wrapped = TableRenderer.forWidth(20, RenderOptions.of(RenderOptions.Wrapping.COLUMN_WRAPPING)).render(table);
    String appended = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table, OptionalInt.of(3));

    assertEquals("   12.35", cellText(lineWithFirstCell(wrapped, "1"), 1));
    assertEquals("   12.35", cellText(lineWithFirstCell(appended, "4"), 1));
    assertTrue(wrapped.contains(LIGHT_GREEN_VALUE_BACKGROUND), wrapped);
    assertTrue(appended.contains(LIGHT_GREEN_VALUE_BACKGROUND), appended);
  }

  @Test
  void renderForWidthFormatsPlainAsciiTableBigDecimalWithoutTrailingZeros() {
    AsciiTable table = new AsciiTable();
    table.addRule();
    table.addRow("Amount");
    table.addRule();
    table.addRow(new BigDecimal("1000.0000"));
    table.addRule();

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertTrue(rendered.contains("1,000"), rendered);
    assertTrue(!rendered.contains("1000.0000"), rendered);
  }

  @Test
  void renderForWidthFormatsDateTimeInDefaultMachineTimeZone() {
    DateTimeZone previousDefault = DateTimeZone.getDefault();
    DateTimeZone.setDefault(DateTimeZone.forID("America/New_York"));

    try {
      TableModel table = TableSchema.from(DateTimeRow.class).model(List.of(new DateTimeRow(new DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC))));

      String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

      assertTrue(rendered.contains("01/01/2024 07:00:00.000 AM"), rendered);
      assertTrue(!rendered.contains("2024-01-01T07:00:00.000"), rendered);
      assertTrue(!rendered.contains("2024-01-01T07:00:00.000-05:00"), rendered);
      assertTrue(!rendered.contains("2024-01-01T12:00:00.000Z"), rendered);
    } finally {
      DateTimeZone.setDefault(previousDefault);
    }
  }

  @Test
  void renderForWidthColumnWrappingFormatsBigMoneyAsRoundedAmountWithoutCurrencyCode() {
    TableModel table = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12.344")))));

    String rendered = TableRenderer.forWidth(24, RenderOptions.of(RenderOptions.Wrapping.COLUMN_WRAPPING)).render(table);

    assertTrue(rendered.contains("12.34"), rendered);
    assertTrue(!rendered.contains("USD"), rendered);
    assertTrue(!rendered.contains("12.344"), rendered);
  }

  @Test
  void renderForWidthAppendFormatsBigMoneyAsRoundedAmountWithoutCurrencyCode() {
    TableModel table = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12.345")))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table, OptionalInt.of(1));

    assertTrue(hasFirstCell(rendered, "2"));
    assertTrue(rendered.contains("12.35"), rendered);
    assertTrue(!rendered.contains("USD"), rendered);
    assertTrue(!rendered.contains("12.345"), rendered);
  }

  @Test
  void renderForWidthDoesNotAddThousandsSeparatorsToGeneratedRowNumbers() {
    TableModel table = TableSchema.from(DecimalRow.class).model(List.of(new DecimalRow(new BigDecimal("1234.5"))));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table, OptionalInt.of(999));

    assertTrue(hasFirstCell(rendered, "1000"), rendered);
    assertEquals("1,234.5", cellText(lineWithFirstCell(rendered, "1000"), 1).trim());
  }

  @Test
  void renderForWidthKeepsUnannotatedAppendFriendlyColumnsCompact() {
    TableModel initialTable = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("9.99")))));
    TableModel appendTable = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12345.67")))));
    RenderOptions options = RenderOptions
        .of(RenderOptions.Wrapping.CELL_WRAPPING).withMinimumRowNumberWidth(10);

    String initialRender = TableRenderer.forWidth(80, options).render(initialTable);
    String appendRender = TableRenderer.forWidth(80, options).render(appendTable, OptionalInt.of(9));

    assertTrue(hasFirstCell(initialRender, "1"));
    assertTrue(hasFirstCell(appendRender, "10"));
    assertEquals(10, firstCellWidth(lineWithFirstCell(initialRender, "1")));
    assertEquals(10, firstCellWidth(lineWithFirstCell(appendRender, "10")));
    assertEquals(6, cellWidth(lineWithFirstCell(initialRender, "1"), 1));
    assertEquals(9, cellWidth(lineWithFirstCell(appendRender, "10"), 1));
    assertTrue(initialRender.contains("9.99"), initialRender);
    assertTrue(appendRender.contains("12,345.67"), appendRender);
  }

  @Test
  void renderForWidthKeepsUnannotatedColumnWrappedAppendFriendlyColumnsCompact() {
    TableModel initialTable = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("9.99")))));
    TableModel appendTable = TableSchema.from(MoneyRow.class).model(List.of(new MoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("12345.67")))));
    RenderOptions options = RenderOptions
        .of(RenderOptions.Wrapping.COLUMN_WRAPPING).withMinimumRowNumberWidth(10);

    String initialRender = TableRenderer.forWidth(80, options).render(initialTable);
    String appendRender = TableRenderer.forWidth(80, options).render(appendTable, OptionalInt.of(9));

    assertTrue(hasFirstCell(initialRender, "1"));
    assertTrue(hasFirstCell(appendRender, "10"));
    assertEquals(10, firstCellWidth(lineWithFirstCell(initialRender, "1")));
    assertEquals(10, firstCellWidth(lineWithFirstCell(appendRender, "10")));
    assertEquals(6, cellWidth(lineWithFirstCell(initialRender, "1"), 1));
    assertEquals(9, cellWidth(lineWithFirstCell(appendRender, "10"), 1));
  }

  @Test
  void renderForWidthUsesFieldAnnotationMinimumWidthForAppendedRows() {
    TableModel initialTable = TableSchema.from(AnnotatedMoneyRow.class).model(List.of(new AnnotatedMoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("9.99")))));
    TableModel appendTable = TableSchema.from(AnnotatedMoneyRow.class).model(List.of(new AnnotatedMoneyRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("1234.67")))));

    String initialRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(initialTable);
    String appendRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(appendTable, OptionalInt.of(1));

    assertTrue(hasFirstCell(initialRender, "1"));
    assertTrue(hasFirstCell(appendRender, "2"));
    assertEquals(lastVisibleLineLength(initialRender), lastVisibleLineLength(appendRender));
  }

  @Test
  void renderForWidthUsesFieldAnnotationPadding() {
    TableModel unpaddedTable = TableSchema.from(UnpaddedStringRow.class).model(List.of(new UnpaddedStringRow("x")));
    TableModel paddedTable = TableSchema.from(PaddedStringRow.class).model(List.of(new PaddedStringRow("x")));

    String unpaddedRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(unpaddedTable);
    String paddedRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(paddedTable);

    assertEquals(
        lastVisibleLineLength(unpaddedRender) + 4,
        lastVisibleLineLength(paddedRender)
    );
  }

  @Test
  void renderForWidthUsesExpandedChildFieldAnnotationMinimumWidth() {
    TableModel initialTable = TableSchema.from(AnnotatedChildParentRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new AnnotatedChildParentRow(new AnnotatedChildColumn("x"))));
    TableModel appendTable = TableSchema.from(AnnotatedChildParentRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new AnnotatedChildParentRow(new AnnotatedChildColumn("12345678"))));

    String initialRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(initialTable);
    String appendRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(appendTable, OptionalInt.of(1));

    assertEquals(lastVisibleLineLength(initialRender), lastVisibleLineLength(appendRender));
  }

  @Test
  void renderForWidthUsesParentFieldAnnotationAsExpandedChildDefault() {
    TableModel initialTable = TableSchema.from(ParentAnnotatedChildRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new ParentAnnotatedChildRow(new UnannotatedChildColumn("x"))));
    TableModel appendTable = TableSchema.from(ParentAnnotatedChildRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new ParentAnnotatedChildRow(new UnannotatedChildColumn("123456789"))));

    String initialRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(initialTable);
    String appendRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(appendTable, OptionalInt.of(1));

    assertEquals(lastVisibleLineLength(initialRender), lastVisibleLineLength(appendRender));
  }

  @Test
  void renderForWidthFormatsPlainAsciiTableBigMoneyCells() {
    AsciiTable table = new AsciiTable();
    table.addRule();
    table.addRow("Amount");
    table.addRule();
    table.addRow(BigMoney.of(CurrencyUnit.USD, new BigDecimal("7.995")));
    table.addRule();

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertTrue(rendered.contains("8.00"), rendered);
    assertTrue(!rendered.contains("USD"), rendered);
    assertTrue(!rendered.contains("7.995"), rendered);
  }

  @Test
  void renderForWidthRepeatsRowNumbersInWrappedColumnGroups() {
    TableModel table = TableSchema.from(WideRow.class).model(List.of(
            new WideRow("alpha", "bravo", "charlie"),
            new WideRow("delta", "echo", "foxtrot")
        ));

    String rendered = TableRenderer.forWidth(14, RenderOptions.of(RenderOptions.Wrapping.COLUMN_WRAPPING)).render(table);

    List<String> groups = Arrays.stream(stripAnsi(rendered).split("\\R\\R")).toList();
    List<String> rawGroups = Arrays.stream(rendered.split("\\R\\R")).toList();

    assertTrue(groups.size() > 1);
    assertAllVisibleLinesAtMost(rendered, 14);
    assertTrue(groups.stream().allMatch(group -> hasFirstCell(group, "#")));
    assertTrue(groups.stream().allMatch(group -> hasFirstCell(group, "1")));
    assertTrue(groups.stream().allMatch(group -> hasFirstCell(group, "2")));
    assertTrue(rawGroups.stream()
                        .allMatch(group -> lineWithFirstCell(group, "2").contains("\u001B[48;5;238m")));
  }

  @Test
  void renderForWidthColumnWrappingWrapsSingleOversizedColumn() {
    AsciiTable table = new AsciiTable();
    table.addRule();
    table.addRow("Description");
    table.addRule();
    table.addRow("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz");
    table.addRule();

    String rendered = TableRenderer.forWidth(24, RenderOptions.of(RenderOptions.Wrapping.COLUMN_WRAPPING)).render(table);

    assertAllLinesAtMost(rendered, 24);
    assertTrue(rendered.lines().count() > 5);
  }

  @Test
  void renderForWidthColumnWrappingWrapsSingleOversizedObjectColumn() {
    TableModel table = TableSchema.from(LongTextRow.class).model(List.of(
            new LongTextRow("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz"),
            new LongTextRow("0123456789abcdefghijklmnopqrstuvwxyz0123456789")
        ));

    String rendered = TableRenderer.forWidth(24, RenderOptions.of(RenderOptions.Wrapping.COLUMN_WRAPPING)).render(table);

    assertAllVisibleLinesAtMost(rendered, 24);
    assertTrue(hasFirstCell(rendered, "#"));
    assertTrue(hasFirstCell(rendered, "1"));
    assertTrue(lineWithFirstCell(rendered, "2").contains("\u001B[48;5;238m"));
    assertTrue(rendered.lines().count() > 7);
  }

  @Test
  void renderForWidthZebraStripesEvenNumberedObjectRows() {
    TableModel table = TableSchema.from(WideRow.class).model(List.of(
            new WideRow("alpha", "bravo", "charlie"),
            new WideRow("delta", "echo", "foxtrot"),
            new WideRow("golf", "hotel", "india")
        ));

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertAllVisibleLinesAtMost(rendered, 80);
    assertTrue(!lineWithFirstCell(rendered, "1").contains("\u001B["));
    assertTrue(lineWithFirstCell(rendered, "2").contains("\u001B[48;5;238m"));
    assertTrue(!lineWithFirstCell(rendered, "3").contains("\u001B["));
  }

  @Test
  void renderForWidthRepeatsHeaderBasedOnTerminalHeight() {
    TableModel table = TableSchema.from(WideRow.class).model(wideRows(8));
    RenderOptions options = RenderOptions
        .of(RenderOptions.Wrapping.CELL_WRAPPING)
        .withTerminalHeight(6);

    String rendered = TableRenderer.forWidth(80, options).render(table);

    assertHeaderVisibleWithinScrollWindow(rendered, 6);
  }

  @Test
  void renderForWidthColumnWrappingRepeatsHeaderBasedOnTerminalHeight() {
    TableModel table = TableSchema.from(WideRow.class).model(wideRows(8));
    RenderOptions options = RenderOptions
        .of(RenderOptions.Wrapping.COLUMN_WRAPPING)
        .withTerminalHeight(6);

    String rendered = TableRenderer.forWidth(14, options).render(table);
    List<String> groups = Arrays.stream(rendered.split("\\R\\R")).toList();

    assertAllVisibleLinesAtMost(rendered, 14);
    assertTrue(groups.size() > 1);
    groups.forEach(group -> assertHeaderVisibleWithinScrollWindow(group, 6));
  }

  @Test
  void renderForWidthAppendRepeatsHeaderBasedOnTerminalHeight() {
    TableModel table = TableSchema.from(WideRow.class).model(List.of(new WideRow("delta", "echo", "foxtrot")));
    RenderOptions options = RenderOptions
        .of(RenderOptions.Wrapping.CELL_WRAPPING)
        .withTerminalHeight(6)
        ;

    String rendered = TableRenderer.forWidth(80, options).render(table, OptionalInt.of(3));

    assertTrue(rendered.startsWith("\u001B[1A\r\u001B[2K"));
    assertTrue(hasFirstCell(rendered, "#"));
    assertTrue(hasFirstCell(rendered, "4"));
  }

  @Test
  void renderForWidthColumnWrappingAppendRepeatsHeaderBasedOnTerminalHeight() {
    TableModel table = TableSchema.from(WideRow.class).model(List.of(new WideRow("delta", "echo", "foxtrot")));
    RenderOptions options = RenderOptions
        .of(RenderOptions.Wrapping.COLUMN_WRAPPING)
        .withTerminalHeight(6)
        ;

    String rendered = TableRenderer.forWidth(14, options).render(table, OptionalInt.of(3));
    List<String> groups = Arrays.stream(stripAnsi(rendered).split("\\R\\R")).toList();

    assertTrue(rendered.startsWith("\u001B[1A\r\u001B[2K"));
    assertTrue(groups.size() > 1);
    assertTrue(groups.stream().allMatch(group -> hasFirstCell(group, "#")));
    assertTrue(groups.stream().allMatch(group -> hasFirstCell(group, "4")));
  }

  @Test
  void renderForWidthCanAppendRowsToAlreadyPrintedObjectTable() {
    TableModel initialTable = TableSchema.from(WideRow.class).model(List.of(new WideRow("alpha-alpha-alpha", "bravo", "charlie")));
    TableModel appendTable = TableSchema.from(WideRow.class).model(List.of(new WideRow("delta", "echo", "foxtrot")));

    String fullRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(initialTable);
    String appendRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(appendTable, OptionalInt.of(1));
    String visibleAppendRender = stripAnsi(appendRender);

    assertTrue(hasFirstCell(fullRender, "1"));
    assertTrue(appendRender.startsWith("\u001B[1A\r\u001B[2K"));
    assertTrue(!visibleAppendRender.contains("#"), appendRender);
    assertTrue(!visibleAppendRender.contains("alpha-alpha-alpha"), appendRender);
    assertTrue(visibleAppendRender.contains("delta"), appendRender);
    assertTrue(hasFirstCell(appendRender, "2"));
    assertTrue(lineWithFirstCell(appendRender, "2").contains("\u001B[48;5;238m"));
  }

  @Test
  void renderForWidthOffsetsMultipleAppendedObjectRows() {
    TableModel table = TableSchema.from(WideRow.class).model(List.of(
            new WideRow("delta", "echo", "foxtrot"),
            new WideRow("golf", "hotel", "india")
        ));

    String appendRender = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table, OptionalInt.of(3));

    assertTrue(hasFirstCell(appendRender, "4"));
    assertTrue(hasFirstCell(appendRender, "5"));
    assertTrue(lineWithFirstCell(appendRender, "4").contains("\u001B[48;5;238m"));
    assertTrue(!lineWithFirstCell(appendRender, "5").contains("\u001B[48;5;238m"));
  }

  @Test
  void renderForWidthColumnWrappingCanAppendRowsToAlreadyPrintedObjectTable() {
    TableModel table = TableSchema.from(WideRow.class).model(List.of(
            new WideRow("delta", "echo", "foxtrot")
        ));

    String appendRender = TableRenderer.forWidth(14, RenderOptions.of(RenderOptions.Wrapping.COLUMN_WRAPPING)).render(table, OptionalInt.of(1));
    List<String> groups = Arrays.stream(stripAnsi(appendRender).split("\\R\\R")).toList();

    assertTrue(appendRender.startsWith("\u001B[1A\r\u001B[2K"));
    assertTrue(groups.size() > 1);
    assertAllVisibleLinesAtMost(appendRender, 14);
    assertTrue(groups.stream().noneMatch(group -> hasFirstCell(group, "#")));
    assertTrue(groups.stream().allMatch(group -> hasFirstCell(group, "2")));
  }

  @Test
  void renderForWidthAppendReturnsEmptyWhenThereAreNoUnprintedRows() {
    TableModel table = TableSchema.from(WideRow.class).model(List.of());

    assertEquals("", TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table, OptionalInt.of(1)));
  }

  @Test
  void renderForTerminalCanAppendRowsUsingConfiguredWidthProperty() {
    String previousWidth = System.getProperty("tables4j.terminalWidth");
    System.setProperty("tables4j.terminalWidth", "34");

    try {
      TableModel table = TableSchema.from(LongTextRow.class).model(List.of(
              new LongTextRow("0123456789abcdefghijklmnopqrstuvwxyz")
          ));

      String rendered = TableRenderer.forTerminal(RenderOptions.cellWrapping()).render(table, OptionalInt.of(1));

      assertTrue(rendered.startsWith("\u001B[1A\r\u001B[2K"));
      assertAllVisibleLinesAtMost(rendered, 34);
      assertTrue(hasFirstCell(rendered, "2"));
    } finally {
      if (previousWidth == null) {
        System.clearProperty("tables4j.terminalWidth");
      } else {
        System.setProperty("tables4j.terminalWidth", previousWidth);
      }
    }
  }

  @Test
  void renderForTerminalRepeatsHeaderUsingConfiguredHeightProperty() {
    String previousWidth = System.getProperty("tables4j.terminalWidth");
    String previousHeight = System.getProperty("tables4j.terminalHeight");
    System.setProperty("tables4j.terminalWidth", "80");
    System.setProperty("tables4j.terminalHeight", "6");

    try {
      TableModel table = TableSchema.from(WideRow.class).model(wideRows(8));

      String rendered = TableRenderer.forTerminal(RenderOptions.cellWrapping()).render(table);

      assertHeaderVisibleWithinScrollWindow(rendered, 6);
    } finally {
      restoreSystemProperty("tables4j.terminalWidth", previousWidth);
      restoreSystemProperty("tables4j.terminalHeight", previousHeight);
    }
  }

  @Test
  void renderForWidthWrapsLongCellsWithinRequestedWidth() {
    AsciiTable table = new AsciiTable();
    table.addRule();
    table.addRow("ItemID", "Description");
    table.addRule();
    table.addRow("ITEM-01", "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz");
    table.addRule();

    String rendered = TableRenderer.forWidth(36, RenderOptions.cellWrapping()).render(table);

    assertAllLinesAtMost(rendered, 36);
    assertTrue(rendered.lines().count() > 5);
  }

  @Test
  void renderForWidthPreservesCompactTablesWhenTheyFit() {
    AsciiTable table = new AsciiTable();
    table.addRule();
    table.addRow("ID", "Status");
    table.addRule();
    table.addRow("1", "DONE");
    table.addRule();

    String rendered = TableRenderer.forWidth(80, RenderOptions.cellWrapping()).render(table);

    assertAllLinesAtMost(rendered, 80);
    assertTrue(rendered.lines().allMatch(line -> line.length() < 80));
  }

  @Test
  void renderForWidthHandlesManyColumnsInNarrowTerminals() {
    AsciiTable table = new AsciiTable();
    table.addRule();
    table.addRow("A", "B", "C", "D", "E", "F", "G", "H");
    table.addRule();
    table.addRow("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel");
    table.addRule();

    String rendered = TableRenderer.forWidth(20, RenderOptions.cellWrapping()).render(table);

    assertAllLinesAtMost(rendered, 20);
  }

  @Test
  void renderForWidthCanWrapColumnsWithoutWrappingCellValues() {
    AsciiTable table = new AsciiTable();
    table.addRule();
    table.addRow("Project", "Task Credits", "Category", "Completed Tasks");
    table.addRule();
    table.addRow("Default", "1234567890123456", "USD", "3");
    table.addRule();

    String rendered = TableRenderer.forWidth(34, RenderOptions.of(RenderOptions.Wrapping.COLUMN_WRAPPING)).render(table);

    List<String> lines = rendered.lines().toList();
    int firstColumnGroup = indexOfLineContaining(lines, "Project");
    int secondColumnGroup = indexOfLineContaining(lines, "Category");

    assertAllLinesAtMost(rendered, 34);
    assertTrue(firstColumnGroup >= 0);
    assertTrue(secondColumnGroup > firstColumnGroup);
    assertTrue(lines.stream().anyMatch(line -> line.contains("1234567890123456")));
  }

  @Test
  void renderForTerminalHonorsConfiguredWidthProperty() {
    String previousWidth = System.getProperty("tables4j.terminalWidth");
    System.setProperty("tables4j.terminalWidth", "34");

    try {
      AsciiTable table = new AsciiTable();
      table.addRule();
      table.addRow("Project", "Long Value");
      table.addRule();
      table.addRow("example", "1234567890123456789012345678901234567890");
      table.addRule();

      String rendered = TableRenderer.forTerminal(RenderOptions.cellWrapping()).render(table);

      assertAllLinesAtMost(rendered, 34);
    } finally {
      if (previousWidth == null) {
        System.clearProperty("tables4j.terminalWidth");
      } else {
        System.setProperty("tables4j.terminalWidth", previousWidth);
      }
    }
  }

  private static void assertAllLinesAtMost(String rendered, int maxWidth) {
    rendered.lines().forEach(line -> assertTrue(
        line.length() <= maxWidth,
        () -> "line length " + line.length() + " exceeds " + maxWidth + ": " + line
    ));
  }

  private static void assertAllVisibleLinesAtMost(String rendered, int maxWidth) {
    rendered.lines().forEach(line -> assertTrue(
        stripAnsi(line).length() <= maxWidth,
        () -> "visible line length " + stripAnsi(line).length() + " exceeds " + maxWidth + ": " + line
    ));
  }

  private static void assertHeaderVisibleWithinScrollWindow(String rendered, int terminalHeight) {
    List<Integer> headerLineIndexes = lineIndexesWithFirstCell(rendered, "#");

    assertTrue(headerLineIndexes.size() > 1, rendered);
    for (int i = 1; i < headerLineIndexes.size(); i++) {
      int gap = headerLineIndexes.get(i) - headerLineIndexes.get(i - 1);
      assertTrue(
          gap <= terminalHeight,
          () -> "header gap " + gap + " exceeds terminal height " + terminalHeight + ": " + rendered
      );
    }

    int lastLineIndex = Math.toIntExact(rendered.lines().count()) - 1;
    int lastHeaderLineIndex = headerLineIndexes.get(headerLineIndexes.size() - 1);
    assertTrue(
        lastLineIndex - lastHeaderLineIndex <= terminalHeight,
        () -> "last header is too far from table end: " + rendered
    );
  }

  private static List<Integer> lineIndexesWithFirstCell(String rendered, String value) {
    List<String> lines = rendered.lines().toList();
    List<Integer> indexes = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {
      if (value.equals(firstCell(lines.get(i)))) {
        indexes.add(i);
      }
    }

    return indexes;
  }

  private static int indexOfLineContaining(List<String> lines, String value) {
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains(value)) {
        return i;
      }
    }

    return -1;
  }

  private static boolean hasFirstCell(String rendered, String value) {
    return rendered.lines().anyMatch(line -> value.equals(firstCell(line)));
  }

  private static String lineWithFirstCell(String rendered, String value) {
    return rendered.lines()
                   .filter(line -> value.equals(firstCell(line)))
                   .findFirst()
                   .orElseThrow();
  }

  private static int lastVisibleLineLength(String rendered) {
    return rendered.lines()
                   .reduce((previous, current) -> current)
                   .map(TableRendererTest::stripAnsi)
                   .map(line -> line.replace("\r", ""))
                   .map(String::length)
                   .orElseThrow();
  }

  private static String firstCell(String line) {
    String visibleLine = stripAnsi(line);
    int firstSeparator = indexOfCellSeparator(visibleLine, 0);
    if (firstSeparator < 0) {
      return "";
    }

    int secondSeparator = indexOfCellSeparator(visibleLine, firstSeparator + 1);
    if (secondSeparator < 0) {
      return "";
    }

    return visibleLine.substring(firstSeparator + 1, secondSeparator).trim();
  }

  private static int firstCellWidth(String line) {
    return cellWidth(line, 0);
  }

  private static String cellText(String line, int cellIndex) {
    String visibleLine = stripAnsi(line).replace("\r", "");
    int leftSeparator = -1;
    int searchIndex = 0;

    for (int i = 0; i <= cellIndex; i++) {
      leftSeparator = indexOfCellSeparator(visibleLine, searchIndex);
      if (leftSeparator < 0) {
        return "";
      }
      searchIndex = leftSeparator + 1;
    }

    int rightSeparator = indexOfCellSeparator(visibleLine, searchIndex);
    if (rightSeparator < 0) {
      return "";
    }

    return visibleLine.substring(leftSeparator + 1, rightSeparator);
  }

  private static int cellWidth(String line, int cellIndex) {
    String visibleLine = stripAnsi(line).replace("\r", "");
    int leftSeparator = -1;
    int searchIndex = 0;

    for (int i = 0; i <= cellIndex; i++) {
      leftSeparator = indexOfCellSeparator(visibleLine, searchIndex);
      if (leftSeparator < 0) {
        return 0;
      }
      searchIndex = leftSeparator + 1;
    }

    int rightSeparator = indexOfCellSeparator(visibleLine, searchIndex);
    if (rightSeparator < 0) {
      return 0;
    }

    return visibleLine.substring(leftSeparator + 1, rightSeparator).length();
  }

  private static int indexOfCellSeparator(String line, int fromIndex) {
    int asciiSeparator = line.indexOf('|', fromIndex);
    int boxSeparator = line.indexOf('\u2502', fromIndex);

    if (asciiSeparator < 0) {
      return boxSeparator;
    }

    if (boxSeparator < 0) {
      return asciiSeparator;
    }

    return Math.min(asciiSeparator, boxSeparator);
  }

  private static String stripAnsi(String value) {
    return ANSI_PATTERN.matcher(value).replaceAll("");
  }

  private static void restoreSystemProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }

  private static List<WideRow> wideRows(int count) {
    List<WideRow> rows = new ArrayList<>();

    for (int i = 1; i <= count; i++) {
      rows.add(new WideRow("first-" + i, "second-" + i, "third-" + i));
    }

    return rows;
  }

  private static List<Object> rowValues(TableModel table, int contentRowIndex) {
    return table.rows().stream()
                .filter(row -> row.ruleStyle() == null)
                .skip(contentRowIndex)
                .findFirst()
                .orElseThrow()
                .cells()
                .stream()
                .map(TableModel.Cell::value)
                .toList();
  }

  private static final class WideRow {
    private final String first;
    private final String second;
    private final String third;

    private WideRow(String first, String second, String third) {
      this.first = first;
      this.second = second;
      this.third = third;
    }

    public String getFirst() {
      return first;
    }

    public String getSecond() {
      return second;
    }

    public String getThird() {
      return third;
    }
  }

  private static final class LongTextRow {
    private final String text;

    private LongTextRow(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  private static final class MoneyRow {
    private final BigMoney amount;

    private MoneyRow(BigMoney amount) {
      this.amount = amount;
    }

    public BigMoney getAmount() {
      return amount;
    }
  }

  private static final class CurrencyRow {
    private final CurrencyUnit currency;

    private CurrencyRow(CurrencyUnit currency) {
      this.currency = currency;
    }

    public CurrencyUnit getCurrency() {
      return currency;
    }
  }

  private static final class DecimalRow {
    private final BigDecimal amount;

    private DecimalRow(BigDecimal amount) {
      this.amount = amount;
    }

    public BigDecimal getAmount() {
      return amount;
    }
  }

  private static final class NumberRow {
    private final Integer integerValue;
    private final Long longValue;
    private final Double doubleValue;
    private final BigInteger bigIntegerValue;

    private NumberRow(
        Integer integerValue,
        Long longValue,
        Double doubleValue,
        BigInteger bigIntegerValue
    ) {
      this.integerValue = integerValue;
      this.longValue = longValue;
      this.doubleValue = doubleValue;
      this.bigIntegerValue = bigIntegerValue;
    }

    public Integer getIntegerValue() {
      return integerValue;
    }

    public Long getLongValue() {
      return longValue;
    }

    public Double getDoubleValue() {
      return doubleValue;
    }

    public BigInteger getBigIntegerValue() {
      return bigIntegerValue;
    }
  }

  private static final class AnnotatedDecimalRow {
    @AsciiTableColumn(
        name = "value",
        decimalPlaces = 2,
        minimumWidth = 8,
        alignment = AsciiTableColumn.Alignment.RIGHT,
        colorCode = true
    )
    private final BigDecimal amount;

    private AnnotatedDecimalRow(BigDecimal amount) {
      this.amount = amount;
    }

    public BigDecimal getAmount() {
      return amount;
    }
  }

  private static final class LeftAlignedMoneyRow {
    @AsciiTableColumn(minimumWidth = 8, alignment = AsciiTableColumn.Alignment.LEFT)
    private final BigMoney amount;

    private LeftAlignedMoneyRow(BigMoney amount) {
      this.amount = amount;
    }

    public BigMoney getAmount() {
      return amount;
    }
  }

  private static final class DateTimeRow {
    private final DateTime timestamp;

    private DateTimeRow(DateTime timestamp) {
      this.timestamp = timestamp;
    }

    public DateTime getTimestamp() {
      return timestamp;
    }
  }

  private static final class AnnotatedMoneyRow {
    @AsciiTableColumn(minimumWidth = 8)
    private final BigMoney amount;

    private AnnotatedMoneyRow(BigMoney amount) {
      this.amount = amount;
    }

    public BigMoney getAmount() {
      return amount;
    }
  }

  private static final class UnpaddedStringRow {
    private final String value;

    private UnpaddedStringRow(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private static final class PaddedStringRow {
    @AsciiTableColumn(padding = 4)
    private final String value;

    private PaddedStringRow(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private static final class AnnotatedChildParentRow {
    private final AnnotatedChildColumn child;

    private AnnotatedChildParentRow(AnnotatedChildColumn child) {
      this.child = child;
    }

    public AnnotatedChildColumn getChild() {
      return child;
    }
  }

  private static final class AnnotatedChildColumn {
    @AsciiTableColumn(minimumWidth = 8)
    private final String v;

    private AnnotatedChildColumn(String v) {
      this.v = v;
    }

    public String getV() {
      return v;
    }
  }

  private static final class ParentAnnotatedChildRow {
    @AsciiTableColumn(minimumWidth = 9)
    private final UnannotatedChildColumn child;

    private ParentAnnotatedChildRow(UnannotatedChildColumn child) {
      this.child = child;
    }

    public UnannotatedChildColumn getChild() {
      return child;
    }
  }

  private static final class UnannotatedChildColumn {
    private final String v;

    private UnannotatedChildColumn(String v) {
      this.v = v;
    }

    public String getV() {
      return v;
    }
  }

}
