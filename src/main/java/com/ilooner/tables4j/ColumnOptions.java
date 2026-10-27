package com.ilooner.tables4j;

import java.util.Objects;

record ColumnOptions(
    int minimumWidth,
    int padding,
    int decimalPlaces,
    AsciiTableColumn.Alignment alignment,
    boolean colorCode
) {
  static final ColumnOptions NONE = new ColumnOptions(
      0,
      0,
      -1,
      AsciiTableColumn.Alignment.DEFAULT,
      false
  );

  ColumnOptions {
    Objects.requireNonNull(alignment, "alignment");
  }

  ColumnOptions merge(ColumnOptions that) {
    return new ColumnOptions(
        Math.max(minimumWidth, that.minimumWidth),
        Math.max(padding, that.padding),
        that.decimalPlaces >= 0 ? that.decimalPlaces : decimalPlaces,
        that.alignment == AsciiTableColumn.Alignment.DEFAULT ? alignment : that.alignment,
        colorCode || that.colorCode
    );
  }

  ColumnWidthOptions columnWidthOptions() {
    return new ColumnWidthOptions(minimumWidth, padding);
  }
}
