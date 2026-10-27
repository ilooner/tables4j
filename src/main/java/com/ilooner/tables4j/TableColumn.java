package com.ilooner.tables4j;

import java.lang.reflect.Method;
import java.util.List;

record TableColumn(String header, List<Method> getterPath, ColumnOptions columnOptions) {
  Object getValue(Object object) {
    try {
      Object value = object;

      for (Method getter : getterPath) {
        if (value == null) {
          return "";
        }

        value = getter.invoke(value);
      }

      return value == null ? "" : value;
    } catch (Exception e) {
      throw new RuntimeException(
                          "Failed to call getter path " + getterPath, e
      );
    }
  }
}
