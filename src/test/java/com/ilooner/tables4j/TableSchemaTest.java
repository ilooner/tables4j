package com.ilooner.tables4j;

import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableSchemaTest {
  @Test
  void tableFromObjectsUsesDirectFieldsByDefault() {
    TableModel table = TableSchema.from(ParentRow.class).model(List.of(new ParentRow("row-1", new ChildRow("child", 8))));

    assertEquals(List.of("#", "id", "child"), rowValues(table, 0));
    assertEquals(1, rowValues(table, 1).get(0));
  }

  @Test
  void tableFromObjectsCanExpandChildObjectColumns() {
    TableModel table = TableSchema.from(ParentRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new ParentRow("row-1", new ChildRow("child", 8))));

    assertEquals(List.of("#", "id", "child.name", "child.count"), rowValues(table, 0));
    assertEquals(List.of(1, "row-1", "child", 8), rowValues(table, 1));
  }

  @Test
  void tableFromObjectsUsesBlankValuesForNullExpandedChildObjects() {
    TableModel table = TableSchema.from(ParentRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new ParentRow("row-1", null)));

    assertEquals(List.of("#", "id", "child.name", "child.count"), rowValues(table, 0));
    assertEquals(List.of(1, "row-1", "", ""), rowValues(table, 1));
  }

  @Test
  void tableFromObjectsDoesNotExpandLeafValueTypes() {
    BigMoney money = BigMoney.of(CurrencyUnit.USD, new BigDecimal("45.67"));
    DateTime dateTime = new DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC);
    TableModel table = TableSchema.from(LeafValueRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS).model(List.of(new LeafValueRow(
            true,
            Boolean.FALSE,
            7,
            "label",
            new BigDecimal("12.34"),
            money,
            dateTime
        )));

    assertEquals(
        List.of("#", "primitiveFlag", "flag", "count", "label", "amount", "money", "dateTime"),
        rowValues(table, 0)
    );
    assertEquals(
        List.of(1, true, Boolean.FALSE, 7, "label", new BigDecimal("12.34"), money, dateTime),
        rowValues(table, 1)
    );
  }

  @Test
  void schemaCanBeReusedWithoutChangingEarlierSnapshots() {
    var schema = TableSchema.from(ChildRow.class);
    var input = new ArrayList<>(List.of(new ChildRow("first", 1)));
    var first = schema.model(input);
    input.clear();
    var second = schema.model(List.of(new ChildRow("second", 2)));

    assertEquals(List.of(1, "first", 1), rowValues(first, 1));
    assertEquals(List.of(1, "second", 2), rowValues(second, 1));
    assertThrows(UnsupportedOperationException.class, () -> first.rows().clear());
    assertThrows(UnsupportedOperationException.class, () -> first.columnOptions().clear());
  }

  @Test
  void expansionStopsAtRecursiveTypesAndSkipsFieldsWithoutGetters() {
    var schema = TableSchema.from(RecursiveRow.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS);
    var model = schema.model(List.of(new RecursiveRow()));

    assertEquals(List.of("#", "name", "child"), rowValues(model, 0));
    assertEquals(List.of(1, "root", ""), rowValues(model, 1));
  }

  private List<Object> rowValues(TableModel table, int index) {
    return table.rows().stream().filter(row -> row.ruleStyle() == null)
        .skip(index).findFirst().orElseThrow().cells().stream()
        .map(TableModel.Cell::value).toList();
  }

  private static final class RecursiveRow {
    private static final String CONSTANT = "ignored";
    private final String name = "root";
    private final RecursiveRow child = null;
    private final String hidden = "ignored";

    public String getName() { return name; }
    public RecursiveRow getChild() { return child; }
    public static String getCONSTANT() { return CONSTANT; }
  }

  private static final class ParentRow {
    private final String id;
    private final ChildRow child;

    private ParentRow(String id, ChildRow child) {
      this.id = id;
      this.child = child;
    }

    public String getId() {
      return id;
    }

    public ChildRow getChild() {
      return child;
    }
  }

  private static final class ChildRow {
    private final String name;
    private final int count;

    private ChildRow(String name, int count) {
      this.name = name;
      this.count = count;
    }

    public String getName() {
      return name;
    }

    public int getCount() {
      return count;
    }
  }

  private static final class LeafValueRow {
    private final boolean primitiveFlag;
    private final Boolean flag;
    private final Integer count;
    private final String label;
    private final BigDecimal amount;
    private final BigMoney money;
    private final DateTime dateTime;

    private LeafValueRow(
        boolean primitiveFlag,
        Boolean flag,
        Integer count,
        String label,
        BigDecimal amount,
        BigMoney money,
        DateTime dateTime
    ) {
      this.primitiveFlag = primitiveFlag;
      this.flag = flag;
      this.count = count;
      this.label = label;
      this.amount = amount;
      this.money = money;
      this.dateTime = dateTime;
    }

    public boolean isPrimitiveFlag() {
      return primitiveFlag;
    }

    public Boolean getFlag() {
      return flag;
    }

    public Integer getCount() {
      return count;
    }

    public String getLabel() {
      return label;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public BigMoney getMoney() {
      return money;
    }

    public DateTime getDateTime() {
      return dateTime;
    }
  }
}
