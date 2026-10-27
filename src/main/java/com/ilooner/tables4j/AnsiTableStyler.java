package com.ilooner.tables4j;

import de.vandermeer.asciitable.AT_Row;
import de.vandermeer.skb.interfaces.document.TableRowType;
import org.joda.money.BigMoney;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static com.ilooner.tables4j.TableText.indexOfCellSeparator;
import static com.ilooner.tables4j.TableText.isContentLine;
import static com.ilooner.tables4j.TableText.isHeaderLine;
import static com.ilooner.tables4j.TableText.lineSeparator;

final class AnsiTableStyler {
  private static final String ZEBRA_ROW_BACKGROUND = "\u001B[48;5;238m";
  private static final String LIGHT_RED_VALUE_BACKGROUND = "\u001B[30;48;5;217m";
  private static final String LIGHT_GREEN_VALUE_BACKGROUND = "\u001B[30;48;5;157m";
  private static final String ANSI_RESET = "\u001B[0m";

  String style(PreparedTable table, String rendered) {
    return colorCodeValues(table, zebraStripeRows(table, rendered));
  }

  private String zebraStripeRows(PreparedTable table, String rendered) {
    if (!table.numbered() || rendered.isEmpty()) {
      return rendered;
    }

    String lineSeparator = lineSeparator(rendered);
    String[] lines = rendered.split("\\R", -1);
    List<String> stripedLines = new ArrayList<>(lines.length);
    boolean stripeCurrentRow = false;

    for (String line : lines) {
      OptionalInt rowNumber = TableText.rowNumber(line);
      if (rowNumber.isPresent()) {
        stripeCurrentRow = rowNumber.getAsInt() % 2 == 0;
      } else if (isHeaderLine(line) || !isContentLine(line)) {
        stripeCurrentRow = false;
      }

      if (stripeCurrentRow && isContentLine(line)) {
        stripedLines.add(ZEBRA_ROW_BACKGROUND + line + ANSI_RESET);
      } else {
        stripedLines.add(line);
      }
    }

    return String.join(lineSeparator, stripedLines);
  }

  private String colorCodeValues(PreparedTable table, String rendered) {
    List<ColumnOptions> columnOptions = table.columnOptions();
    if (rendered.isEmpty() || columnOptions.stream().noneMatch(ColumnOptions::colorCode)) {
      return rendered;
    }

    Map<Integer, List<ValueSign>> valueSigns = colorCodedValueSigns(table, columnOptions);
    String lineSeparator = lineSeparator(rendered);
    String[] lines = rendered.split("\\R", -1);
    List<String> coloredLines = new ArrayList<>(lines.length);
    OptionalInt currentRowNumber = OptionalInt.empty();

    for (String originalLine : lines) {
      OptionalInt lineRowNumber = TableText.rowNumber(originalLine);
      if (lineRowNumber.isPresent()) {
        currentRowNumber = lineRowNumber;
      } else if (isHeaderLine(originalLine)) {
        currentRowNumber = OptionalInt.empty();
      }

      String coloredLine = originalLine;
      if (currentRowNumber.isPresent() && isContentLine(originalLine)) {
        List<ValueSign> rowValueSigns = valueSigns.get(currentRowNumber.getAsInt());
        if (rowValueSigns != null) {
          boolean zebraStriped = originalLine.startsWith(ZEBRA_ROW_BACKGROUND);
          for (int i = 0; i < Math.min(columnOptions.size(), rowValueSigns.size()); i++) {
            coloredLine = colorCodeCell(
                coloredLine,
                i,
                rowValueSigns.get(i),
                zebraStriped
            );
          }
        }
      }

      coloredLines.add(coloredLine);
    }

    return String.join(lineSeparator, coloredLines);
  }

  private Map<Integer, List<ValueSign>> colorCodedValueSigns(
      PreparedTable table,
      List<ColumnOptions> columnOptions
  ) {
    Map<Integer, List<ValueSign>> valueSigns = new HashMap<>();

    for (AT_Row row : table.getRawContent()) {
      OptionalInt rowNumber = rowNumber(row);
      if (rowNumber.isEmpty()) {
        continue;
      }

      List<ValueSign> rowValueSigns = new ArrayList<>(columnOptions.size());
      for (int i = 0; i < columnOptions.size(); i++) {
        if (!columnOptions.get(i).colorCode() || i >= row.getCells().size()) {
          rowValueSigns.add(ValueSign.NONE);
          continue;
        }

        rowValueSigns.add(valueSign(row.getCells().get(i).getContent()));
      }
      valueSigns.put(rowNumber.getAsInt(), List.copyOf(rowValueSigns));
    }

    return valueSigns;
  }

  private OptionalInt rowNumber(AT_Row row) {
    if (row.getType() != TableRowType.CONTENT || row.getCells() == null || row.getCells().isEmpty()) {
      return OptionalInt.empty();
    }

    Object content = row.getCells().getFirst().getContent();
    if (content == null) {
      return OptionalInt.empty();
    }

    try {
      int value = Integer.parseInt(content.toString().trim());
      return value >= 0 ? OptionalInt.of(value) : OptionalInt.empty();
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  private ValueSign valueSign(Object value) {
    if (value instanceof BigMoney money) {
      return ValueSign.from(money.getAmount().signum());
    }

    if (value instanceof BigDecimal decimal) {
      return ValueSign.from(decimal.signum());
    }

    if (!(value instanceof Number) && !(value instanceof CharSequence)) {
      return ValueSign.NONE;
    }

    try {
      return ValueSign.from(new BigDecimal(value.toString().trim().replace(",", "")).signum());
    } catch (NumberFormatException e) {
      return ValueSign.NONE;
    }
  }

  private String colorCodeCell(
      String line,
      int cellIndex,
      ValueSign valueSign,
      boolean zebraStriped
  ) {
    String background = switch (valueSign) {
      case NEGATIVE -> LIGHT_RED_VALUE_BACKGROUND;
      case POSITIVE -> LIGHT_GREEN_VALUE_BACKGROUND;
      case NONE, ZERO -> null;
    };
    if (background == null) {
      return line;
    }

    int leftSeparator = -1;
    int searchIndex = 0;
    for (int i = 0; i <= cellIndex; i++) {
      leftSeparator = indexOfCellSeparator(line, searchIndex);
      if (leftSeparator < 0) {
        return line;
      }
      searchIndex = leftSeparator + 1;
    }

    int rightSeparator = indexOfCellSeparator(line, searchIndex);
    if (rightSeparator < 0) {
      return line;
    }

    int cellStart = leftSeparator + 1;
    if (cellStart == rightSeparator) {
      return line;
    }

    String restoreBackground = zebraStriped ? ANSI_RESET + ZEBRA_ROW_BACKGROUND : ANSI_RESET;
    return line.substring(0, cellStart)
        + background
        + line.substring(cellStart, rightSeparator)
        + restoreBackground
        + line.substring(rightSeparator);
  }


  private enum ValueSign {
    NONE,
    NEGATIVE,
    ZERO,
    POSITIVE;

    private static ValueSign from(int signum) {
      if (signum < 0) {
        return NEGATIVE;
      }
      if (signum > 0) {
        return POSITIVE;
      }
      return ZERO;
    }
  }
}
