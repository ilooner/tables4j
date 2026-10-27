package com.ilooner.tables4j;



/** Controls whether nested bean properties become separate columns. */
public enum ColumnDiscovery {
  /** Creates one column per declared instance field that has a public getter. */
  DIRECT_FIELDS,
  /** Expands nested beans into columns with dotted headers, stopping at leaf types and cycles. */
  EXPAND_CHILD_OBJECTS
}
