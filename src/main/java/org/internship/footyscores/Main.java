package org.internship.footyscores;

import org.internship.footyscores.cli.GenerateCommand;
import org.internship.footyscores.cli.ServeCommand;
import org.internship.footyscores.cli.ShowCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "footyscores",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "CLI tool for generating and serving Olympic football match data.",
    subcommands = {GenerateCommand.class, ServeCommand.class, ShowCommand.class})
public final class Main {

  public Main() {}

  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exitCode);
  }
}
