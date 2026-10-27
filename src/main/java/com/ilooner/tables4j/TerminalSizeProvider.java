package com.ilooner.tables4j;



/** Supplies one consistent snapshot of the current terminal dimensions. */
@FunctionalInterface
public interface TerminalSizeProvider {
  /**
   * Returns the current terminal dimensions as a single snapshot.
   *
   * @return the terminal width and height
   */
  TerminalSize size();
}
