package com.ilooner.tables4j.examples;

import com.ilooner.tables4j.AsciiTableColumn;
import com.ilooner.tables4j.RenderOptions;
import com.ilooner.tables4j.TableRenderer;
import com.ilooner.tables4j.TableSchema;
import java.util.List;

/** Simulates deployment events arriving in batches through one table session. */
public final class DeploymentCli {
  public static void main(String[] args) throws InterruptedException {
    var events = List.of(
        new Event("10:42:01", "api", "build", "Image ready"),
        new Event("10:42:02", "worker", "build", "Image ready"),
        new Event("10:42:03", "api", "deploy", "Rolling out"),
        new Event("10:42:04", "worker", "deploy", "Rolling out"),
        new Event("10:42:05", "api", "healthy", "Checks passed"),
        new Event("10:42:06", "worker", "healthy", "Checks passed")
    );
    var renderer = TableRenderer.forWidth(78, RenderOptions.cellWrapping());
    var session = renderer.openSession(TableSchema.from(Event.class), System.out);

    System.out.println("\nDEPLOY / release 2026.10.27");
    System.out.println("Simulated events | one session | continuous row numbers\n");
    for (Event event : events) {
      session.append(List.of(event));
      Thread.sleep(Long.getLong("demo.delay", 900L));
    }
    System.out.println("\nRelease complete. 2 services healthy.");
  }

  public static final class Event {
    @AsciiTableColumn(name = "Time", minimumWidth = 12)
    private final String time;
    @AsciiTableColumn(name = "Service", minimumWidth = 12)
    private final String service;
    @AsciiTableColumn(name = "Stage", minimumWidth = 12)
    private final String stage;
    @AsciiTableColumn(name = "Detail", minimumWidth = 20)
    private final String detail;

    public Event(String time, String service, String stage, String detail) {
      this.time = time;
      this.service = service;
      this.stage = stage;
      this.detail = detail;
    }

    public String getTime() { return time; }
    public String getService() { return service; }
    public String getStage() { return stage; }
    public String getDetail() { return detail; }
  }
}
