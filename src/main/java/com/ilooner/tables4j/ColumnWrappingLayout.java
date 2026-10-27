package com.ilooner.tables4j;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import static java.util.Arrays.stream;

final class ColumnWrappingLayout implements TableLayout {
  @Override
  public List<PreparedTable> arrange(PreparedTable table, int width, RenderOptions options) {
    if (table.getColNumber() < 1) return List.of(table);
    return columnRanges(table, width, table.numbered() ? 1 : 0, options).stream()
        .map(table::columns).toList();
  }

  private List<ColumnRange> columnRanges(
      PreparedTable table,
      int width,
      int pinnedColumnCount,
      RenderOptions options
  ) {
    int columnCount = table.getColNumber();
    int[] widths = new ColumnWidths(table, options, OptionalInt.empty()).naturalWidths();
    int terminalWidth = Math.max(1, width);
    int pinnedColumns = Math.min(Math.max(0, pinnedColumnCount), columnCount);

    List<ColumnRange> ranges = new ArrayList<>();
    if (pinnedColumns == columnCount) {
      ranges.add(new ColumnRange(pinnedColumns, columnCount, pinnedColumns));
      return ranges;
    }

    int pinnedContentWidth = stream(widths, 0, pinnedColumns).sum();
    int start = pinnedColumns;

    while (start < columnCount) {
      int end = start;
      int contentWidth = 0;

      while (end < columnCount) {
        int nextColumnCount = pinnedColumns + end - start + 1;
        int nextTableWidth = pinnedContentWidth + contentWidth + widths[end] + nextColumnCount + 1;
        if (nextTableWidth > terminalWidth && end > start) {
          break;
        }

        contentWidth += widths[end];
        end++;

        if (nextTableWidth > terminalWidth) {
          break;
        }
      }

      ranges.add(new ColumnRange(start, end, pinnedColumns));
      start = end;
    }

    return ranges;
  }
}
