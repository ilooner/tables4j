package com.ilooner.tables4j;



/**
 * A snapshot of terminal dimensions, measured in character cells.
 *
 * @param width the positive number of columns
 * @param height the positive number of rows
 */
public record TerminalSize(int width, int height) {
  /**
   * Creates a snapshot with positive dimensions.
   *
   * @param width the positive number of columns
   * @param height the positive number of rows
   * @throws IllegalArgumentException if either dimension is less than one
   */
  public TerminalSize {
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("Terminal dimensions must be positive.");
    }
  }
}
