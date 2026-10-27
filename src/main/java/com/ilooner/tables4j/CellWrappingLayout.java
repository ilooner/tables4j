package com.ilooner.tables4j;

import java.util.List;

final class CellWrappingLayout implements TableLayout {
  @Override
  public List<PreparedTable> arrange(PreparedTable table, int width, RenderOptions options) {
    return List.of(table);
  }
}
