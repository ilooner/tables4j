package com.ilooner.tables4j;

import de.vandermeer.skb.interfaces.document.TableRowStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, reusable definition of the columns in a table of beans.
 *
 * @param <T> the type of bean described by this schema
 */
public final class TableSchema<T> {
  private final List<TableColumn> columns;

  private TableSchema(List<TableColumn> columns) {
    this.columns = List.copyOf(columns);
  }

  /**
   * Discovers columns from declared instance fields with public getters, without expanding
   * nested beans.
   *
   * @param <T> the type of bean described by the schema
   * @param type the bean class to inspect
   * @return a reusable schema for the bean class
   */
  public static <T> TableSchema<T> from(Class<T> type) {
    return from(type, ColumnDiscovery.DIRECT_FIELDS);
  }

  /**
   * Discovers columns from declared instance fields with public getters using the selected
   * nested bean discovery strategy.
   *
   * @param <T> the type of bean described by the schema
   * @param type the bean class to inspect
   * @param discovery the strategy for expanding nested beans
   * @return a reusable schema for the bean class
   */
  public static <T> TableSchema<T> from(Class<T> type, ColumnDiscovery discovery) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(discovery, "discovery");
    return new TableSchema<>(new ReflectionSchemaReader().discoverColumns(type, discovery));
  }

  TableModel model(List<? extends T> objects) {
    Objects.requireNonNull(objects, "objects");
    List<ColumnOptions> options = new ArrayList<>();
    options.add(ColumnOptions.NONE);
    columns.forEach(column -> options.add(column.columnOptions()));

    List<TableModel.Row> rows = new ArrayList<>();
    List<Object> headers = new ArrayList<>();
    headers.add("#");
    columns.forEach(column -> headers.add(column.header()));
    rows.add(TableModel.Row.rule(TableRowStyle.NORMAL));
    rows.add(TableModel.Row.content(headers));
    rows.add(TableModel.Row.rule(TableRowStyle.NORMAL));
    for (int i = 0; i < objects.size(); i++) {
      List<Object> values = new ArrayList<>();
      values.add(i + 1);
      for (TableColumn column : columns) {
        values.add(column.getValue(objects.get(i)));
      }
      rows.add(TableModel.Row.content(values));
    }
    rows.add(TableModel.Row.rule(TableRowStyle.NORMAL));
    return new TableModel(rows, options, true, true);
  }
}
