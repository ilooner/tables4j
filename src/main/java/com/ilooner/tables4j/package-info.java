/**
 * Object-based terminal tables. A {@link com.ilooner.tables4j.TableSchema} discovers bean columns
 * once, including {@link com.ilooner.tables4j.AsciiTableColumn} annotations. A
 * {@link com.ilooner.tables4j.TableRenderer} formats snapshots without modifying their inputs.
 * A {@link com.ilooner.tables4j.TableSession} owns row numbering and output for a stream of batches.
 *
 * <pre>{@code
 * var schema = TableSchema.from(Item.class, ColumnDiscovery.EXPAND_CHILD_OBJECTS);
 * var renderer = TableRenderer.forTerminal(RenderOptions.columnWrapping());
 * output.println(renderer.render(schema, items));
 *
 * var session = renderer.openSession(schema, output);
 * session.append(firstBatch);
 * session.append(nextBatch);
 * }</pre>
 *
 * <p>Fixed-width rendering and an injectable {@link com.ilooner.tables4j.TerminalSizeProvider}
 * support deterministic output without changing process-wide terminal settings.
 */
package com.ilooner.tables4j;
