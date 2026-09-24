package org.internship.footyscores.output;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared JSON writing helpers, pinned so that output is byte-stable across runs and platforms. */
public final class Json {

  private Json() {}

  /** Two-space indentation, {@code "key": value} spacing and LF newlines. */
  public static ObjectWriter prettyWriter(ObjectMapper mapper) {
    DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
    DefaultPrettyPrinter printer =
        new DefaultPrettyPrinter()
            .withSeparators(
                Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
    printer.indentObjectsWith(indenter);
    printer.indentArraysWith(indenter);
    return mapper.writer(printer).with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  public static void write(Path target, Object value, ObjectWriter writer) throws IOException {
    if (target.getParent() != null) {
      Files.createDirectories(target.getParent());
    }
    Files.writeString(target, writer.writeValueAsString(value) + "\n", StandardCharsets.UTF_8);
  }
}
