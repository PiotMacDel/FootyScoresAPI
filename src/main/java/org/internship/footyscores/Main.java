package org.internship.footyscores;

import org.internship.footyscores.cli.GenerateCommand;
import org.internship.footyscores.cli.ServeCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "footyscores",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "CLI tool for generating and serving Olympic football match data.",
    subcommands = {GenerateCommand.class, ServeCommand.class})
public final class Main {

  public Main() {}

  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exitCode);
  }
}
