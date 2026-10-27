package com.ilooner.tables4j;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.AT_Row;
import de.vandermeer.skb.interfaces.document.TableRowStyle;
import de.vandermeer.skb.interfaces.document.TableRowType;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable table structure. Library rendering objects are created afresh for each render. */
record TableModel(List<Row> rows, List<ColumnOptions> columnOptions, boolean numbered, boolean hasHeader) {
  TableModel {
    rows = List.copyOf(rows);
    columnOptions = List.copyOf(columnOptions);
  }

  static TableModel from(AsciiTable table) {
    Objects.requireNonNull(table, "table");
    List<Row> rows = new ArrayList<>();
    for (AT_Row row : table.getRawContent()) {
      if (row.getType() == TableRowType.CONTENT) {
        rows.add(new Row(null, row.getCells().stream().map(cell -> new Cell(
            cell.getContent(), cell.getContext().getTextAlignment(),
            cell.getContext().getPaddingLeft(), cell.getContext().getPaddingRight()
        )).toList()));
      } else {
        rows.add(Row.rule(row.getStyle()));
      }
    }
    return new TableModel(rows, List.of(), false, hasHeader(rows));
  }

  private static boolean hasHeader(List<Row> rows) {
    boolean seenContent = false;
    for (Row row : rows) {
      if (row.ruleStyle() == null) {
        if (seenContent) return false;
        seenContent = true;
      } else if (seenContent) {
        return true;
      }
    }
    return false;
  }

  PreparedTable prepare(CellFormatter formatter) {
    PreparedTable table = new PreparedTable(columnOptions, numbered, hasHeader);
    for (Row row : rows) {
      if (row.ruleStyle() != null) {
        table.addRule(row.ruleStyle());
        continue;
      }
      Object[] values = new Object[row.cells().size()];
      for (int i = 0; i < values.length; i++) {
        Cell cell = row.cells().get(i);
        values[i] = numbered && i == 0 ? cell.value() : formatter.format(cell.value(), table.columnOptionsAt(i));
      }
      AT_Row renderedRow = table.addRow(values);
      for (int i = 0; i < values.length; i++) {
        Cell cell = row.cells().get(i);
        var context = renderedRow.getCells().get(i).getContext();
        context.setTextAlignment(formatter.alignment(cell.value(), table.columnOptionsAt(i), cell.alignment()));
        context.setPaddingLeft(cell.paddingLeft());
        context.setPaddingRight(cell.paddingRight());
      }
    }
    return table;
  }

  record Cell(Object value, TextAlignment alignment, int paddingLeft, int paddingRight) {}

  record Row(TableRowStyle ruleStyle, List<Cell> cells) {
    Row { cells = List.copyOf(cells); }

    static Row rule(TableRowStyle style) { return new Row(style, List.of()); }

    static Row content(List<Object> values) {
      return new Row(null, values.stream().map(value -> new Cell(value, TextAlignment.LEFT, 0, 0)).toList());
    }
  }
}
