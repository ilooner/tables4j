package com.ilooner.tables4j;



record ColumnWidthOptions(int minimumWidth, int padding) {
  static final ColumnWidthOptions NONE = new ColumnWidthOptions(0, 0);
}
