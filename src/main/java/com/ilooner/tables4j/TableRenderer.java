package com.ilooner.tables4j;

import de.vandermeer.asciitable.AsciiTable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Renders table snapshots without changing the schema, rows, or caller-owned library tables. */
public final class TableRenderer {
  private static final String MOVE_TO_PREVIOUS_LINE_AND_CLEAR = "\u001B[1A\r\u001B[2K";
  private final TerminalSizeProvider terminal;
  private final RenderOptions options;
  private final boolean detectHeight;
  private final TableLayout layout;
  private final CellFormatter formatter = new CellFormatter();
  private final AnsiTableStyler styler = new AnsiTableStyler();

  /**
   * Creates a renderer that reads terminal dimensions for each render. The terminal height is
   * used for header repetition unless the options specify a height.
   *
   * @param terminal the provider of terminal dimensions
   * @param options the rendering configuration
   */
  public TableRenderer(TerminalSizeProvider terminal, RenderOptions options) {
    this(terminal, options, true);
  }

  private TableRenderer(TerminalSizeProvider terminal, RenderOptions options, boolean detectHeight) {
    this.terminal = Objects.requireNonNull(terminal, "terminal");
    this.options = Objects.requireNonNull(options, "options");
    this.detectHeight = detectHeight;
    this.layout = switch (options.wrapping()) {
      case CELL_WRAPPING -> new CellWrappingLayout();
      case COLUMN_WRAPPING -> new ColumnWrappingLayout();
    };
  }

  /**
   * Creates a renderer that automatically detects terminal dimensions.
   *
   * @param options the rendering configuration
   * @return a renderer using the current terminal dimensions
   */
  public static TableRenderer forTerminal(RenderOptions options) {
    return new TableRenderer(new SystemTerminalSizeProvider(), options);
  }

  /**
   * Renders at a fixed width, repeating headers only when a height is explicitly configured.
   *
   * @param width the positive terminal width in character cells
   * @param options the rendering configuration
   * @return a renderer using the specified width
   * @throws IllegalArgumentException if the width is less than one
   */
  public static TableRenderer forWidth(int width, RenderOptions options) {
    TerminalSize size = new TerminalSize(width, 24);
    return new TableRenderer(() -> size, options, false);
  }

  /**
   * Renders a snapshot of bean rows using a reusable schema.
   *
   * @param <T> the type of bean described by the schema
   * @param schema the definition of the table's columns
   * @param rows the beans to render in iteration order
   * @return the rendered table, including ANSI styling
   */
  public <T> String render(TableSchema<T> schema, List<? extends T> rows) {
    return render(schema.model(rows));
  }

  /**
   * Adapts a manually constructed library table without mutating it.
   *
   * @param table the table to render
   * @return the rendered table, including ANSI styling
   */
  public String render(AsciiTable table) {
    return render(TableModel.from(table));
  }

  /**
   * Opens a session for appending batches with continuous row numbering. The caller retains
   * ownership of the output stream.
   *
   * @param <T> the type of bean described by the schema
   * @param schema the definition of the table's columns
   * @param output the stream that receives rendered batches
   * @return a new session to be used by one thread at a time
   */
  public <T> TableSession<T> openSession(TableSchema<T> schema, PrintStream output) {
    return new TableSession<>(schema,
        new TableRenderer(terminal, options.forSession(), detectHeight), output);
  }

  String render(TableModel model) {
    return render(model, OptionalInt.empty());
  }

  String render(TableModel model, OptionalInt alreadyPrintedRows) {
    TerminalSize size = terminal.size();
    RenderOptions effectiveOptions = detectHeight && options.terminalHeight().isEmpty()
        ? options.withTerminalHeight(size.height()) : options;
    HeaderRepeater headers = new HeaderRepeater(effectiveOptions.terminalHeight());
    PreparedTable table = model.prepare(formatter);
    List<String> renderedGroups = new ArrayList<>();
    for (PreparedTable group : layout.arrange(table, size.width(), effectiveOptions)) {
      if (alreadyPrintedRows.isPresent()) {
        int offset = alreadyPrintedRows.getAsInt();
        if (offset < 0) throw new IllegalArgumentException("alreadyPrintedRows cannot be negative.");
        if (group.bodyRowCount() == 0) continue;
        PreparedTable appended = group.appended(offset);
        appended.configure(size.width(), effectiveOptions);
        int[] widths = new ColumnWidths(group, effectiveOptions,
            OptionalInt.of(offset + group.bodyRowCount())).forWidth(size.width());
        appended.useWidths(widths);
        String rendered = styler.style(appended, appended.render(size.width()));
        renderedGroups.add(headers.repeatAppend(group, rendered, size.width(), offset, effectiveOptions, widths));
      } else {
        group.configure(size.width(), effectiveOptions);
        String rendered = styler.style(group, group.render(size.width()));
        renderedGroups.add(headers.repeat(group, rendered));
      }
    }
    String rendered = String.join(System.lineSeparator() + System.lineSeparator(), renderedGroups);
    return alreadyPrintedRows.isPresent() && !rendered.isEmpty()
        ? MOVE_TO_PREVIOUS_LINE_AND_CLEAR + rendered : rendered;
  }
}
