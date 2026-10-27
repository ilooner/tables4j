package com.ilooner.tables4j;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.AT_Cell;
import de.vandermeer.asciitable.AT_Row;
import de.vandermeer.skb.interfaces.document.TableRowStyle;
import de.vandermeer.skb.interfaces.document.TableRowType;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalInt;

/** Owns a disposable library table and its explicit rendering metadata. */
final class PreparedTable {
  private final AsciiTable table = new AsciiTable();
  private final List<ColumnOptions> columnOptions;
  private final boolean numbered;
  private final boolean hasHeader;

  PreparedTable(List<ColumnOptions> columnOptions, boolean numbered, boolean hasHeader) {
    this.columnOptions = List.copyOf(columnOptions);
    this.numbered = numbered;
    this.hasHeader = hasHeader;
  }

  boolean numbered() { return numbered; }
  int getColNumber() { return table.getColNumber(); }
  LinkedList<AT_Row> getRawContent() { return table.getRawContent(); }
  List<ColumnOptions> columnOptions() { return columnOptions; }
  AT_Row addRow(Object... values) { return table.addRow(values); }
  void addRule(TableRowStyle style) { table.addRule(style); }
  String render(int width) { return table.render(width); }

  ColumnOptions columnOptionsAt(int index) {
    return index < columnOptions.size() ? columnOptions.get(index) : ColumnOptions.NONE;
  }

  void configure(int width, RenderOptions options) {
    table.getContext().setWidth(width);
    table.getRenderer().setCWC(new ColumnWidths(this, options, OptionalInt.empty()));
    for (AT_Row row : getRawContent()) {
      if (row.getType() != TableRowType.CONTENT || row.getCells() == null) continue;
      for (AT_Cell cell : row.getCells()) {
        if (cell.getContext().getTextAlignment() != TextAlignment.RIGHT) {
          cell.getContext().setTextAlignment(TextAlignment.LEFT);
        }
      }
    }
  }

  void useWidths(int[] widths) {
    int[] fixedWidths = widths.clone();
    table.getRenderer().setCWC((rows, columns, width) -> fixedWidths.clone());
  }

  boolean hasHeader() { return hasHeader; }

  PreparedTable appended(int alreadyPrintedRows) {
    PreparedTable appended = new PreparedTable(columnOptions, numbered, false);
    List<AT_Row> rows = bodyRows(getRawContent());
    for (int i = 0; i < rows.size(); i++) {
      AT_Row row = rows.get(i);
      Object[] values = row.getCells().stream().map(AT_Cell::getContent).toArray();
      if (numbered && values.length > 0) values[0] = alreadyPrintedRows + i + 1;
      AT_Row target = appended.addRow(values);
      copyCellTextAlignments(row, target);
    }
    if (!rows.isEmpty()) appended.addRule(lastRuleStyle(getRawContent()));
    return appended;
  }

  int bodyRowCount() { return bodyRows(getRawContent()).size(); }

  PreparedTable columns(ColumnRange range) {
    List<ColumnOptions> options = new ArrayList<>();
    if (!columnOptions.isEmpty()) {
      for (int i = 0; i < range.pinnedColumnCount(); i++) options.add(columnOptionsAt(i));
      for (int i = range.start(); i < range.end(); i++) options.add(columnOptionsAt(i));
    }
    PreparedTable group = new PreparedTable(options, numbered, hasHeader);
    for (AT_Row row : getRawContent()) {
      if (row.getType() == TableRowType.CONTENT) {
        AT_Row target = group.addRow(cellsForRange(row, range));
        copyCellTextAlignmentsForRange(row, target, range);
      } else {
        group.addRule(row.getStyle());
      }
    }
    return group;
  }

  private List<AT_Row> bodyRows(LinkedList<AT_Row> rows) {
    boolean skippedHeader = !hasHeader;
    List<AT_Row> bodyRows = new ArrayList<>();

    for (AT_Row row : rows) {
      if (row.getType() != TableRowType.CONTENT) {
        continue;
      }

      if (!skippedHeader) {
        skippedHeader = true;
        continue;
      }

      bodyRows.add(row);
    }

    return bodyRows;
  }

  private TableRowStyle lastRuleStyle(LinkedList<AT_Row> rows) {
    TableRowStyle style = TableRowStyle.NORMAL;

    for (AT_Row row : rows) {
      if (row.getType() != TableRowType.CONTENT) {
        style = row.getStyle();
      }
    }

    return style;
  }

  private void copyCellTextAlignments(AT_Row sourceRow, AT_Row targetRow) {
    for (int i = 0; i < Math.min(sourceRow.getCells().size(), targetRow.getCells().size()); i++) {
      targetRow.getCells()
               .get(i)
               .getContext()
               .setTextAlignment(sourceRow.getCells().get(i).getContext().getTextAlignment());
    }
  }

  private Object[] cellsForRange(AT_Row row, ColumnRange range) {
    Object[] cells = new Object[range.pinnedColumnCount() + range.end() - range.start()];

    for (int i = 0; i < range.pinnedColumnCount(); i++) {
      cells[i] = row.getCells().get(i).getContent();
    }

    for (int i = range.start(); i < range.end(); i++) {
      cells[range.pinnedColumnCount() + i - range.start()] = row.getCells().get(i).getContent();
    }

    return cells;
  }

  private void copyCellTextAlignmentsForRange(AT_Row sourceRow, AT_Row targetRow, ColumnRange range) {
    int targetColumn = 0;
    for (int i = 0; i < range.pinnedColumnCount(); i++) {
      copyCellTextAlignment(sourceRow, i, targetRow, targetColumn);
      targetColumn++;
    }

    for (int i = range.start(); i < range.end(); i++) {
      copyCellTextAlignment(sourceRow, i, targetRow, targetColumn);
      targetColumn++;
    }
  }

  private void copyCellTextAlignment(
      AT_Row sourceRow,
      int sourceColumn,
      AT_Row targetRow,
      int targetColumn
  ) {
    if (sourceColumn >= sourceRow.getCells().size() || targetColumn >= targetRow.getCells().size()) {
      return;
    }

    targetRow.getCells()
             .get(targetColumn)
             .getContext()
             .setTextAlignment(sourceRow.getCells().get(sourceColumn).getContext().getTextAlignment());
  }
}
