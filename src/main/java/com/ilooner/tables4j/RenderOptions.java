package com.ilooner.tables4j;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable rendering configuration; append state belongs to {@link TableSession}.
 *
 * @param wrapping the layout strategy for tables wider than the terminal
 * @param minimumColumnWidth the minimum data column width in character cells
 * @param columnWidthPadding the extra width added to each column before fitting the table
 * @param minimumRowNumberWidth the minimum width of row numbers, excluding cell padding
 * @param terminalHeight the height used for header repetition, or empty to use the renderer's
 *     default behavior
 */
public record RenderOptions(
    Wrapping wrapping,
    int minimumColumnWidth,
    int columnWidthPadding,
    int minimumRowNumberWidth,
    OptionalInt terminalHeight
) {
  /** Layout strategies for tables wider than the terminal. */
  public enum Wrapping {
    /** Keeps columns together and wraps cell contents to fit the available width. */
    CELL_WRAPPING,
    /** Splits columns into groups that fit the available width. */
    COLUMN_WRAPPING
  }

  /**
   * Creates rendering options with nonnegative widths and padding.
   *
   * @param wrapping the layout strategy
   * @param minimumColumnWidth the minimum data column width in character cells
   * @param columnWidthPadding the extra width added to each column before fitting the table
   * @param minimumRowNumberWidth the minimum width of row numbers, excluding cell padding
   * @param terminalHeight the height used for header repetition, or empty to use the renderer's
   *     default behavior
   * @throws IllegalArgumentException if a width or padding is negative, or a supplied height is
   *     less than one
   */
  public RenderOptions {
    Objects.requireNonNull(wrapping, "wrapping");
    Objects.requireNonNull(terminalHeight, "terminalHeight");
    if (minimumColumnWidth < 0 || columnWidthPadding < 0 || minimumRowNumberWidth < 0) {
      throw new IllegalArgumentException("Column widths and padding cannot be negative.");
    }
    if (terminalHeight.isPresent() && terminalHeight.getAsInt() < 1) {
      throw new IllegalArgumentException("terminalHeight must be positive.");
    }
  }

  /**
   * Creates options with the selected layout, zero minimum widths and extra padding, and no
   * explicit terminal height.
   *
   * @param wrapping the layout strategy
   * @return the default options for that strategy
   */
  public static RenderOptions of(Wrapping wrapping) {
    return new RenderOptions(wrapping, 0, 0, 0, OptionalInt.empty());
  }

  /**
   * Creates default options that wrap cell contents to fit the available width.
   *
   * @return the default cell wrapping options
   */
  public static RenderOptions cellWrapping() { return of(Wrapping.CELL_WRAPPING); }

  /**
   * Creates default options that split columns into groups to fit the available width.
   *
   * @return the default column wrapping options
   */
  public static RenderOptions columnWrapping() { return of(Wrapping.COLUMN_WRAPPING); }

  /**
   * Returns a copy with the specified minimum data column width.
   *
   * @param width the nonnegative minimum width in character cells
   * @return the updated options
   * @throws IllegalArgumentException if the width is negative
   */
  public RenderOptions withMinimumColumnWidth(int width) {
    return new RenderOptions(wrapping, width, columnWidthPadding, minimumRowNumberWidth, terminalHeight);
  }

  /**
   * Returns a copy with the specified extra column width before fitting the table.
   *
   * @param padding the nonnegative extra width in character cells
   * @return the updated options
   * @throws IllegalArgumentException if the padding is negative
   */
  public RenderOptions withColumnWidthPadding(int padding) {
    return new RenderOptions(wrapping, minimumColumnWidth, padding, minimumRowNumberWidth, terminalHeight);
  }

  /**
   * Returns a copy with the specified minimum width for row numbers, excluding cell padding.
   *
   * @param width the nonnegative minimum width in character cells
   * @return the updated options
   * @throws IllegalArgumentException if the width is negative
   */
  public RenderOptions withMinimumRowNumberWidth(int width) {
    return new RenderOptions(wrapping, minimumColumnWidth, columnWidthPadding, width, terminalHeight);
  }

  /**
   * Returns a copy with an explicit terminal height for repeating headers.
   *
   * @param height the positive terminal height in character cells
   * @return the updated options
   * @throws IllegalArgumentException if the height is less than one
   */
  public RenderOptions withTerminalHeight(int height) {
    return new RenderOptions(wrapping, minimumColumnWidth, columnWidthPadding, minimumRowNumberWidth, OptionalInt.of(height));
  }

  RenderOptions forSession() {
    return withMinimumRowNumberWidth(Math.max(10, minimumRowNumberWidth));
  }
}
