package org.internship.footyscores;

import org.internship.footyscores.cli.GenerateCommand;
import picocli.CommandLine;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new GenerateCommand())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
    System.exit(exitCode);
  }
}
