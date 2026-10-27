package com.ilooner.tables4j;

import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

final class CellFormatter {
  private final DateTimeFormatter dateTimeFormatter = DateTimeFormat
      .forPattern("MM/dd/yyyy hh:mm:ss.SSS a").withLocale(Locale.US);

  Object format(Object content, ColumnOptions columnOptions) {
    if (content instanceof BigMoney money) {
      return formatWithThousandsSeparators(
          money.getAmount().setScale(2, RoundingMode.HALF_UP)
      );
    }

    if (content instanceof CurrencyUnit currency) {
      return currency.getCode();
    }

    if (content instanceof BigDecimal decimal) {
      if (columnOptions.decimalPlaces() >= 0) {
        return formatWithThousandsSeparators(
            decimal.setScale(columnOptions.decimalPlaces(), RoundingMode.HALF_UP)
        );
      }

      return formatWithThousandsSeparators(decimal.stripTrailingZeros());
    }

    if (content instanceof Number number) {
      try {
        return formatWithThousandsSeparators(new BigDecimal(number.toString()).stripTrailingZeros());
      } catch (NumberFormatException e) {
        return number.toString();
      }
    }

    if (content instanceof DateTime dateTime) {
      return dateTimeFormatter.print(dateTime.withZone(DateTimeZone.getDefault()));
    }

    return content;
  }

  private String formatWithThousandsSeparators(BigDecimal value) {
    String plainValue = value.toPlainString();
    int decimalPoint = plainValue.indexOf('.');
    int integerEnd = decimalPoint >= 0 ? decimalPoint : plainValue.length();
    int integerStart = plainValue.startsWith("-") ? 1 : 0;
    StringBuilder formatted = new StringBuilder(plainValue.length() + plainValue.length() / 3);

    if (integerStart == 1) {
      formatted.append('-');
    }

    for (int i = integerStart; i < integerEnd; i++) {
      if (i > integerStart && (integerEnd - i) % 3 == 0) {
        formatted.append(',');
      }
      formatted.append(plainValue.charAt(i));
    }

    if (decimalPoint >= 0) {
      formatted.append(plainValue, decimalPoint, plainValue.length());
    }

    return formatted.toString();
  }

  TextAlignment alignment(Object value, ColumnOptions options, TextAlignment original) {
    return switch (options.alignment()) {
      case LEFT -> TextAlignment.LEFT;
      case RIGHT -> TextAlignment.RIGHT;
      case DEFAULT -> value instanceof BigMoney ? TextAlignment.RIGHT : original;
    };
  }
}
