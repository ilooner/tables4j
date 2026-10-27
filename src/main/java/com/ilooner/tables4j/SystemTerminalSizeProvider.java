package com.ilooner.tables4j;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

final class SystemTerminalSizeProvider implements TerminalSizeProvider {
  private static final int DEFAULT_TERMINAL_HEIGHT = 24;
  private static final int DEFAULT_TERMINAL_WIDTH = 80;
  private static final int STTY_TIMEOUT_MS = 200;
  private static final String TERMINAL_HEIGHT_PROPERTY = "tables4j.terminalHeight";
  private static final String TERMINAL_WIDTH_PROPERTY = "tables4j.terminalWidth";

  @Override
  public TerminalSize size() {
    boolean needsWidth = parsePositiveInt(System.getProperty(TERMINAL_WIDTH_PROPERTY)).isEmpty()
        && parsePositiveInt(System.getenv("COLUMNS")).isEmpty();
    boolean needsHeight = parsePositiveInt(System.getProperty(TERMINAL_HEIGHT_PROPERTY)).isEmpty()
        && parsePositiveInt(System.getenv("LINES")).isEmpty();
    Optional<TerminalSize> detected = needsWidth || needsHeight ? sttySize() : Optional.empty();
    return new TerminalSize(terminalWidth(detected), terminalHeight(detected));
  }

  private int terminalHeight(Optional<TerminalSize> detected) {
    OptionalInt propertyHeight = parsePositiveInt(System.getProperty(TERMINAL_HEIGHT_PROPERTY));
    if (propertyHeight.isPresent()) {
      return propertyHeight.getAsInt();
    }

    OptionalInt environmentHeight = parsePositiveInt(System.getenv("LINES"));
    if (environmentHeight.isPresent()) {
      return environmentHeight.getAsInt();
    }

    OptionalInt sttyHeight = detected.isPresent() ? OptionalInt.of(detected.get().height()) : OptionalInt.empty();
    if (sttyHeight.isPresent()) {
      return sttyHeight.getAsInt();
    }

    return DEFAULT_TERMINAL_HEIGHT;
  }

  private int terminalWidth(Optional<TerminalSize> detected) {
    OptionalInt propertyWidth = parsePositiveInt(System.getProperty(TERMINAL_WIDTH_PROPERTY));
    if (propertyWidth.isPresent()) {
      return propertyWidth.getAsInt();
    }

    OptionalInt environmentWidth = parsePositiveInt(System.getenv("COLUMNS"));
    if (environmentWidth.isPresent()) {
      return environmentWidth.getAsInt();
    }

    OptionalInt sttyWidth = detected.isPresent() ? OptionalInt.of(detected.get().width()) : OptionalInt.empty();
    if (sttyWidth.isPresent()) {
      return sttyWidth.getAsInt();
    }

    return DEFAULT_TERMINAL_WIDTH;
  }

  private OptionalInt parsePositiveInt(String value) {
    if (value == null || value.isBlank()) {
      return OptionalInt.empty();
    }

    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  private Optional<TerminalSize> sttySize() {
    Process process = null;

    try {
      process = new ProcessBuilder("sh", "-c", "stty size < /dev/tty")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

      if (!process.waitFor(STTY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }

      if (process.exitValue() != 0) {
        return Optional.empty();
      }

      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String[] parts = output.trim().split("\\s+");
      if (parts.length < 2) {
        return Optional.empty();
      }

      OptionalInt height = parsePositiveInt(parts[0]);
      OptionalInt width = parsePositiveInt(parts[1]);
      return height.isPresent() && width.isPresent()
          ? Optional.of(new TerminalSize(width.getAsInt(), height.getAsInt()))
          : Optional.empty();
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Optional.empty();
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
  }

}
