package com.ilooner.tables4j.examples;

import com.ilooner.tables4j.AsciiTableColumn;
import com.ilooner.tables4j.RenderOptions;
import com.ilooner.tables4j.TableRenderer;
import com.ilooner.tables4j.TableSchema;
import java.math.BigDecimal;
import java.util.List;

import static com.ilooner.tables4j.AsciiTableColumn.Alignment.RIGHT;

/** A snapshot report with annotated headers, number formatting, and signed color coding. */
public final class InventoryCli {
  public static void main(String[] args) {
    var items = List.of(
        new Item("KB-104", "Mechanical keyboard", 1280, 240, "89.9"),
        new Item("DK-200", "USB-C dock", 86, -12, "149"),
        new Item("HP-310", "Studio headphones", 342, 58, "219.5"),
        new Item("MN-400", "4K monitor", 24, -6, "1299")
    );
    var schema = TableSchema.from(Item.class);
    var renderer = TableRenderer.forWidth(78,
        RenderOptions.cellWrapping().withColumnWidthPadding(2));

    System.out.println("\nINVENTORY / warehouse snapshot");
    System.out.println("Sample data | prices in USD | change since yesterday\n");
    System.out.println(renderer.render(schema, items));
    System.out.println("\n4 products. Positive changes are green; negative changes are red.");
  }

  public static final class Item {
    @AsciiTableColumn(name = "SKU")
    private final String sku;
    @AsciiTableColumn(name = "Product")
    private final String product;
    @AsciiTableColumn(name = "Stock", alignment = RIGHT)
    private final int stock;
    @AsciiTableColumn(name = "Change", alignment = RIGHT, colorCode = true)
    private final int change;
    @AsciiTableColumn(name = "Price", decimalPlaces = 2, alignment = RIGHT)
    private final BigDecimal price;

    public Item(String sku, String product, int stock, int change, String price) {
      this.sku = sku;
      this.product = product;
      this.stock = stock;
      this.change = change;
      this.price = new BigDecimal(price);
    }

    public String getSku() { return sku; }
    public String getProduct() { return product; }
    public int getStock() { return stock; }
    public int getChange() { return change; }
    public BigDecimal getPrice() { return price; }
  }
}
