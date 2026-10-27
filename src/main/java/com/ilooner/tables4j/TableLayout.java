package com.ilooner.tables4j;

import java.util.List;

sealed interface TableLayout permits CellWrappingLayout, ColumnWrappingLayout {
  List<PreparedTable> arrange(PreparedTable table, int width, RenderOptions options);
}
