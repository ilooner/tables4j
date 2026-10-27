package com.ilooner.tables4j.examples;

import com.ilooner.tables4j.AsciiTableColumn;
import com.ilooner.tables4j.RenderOptions;
import com.ilooner.tables4j.TableRenderer;
import com.ilooner.tables4j.TableSchema;
import java.util.List;

/** Compares both layouts using the same schema, rows, and 44-column width. */
public final class WrappingCli {
  public static void main(String[] args) throws InterruptedException {
    var rows = List.of(
        new Service("api", "us-east-1", "Platform", "Ready for release"),
        new Service("worker", "eu-west-1", "Data", "Waiting for approval")
    );
    var schema = TableSchema.from(Service.class);

    System.out.println("\nLAYOUT / same data, 44 columns\n");
    System.out.println("1. CELL WRAPPING / keep columns together\n");
    System.out.println(TableRenderer.forWidth(44, RenderOptions.cellWrapping())
        .render(schema, rows));
    System.out.flush();
    Thread.sleep(Long.getLong("demo.delay", 900L) * 3);

    System.out.println("\n2. COLUMN WRAPPING / keep values readable\n");
    System.out.println(TableRenderer.forWidth(44, RenderOptions.columnWrapping())
        .render(schema, rows));
  }

  public static final class Service {
    @AsciiTableColumn(name = "Service")
    private final String service;
    @AsciiTableColumn(name = "Region")
    private final String region;
    @AsciiTableColumn(name = "Owner")
    private final String owner;
    @AsciiTableColumn(name = "Notes")
    private final String notes;

    public Service(String service, String region, String owner, String notes) {
      this.service = service;
      this.region = region;
      this.owner = owner;
      this.notes = notes;
    }

    public String getService() { return service; }
    public String getRegion() { return region; }
    public String getOwner() { return owner; }
    public String getNotes() { return notes; }
  }
}
