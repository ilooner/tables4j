package com.ilooner.tables4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Customizes the column discovered from a bean field with a public getter. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AsciiTableColumn {
  /**
   * Overrides the column header.
   *
   * @return the header text, or a blank string to use the field name
   */
  String name() default "";

  /**
   * Sets the number of decimal places used to format {@link java.math.BigDecimal} values.
   *
   * @return the number of decimal places, or {@code -1} to use the default formatting
   */
  int decimalPlaces() default -1;

  /**
   * Sets the alignment of values in the column.
   *
   * @return the alignment, or {@link Alignment#DEFAULT} to use the value type's default
   */
  Alignment alignment() default Alignment.DEFAULT;

  /**
   * Enables light green and light red backgrounds for positive and negative numeric values.
   *
   * @return whether numeric values are colored according to their sign
   */
  boolean colorCode() default false;

  /**
   * Sets a minimum column width in character cells.
   *
   * @return the minimum width, or zero to use the content and renderer settings
   */
  int minimumWidth() default 0;

  /**
   * Adds space to the column's calculated width before fitting it to the terminal.
   *
   * @return the additional width in character cells
   */
  int padding() default 0;

  /** Alignment choices for values in a column. */
  enum Alignment {
    /** Preserves the value type's default alignment. */
    DEFAULT,
    /** Aligns values to the left. */
    LEFT,
    /** Aligns values to the right. */
    RIGHT
  }
}
