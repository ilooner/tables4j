package com.ilooner.tables4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.ilooner.tables4j.TableText.isContentLine;
import static com.ilooner.tables4j.TableText.lineSeparator;

final class HeaderRepeater {
  private final OptionalInt terminalHeight;

  HeaderRepeater(OptionalInt terminalHeight) {
    this.terminalHeight = terminalHeight;
  }

  String repeat(PreparedTable table, String rendered) {
    if (terminalHeight.isEmpty() || rendered.isEmpty()) {
      return rendered;
    }

    Optional<HeaderLines> headerLines = headerLines(table, rendered);
    if (headerLines.isEmpty()) {
      return rendered;
    }

    return repeatHeadersInRenderedLines(
        rendered,
        headerLines.get(),
        terminalHeight.getAsInt(),
        new HeaderScrollState(0, -1),
        true
    );
  }

  String repeatAppend(
      PreparedTable source,
      String rendered,
      int width,
      int alreadyPrintedRows,
      RenderOptions options,
      int[] widths
  ) {
    if (terminalHeight.isEmpty() || rendered.isEmpty()) {
      return rendered;
    }

    Optional<HeaderLines> headerLines = headerLinesForFixedWidthTable(source, width, options, widths);
    if (headerLines.isEmpty()) {
      return rendered;
    }

    return repeatHeadersInRenderedLines(
        rendered,
        headerLines.get(),
        terminalHeight.getAsInt(),
        scrollStateAfterRows(headerLines.get(), terminalHeight.getAsInt(), alreadyPrintedRows),
        false
    );
  }

  private Optional<HeaderLines> headerLinesForFixedWidthTable(
      PreparedTable source,
      int width,
      RenderOptions options,
      int[] widths
  ) {
    if (!source.hasHeader()) {
      return Optional.empty();
    }

    source.configure(width, options);
    source.useWidths(widths);

    return headerLines(source, source.render(width));
  }

  private String repeatHeadersInRenderedLines(
      String rendered,
      HeaderLines headerLines,
      int terminalHeight,
      HeaderScrollState initialState,
      boolean renderedIncludesHeader
  ) {
    String lineSeparator = lineSeparator(rendered);
    String[] lines = rendered.split("\\R", -1);
    int lineCount = renderedLineCount(lines);
    List<String> result = new ArrayList<>(lineCount);
    int outputLineCount = initialState.lineCount();
    int lastHeaderLineIndex = initialState.lastHeaderLineIndex();

    for (int i = 0; i < lineCount; i++) {
      String line = lines[i];
      boolean originalHeaderLine = renderedIncludesHeader
          && i >= headerLines.headerStartIndex()
          && i < headerLines.headerEndIndexExclusive();

      if (originalHeaderLine) {
        lastHeaderLineIndex = outputLineCount;
      } else if (isBodyContentLine(line, i, headerLines, renderedIncludesHeader)
          && shouldRepeatHeaderBeforeLine(outputLineCount, lastHeaderLineIndex, headerLines, terminalHeight)) {
        lastHeaderLineIndex = addRepeatHeaderLines(result, headerLines);
        outputLineCount += headerLines.repeatHeaderLines().size();
      }

      result.add(line);
      outputLineCount++;
    }

    if (lineCount < lines.length) {
      result.add("");
    }

    return String.join(lineSeparator, result);
  }

  private boolean isBodyContentLine(
      String line,
      int lineIndex,
      HeaderLines headerLines,
      boolean renderedIncludesHeader
  ) {
    return isContentLine(line)
        && (!renderedIncludesHeader || lineIndex > headerLines.headerSeparatorIndex());
  }

  private boolean shouldRepeatHeaderBeforeLine(
      int outputLineCount,
      int lastHeaderLineIndex,
      HeaderLines headerLines,
      int terminalHeight
  ) {
    return lastHeaderLineIndex >= 0
        && outputLineCount + headerLines.leadingRepeatLineCount() - lastHeaderLineIndex >= terminalHeight;
  }

  private int addRepeatHeaderLines(List<String> result, HeaderLines headerLines) {
    int outputLineCount = result.size();
    result.addAll(headerLines.repeatHeaderLines());

    return lastHeaderLineIndexAfterRepeat(outputLineCount, headerLines);
  }

  private int lastHeaderLineIndexAfterRepeat(int outputLineCount, HeaderLines headerLines) {
    return outputLineCount
        + headerLines.leadingRepeatLineCount()
        + headerLines.headerContentLineCount()
        - 1;
  }

  private HeaderScrollState scrollStateAfterRows(
      HeaderLines headerLines,
      int terminalHeight,
      int alreadyPrintedRows
  ) {
    int outputLineCount = headerLines.headerSeparatorIndex() + 1;
    int lastHeaderLineIndex = headerLines.headerEndIndexExclusive() - 1;

    for (int i = 0; i < alreadyPrintedRows; i++) {
      if (shouldRepeatHeaderBeforeLine(outputLineCount, lastHeaderLineIndex, headerLines, terminalHeight)) {
        lastHeaderLineIndex = lastHeaderLineIndexAfterRepeat(outputLineCount, headerLines);
        outputLineCount += headerLines.repeatHeaderLines().size();
      }

      outputLineCount++;
    }

    return new HeaderScrollState(outputLineCount, lastHeaderLineIndex);
  }

  private Optional<HeaderLines> headerLines(PreparedTable table, String rendered) {
    if (!table.hasHeader() || rendered.isEmpty()) {
      return Optional.empty();
    }

    String[] lines = rendered.split("\\R", -1);
    int lineCount = renderedLineCount(lines);
    int headerStartIndex = firstContentLineIndex(lines, lineCount);
    if (headerStartIndex < 0) {
      return Optional.empty();
    }

    int headerEndIndexExclusive = headerStartIndex;
    while (headerEndIndexExclusive < lineCount && isContentLine(lines[headerEndIndexExclusive])) {
      headerEndIndexExclusive++;
    }

    if (headerEndIndexExclusive == headerStartIndex || headerEndIndexExclusive >= lineCount) {
      return Optional.empty();
    }

    List<String> repeatHeaderLines = new ArrayList<>();
    repeatHeaderLines.add(lines[headerEndIndexExclusive]);
    repeatHeaderLines.addAll(Arrays.asList(lines).subList(headerStartIndex, headerEndIndexExclusive));
    repeatHeaderLines.add(lines[headerEndIndexExclusive]);

    return Optional.of(new HeaderLines(
        headerStartIndex,
        headerEndIndexExclusive,
        headerEndIndexExclusive,
        List.copyOf(repeatHeaderLines),
        1,
        headerEndIndexExclusive - headerStartIndex
    ));
  }

  private int firstContentLineIndex(String[] lines, int lineCount) {
    for (int i = 0; i < lineCount; i++) {
      if (isContentLine(lines[i])) {
        return i;
      }
    }

    return -1;
  }

  private int renderedLineCount(String[] lines) {
    if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
      return lines.length - 1;
    }

    return lines.length;
  }


  private record HeaderLines(
      int headerStartIndex,
      int headerEndIndexExclusive,
      int headerSeparatorIndex,
      List<String> repeatHeaderLines,
      int leadingRepeatLineCount,
      int headerContentLineCount
  ) {}

  private record HeaderScrollState(int lineCount, int lastHeaderLineIndex) {}
}
