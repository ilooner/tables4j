package com.ilooner.tables4j;

import de.vandermeer.asciitable.AT_Cell;
import de.vandermeer.asciitable.AT_Row;
import de.vandermeer.asciitable.AT_ColumnWidthCalculator;
import de.vandermeer.skb.interfaces.document.TableRowType;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

final class ColumnWidths implements AT_ColumnWidthCalculator {
  private final PreparedTable table;
  private final RenderOptions options;
  private final int minimumRowNumberWidth;

  ColumnWidths(PreparedTable table, RenderOptions options, OptionalInt largestRowNumber) {
    this.table = table;
    this.options = options;
    this.minimumRowNumberWidth = minimumFirstColumnWidth(table, options, largestRowNumber);
  }

  int[] naturalWidths() {
    int[] minimums = minimumWidths(table.getRawContent(), table.getColNumber());
    return configuredWidths(table.getRawContent(), table.getColNumber(), minimums);
  }

  int[] forWidth(int width) {
    return calculateColumnWidths(table.getRawContent(), table.getColNumber(), width);
  }

  private int[] configuredWidths(LinkedList<AT_Row> rows, int columnCount, int[] minimums) {
    int[] widths = naturalWidths(rows, columnCount, minimums);
    applyColumnWidthOptions(widths, minimums, minimumRowNumberWidth,
        options.minimumColumnWidth(), options.columnWidthPadding(),
        table.columnOptions().stream().map(ColumnOptions::columnWidthOptions).toList());
    return widths;
  }

  @Override
  public int[] calculateColumnWidths(LinkedList<AT_Row> rows, int columnCount, int tableWidth) {
    Objects.requireNonNull(rows, "rows");
    if (columnCount < 1) return new int[0];
    int[] minimums = minimumWidths(rows, columnCount);
    int[] widths = configuredWidths(rows, columnCount, minimums);
    int availableWidth = Math.max(Arrays.stream(minimums).sum(), tableWidth - columnCount - 1);
    shrinkToFit(widths, minimums, availableWidth);
    return widths;
  }

  private int[] minimumWidths(LinkedList<AT_Row> rows, int colNumbers) {
    int[] widths = new int[colNumbers];
    Arrays.fill(widths, 1);

    for (AT_Row row : rows) {
      if (row.getType() != TableRowType.CONTENT || row.getCells() == null) {
        continue;
      }

      for (int i = 0; i < Math.min(row.getCells().size(), colNumbers); i++) {
        AT_Cell cell = row.getCells().get(i);
        int padding = cell.getContext().getPaddingLeft() + cell.getContext().getPaddingRight();
        widths[i] = Math.max(widths[i], padding + 1);
      }
    }

    return widths;
  }

  private int[] naturalWidths(LinkedList<AT_Row> rows, int colNumbers, int[] minimumWidths) {
    int[] widths = Arrays.copyOf(minimumWidths, minimumWidths.length);

    for (AT_Row row : rows) {
      if (row.getType() != TableRowType.CONTENT || row.getCells() == null) {
        continue;
      }

      for (int i = 0; i < Math.min(row.getCells().size(), colNumbers); i++) {
        AT_Cell cell = row.getCells().get(i);
        Object content = cell.getContent();
        if (content == null) {
          continue;
        }

        int padding = cell.getContext().getPaddingLeft() + cell.getContext().getPaddingRight();
        for (String line : normalizedLines(content)) {
          widths[i] = Math.max(widths[i], line.length() + padding);
        }
      }
    }

    return widths;
  }

  private String[] normalizedLines(Object content) {
    return content.toString().replaceAll("\\s+", " ").split("\\R", -1);
  }

  private void applyColumnWidthOptions(
      int[] widths,
      int[] minimumWidths,
      int minimumFirstColumnWidth,
      int minimumColumnWidth,
      int columnWidthPadding,
      List<ColumnWidthOptions> columnWidthOptions
  ) {
    if (widths.length > 0 && minimumFirstColumnWidth > 0) {
      widths[0] = Math.max(widths[0], minimumFirstColumnWidth);
      minimumWidths[0] = Math.max(minimumWidths[0], minimumFirstColumnWidth);
    }

    int firstPaddedColumn = columnWidthOptions.isEmpty() ? 0 : 1;
    for (int i = firstPaddedColumn; i < widths.length; i++) {
      ColumnWidthOptions columnOptions = columnWidthOptionsAt(columnWidthOptions, i);
      int effectiveMinimumWidth = Math.max(minimumColumnWidth, columnOptions.minimumWidth());

      widths[i] = Math.max(widths[i], effectiveMinimumWidth);
      minimumWidths[i] = Math.max(minimumWidths[i], Math.min(effectiveMinimumWidth, widths[i]));
    }

    for (int i = 0; i < widths.length; i++) {
      ColumnWidthOptions columnOptions = columnWidthOptionsAt(columnWidthOptions, i);
      widths[i] += columnWidthPadding + columnOptions.padding();
    }
  }

  private ColumnWidthOptions columnWidthOptionsAt(
      List<ColumnWidthOptions> columnWidthOptions,
      int index
  ) {
    if (index < 0 || index >= columnWidthOptions.size()) {
      return ColumnWidthOptions.NONE;
    }

    return columnWidthOptions.get(index);
  }

  private int minimumFirstColumnWidth(
      PreparedTable table,
      RenderOptions options,
      OptionalInt largestRenderedRowNumber
  ) {
    if (!table.numbered()) {
      return 0;
    }

    int minimumContentWidth = options.minimumRowNumberWidth();
    if (largestRenderedRowNumber.isPresent()) {
      minimumContentWidth = Math.max(
          minimumContentWidth,
          String.valueOf(largestRenderedRowNumber.getAsInt()).length()
      );
    }

    if (minimumContentWidth < 1) {
      return 0;
    }

    return minimumContentWidth + firstColumnPadding(table);
  }

  private int firstColumnPadding(PreparedTable table) {
    return table.getRawContent()
                .stream()
                .filter(row -> row.getType() == TableRowType.CONTENT)
                .findFirst()
                .map(row -> row.getCells().getFirst())
                .map(cell -> cell.getContext().getPaddingLeft() + cell.getContext().getPaddingRight())
                .orElse(0);
  }

  private static void shrinkToFit(int[] widths, int[] minimumWidths, int targetTotal) {
    int excess = Arrays.stream(widths).sum() - targetTotal;

    while (excess > 0) {
      int column = widestShrinkableColumn(widths, minimumWidths);
      if (column < 0) {
        return;
      }

      int nextWidest = nextWidestShrinkableColumnWidth(widths, minimumWidths, column);
      int floor = Math.max(minimumWidths[column], nextWidest);
      int reduction = Math.min(excess, Math.max(1, widths[column] - floor));

      widths[column] -= reduction;
      excess -= reduction;
    }
  }

  private static int widestShrinkableColumn(int[] widths, int[] minimumWidths) {
    int column = -1;

    for (int i = 0; i < widths.length; i++) {
      if (widths[i] <= minimumWidths[i]) {
        continue;
      }

      if (column < 0 || widths[i] > widths[column]) {
        column = i;
      }
    }

    return column;
  }

  private static int nextWidestShrinkableColumnWidth(int[] widths, int[] minimumWidths, int excludedColumn) {
    int result = 0;

    for (int i = 0; i < widths.length; i++) {
      if (i == excludedColumn || widths[i] <= minimumWidths[i]) {
        continue;
      }

      result = Math.max(result, widths[i]);
    }

    return result;
  }

}
