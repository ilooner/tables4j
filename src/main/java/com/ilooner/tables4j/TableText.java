package com.ilooner.tables4j;

import java.util.Optional;
import java.util.OptionalInt;

final class TableText {
  private TableText() {}
  private static final String ROW_NUMBER_HEADER = "#";

  static String lineSeparator(String rendered) {
    return rendered.contains("\r\n") ? "\r\n" : "\n";
  }

  static boolean isHeaderLine(String line) {
    return ROW_NUMBER_HEADER.equals(firstCell(line).orElse("").trim());
  }

  static OptionalInt rowNumber(String line) {
    Optional<String> firstCell = firstCell(line);
    if (firstCell.isEmpty()) {
      return OptionalInt.empty();
    }

    String value = firstCell.get().trim();
    if (value.isEmpty()) {
      return OptionalInt.empty();
    }

    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return OptionalInt.empty();
      }
    }

    return OptionalInt.of(Integer.parseInt(value));
  }

  static Optional<String> firstCell(String line) {
    int firstSeparator = indexOfCellSeparator(line, 0);
    if (firstSeparator < 0) {
      return Optional.empty();
    }

    int secondSeparator = indexOfCellSeparator(line, firstSeparator + 1);
    if (secondSeparator < 0) {
      return Optional.empty();
    }

    return Optional.of(line.substring(firstSeparator + 1, secondSeparator));
  }

  static int indexOfCellSeparator(String line, int fromIndex) {
    int asciiSeparator = line.indexOf('|', fromIndex);
    int boxSeparator = line.indexOf('\u2502', fromIndex);

    if (asciiSeparator < 0) {
      return boxSeparator;
    }

    if (boxSeparator < 0) {
      return asciiSeparator;
    }

    return Math.min(asciiSeparator, boxSeparator);
  }

  static boolean isContentLine(String line) {
    return firstCell(line).isPresent();
  }

}
