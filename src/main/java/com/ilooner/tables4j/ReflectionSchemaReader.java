package com.ilooner.tables4j;

import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ReflectionSchemaReader {
  List<TableColumn> discoverColumns(
      Class<?> clazz,
      ColumnDiscovery columnDiscoveryStrategy
  ) {
    return discoverColumns(
        clazz,
        columnDiscoveryStrategy,
        "",
        List.of(),
        Set.of(),
        ColumnOptions.NONE
    );
  }

  private List<TableColumn> discoverColumns(
      Class<?> clazz,
      ColumnDiscovery columnDiscoveryStrategy,
      String headerPrefix,
      List<Method> getterPath,
      Set<Class<?>> ancestry,
      ColumnOptions inheritedColumnOptions
  ) {
    List<TableColumn> columns = new ArrayList<>();
    Set<Class<?>> childAncestry = new HashSet<>(ancestry);
    childAncestry.add(clazz);

    for (Field field : clazz.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }

      Method getter = findGetter(clazz, field);
      if (getter == null) {
        continue;
      }

      String header = headerPrefix + columnName(field);
      List<Method> fieldGetterPath = append(getterPath, getter);
      ColumnOptions fieldColumnOptions = columnOptions(field, inheritedColumnOptions);

      if (columnDiscoveryStrategy == ColumnDiscovery.EXPAND_CHILD_OBJECTS
          && shouldExpandChildObject(field.getType(), childAncestry)) {
        List<TableColumn> childColumns = discoverColumns(
            field.getType(),
            columnDiscoveryStrategy,
            header + ".",
            fieldGetterPath,
            childAncestry,
            fieldColumnOptions
        );

        if (!childColumns.isEmpty()) {
          columns.addAll(childColumns);
          continue;
        }
      }

      columns.add(new TableColumn(header, fieldGetterPath, fieldColumnOptions));
    }

    return columns;
  }

  private String columnName(Field field) {
    AsciiTableColumn annotation = field.getAnnotation(AsciiTableColumn.class);
    if (annotation == null || annotation.name().isBlank()) {
      return field.getName();
    }

    return annotation.name();
  }

  private ColumnOptions columnOptions(Field field, ColumnOptions inheritedOptions) {
    AsciiTableColumn annotation = field.getAnnotation(AsciiTableColumn.class);
    if (annotation == null) {
      return inheritedOptions;
    }

    if (annotation.decimalPlaces() < -1) {
      throw new IllegalArgumentException(
          "decimalPlaces cannot be less than -1 for field "
              + field.getDeclaringClass().getName() + "." + field.getName()
      );
    }

    return inheritedOptions.merge(
        new ColumnOptions(
            annotation.minimumWidth(),
            annotation.padding(),
            annotation.decimalPlaces(),
            annotation.alignment(),
            annotation.colorCode()
        )
    );
  }

  private List<Method> append(List<Method> getterPath, Method getter) {
    List<Method> fieldGetterPath = new ArrayList<>(getterPath);
    fieldGetterPath.add(getter);

    return List.copyOf(fieldGetterPath);
  }

  private boolean shouldExpandChildObject(Class<?> clazz, Set<Class<?>> ancestry) {
    return !ancestry.contains(clazz) && !isLeafType(clazz);
  }

  private boolean isLeafType(Class<?> clazz) {
    return clazz.isPrimitive()
        || clazz.isEnum()
        || clazz.isArray()
        || clazz == Boolean.class
        || clazz == Character.class
        || clazz == Void.class
        || clazz == Class.class
        || CharSequence.class.isAssignableFrom(clazz)
        || Number.class.isAssignableFrom(clazz)
        || BigMoney.class.isAssignableFrom(clazz)
        || CurrencyUnit.class.isAssignableFrom(clazz)
        || DateTime.class.isAssignableFrom(clazz)
        || TemporalAccessor.class.isAssignableFrom(clazz)
        || Collection.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz)
        || Optional.class.isAssignableFrom(clazz)
        || clazz.getPackageName().startsWith("java.");
  }

  private Method findGetter(Class<?> clazz, Field field) {
    String fieldName = field.getName();
    String capitalized =
                        Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

    List<String> getterNames = new ArrayList<>();
    getterNames.add("get" + capitalized);

    if (field.getType() == boolean.class || field.getType() == Boolean.class) {
      getterNames.add("is" + capitalized);
    }

    for (String getterName : getterNames) {
      try {
        Method method = clazz.getMethod(getterName);
        if (method.getParameterCount() == 0) {
          return method;
        }
      } catch (NoSuchMethodException ignored) {
      }
    }

    return null;
  }
}
