// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;


import com.android.tools.r8.BaseCompilerCommandParser;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.StringConsumer.WriterConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

class TraceReferencesCommandParser {

  private static final Set<String> OPTIONS_WITH_PARAMETER =
      ImmutableSet.of("--lib", "--target", "--source", "--output");

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(
        builder,
        "Usage: tracereferences <command> [<options>] [@<argfile>]",
        " Where <command> is one of:");
    new ParseFlagPrinter().addFlags(getCommandFlags()).appendLinesToBuilder(builder);
    StringUtils.appendLines(
        builder,
        " and each <argfile> is a file containing additional options (one per line)",
        " and options are:");
    new ParseFlagPrinter().addFlags(getOptionFlags()).appendLinesToBuilder(builder);
    StringUtils.appendLines(builder, " and <keep-rule-options> are:");
    new ParseFlagPrinter().addFlags(getKeepRuleFlags()).appendLinesToBuilder(builder);
    return builder.toString();
  }

  static List<ParseFlagInfo> getCommandFlags() {
    return ImmutableList.of(
        ParseFlagInfoImpl.flag0("--check", "Run emitting only diagnostics messages."),
        ParseFlagInfoImpl.flag1(
            "--keep-rules",
            "[<keep-rules-options>]",
            "Traced references will be output in the keep-rules",
            "format."));
  }

  static List<ParseFlagInfo> getOptionFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(
            ParseFlagInfoImpl.flag1(
                "--lib", "<file|jdk-home>", "Add <file|jdk-home> runtime library."))
        .add(
            ParseFlagInfoImpl.flag1(
                "--source", "<file>", "Add <file> as a source for tracing references."))
        .add(
            ParseFlagInfoImpl.flag1(
                "--target",
                "<file>",
                "Add <file> as a target for tracing references. When",
                "target is not specified all references from source",
                "outside of library are treated as a missing",
                "references."))
        .add(
            ParseFlagInfoImpl.flag1(
                "--output",
                "<file>",
                "Output result in <outfile>. If not passed the",
                "result will go to standard out."))
        .add(ParseFlagInfoImpl.getMapDiagnostics())
        .add(ParseFlagInfoImpl.getVersion("tracereferences"))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  static List<ParseFlagInfo> getKeepRuleFlags() {
    return ImmutableList.of(
        ParseFlagInfoImpl.flag0(
            "--allowobfuscation",
            "Output keep rules with the allowobfuscation",
            "modifier (defaults to rules without the modifier)"));
  }

  /**
   * Parse the tracereferences command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return tracereferences command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(String[] args, Origin origin) {
    return new TraceReferencesCommandParser().parse(args, origin, TraceReferencesCommand.builder());
  }

  /**
   * Parse the tracereferences command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return tracereferences command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, DiagnosticsHandler handler) {
    return new TraceReferencesCommandParser()
        .parse(args, origin, TraceReferencesCommand.builder(handler));
  }

  private enum Command {
    CHECK,
    KEEP_RULES;
  }

  private void checkCommandNotSet(
      Command command, TraceReferencesCommand.Builder builder, Origin origin) {
    if (command != null) {
      builder.error(new StringDiagnostic("Multiple commands specified", origin));
    }
  }

  @SuppressWarnings("DefaultCharset")
  private TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, TraceReferencesCommand.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    Path output = null;
    Command command = null;
    boolean allowObfuscation = false;
    if (expandedArgs.length == 0) {
      builder.error(new StringDiagnostic("Missing command"));
      return builder;
    }
    // Parse options.
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_PARAMETER.contains(arg)) {
        if (++i < expandedArgs.length) {
          nextArg = expandedArgs[i];
        } else {
          builder.error(
              new StringDiagnostic("Missing parameter for " + expandedArgs[i - 1] + ".", origin));
          break;
        }
      }
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
        return builder;
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
        return builder;
      } else if (arg.equals("--check")) {
        checkCommandNotSet(command, builder, origin);
        command = Command.CHECK;
      } else if (arg.equals("--keep-rules")) {
        checkCommandNotSet(command, builder, origin);
        command = Command.KEEP_RULES;
      } else if (arg.equals("--allowobfuscation")) {
        allowObfuscation = true;
      } else if (arg.equals("--lib")) {
        addLibraryArgument(builder, origin, nextArg);
      } else if (arg.equals("--target")) {
        builder.addTargetFiles(Paths.get(nextArg));
      } else if (arg.equals("--source")) {
        builder.addSourceFiles(Paths.get(nextArg));
      } else if (arg.equals("--output")) {
        if (output != null) {
          builder.error(new StringDiagnostic("Option '--output' passed multiple times.", origin));
        } else {
          output = Paths.get(nextArg);
        }
      } else if (arg.startsWith("@")) {
        builder.error(new StringDiagnostic("Recursive @argfiles are not supported: ", origin));
      } else {
        int argsConsumed =
            BaseCompilerCommandParser.tryParseMapDiagnostics(
                builder::error, builder.getReporter(), arg, expandedArgs, i, origin);
        if (argsConsumed >= 0) {
          i += argsConsumed;
          continue;
        }
        builder.error(new StringDiagnostic("Unsupported option '" + arg + "'", origin));
      }
    }

    if (command == null) {
      builder.error(
          new StringDiagnostic(
              "Missing command, specify one of 'check' or '--keep-rules'", origin));
      return builder;
    }

    if (command == Command.CHECK && output != null) {
      builder.error(
          new StringDiagnostic("Using '--output' requires command '--keep-rules'", origin));
      return builder;
    }

    if (command != Command.KEEP_RULES && allowObfuscation) {
      builder.error(
          new StringDiagnostic(
              "Using '--allowobfuscation' requires command '--keep-rules'", origin));
      return builder;
    }

    switch (command) {
      case CHECK:
        builder.setConsumer(
            new TraceReferencesCheckConsumer(TraceReferencesConsumer.emptyConsumer()));
        break;
      case KEEP_RULES:
        builder.setConsumer(
            new TraceReferencesCheckConsumer(
                TraceReferencesKeepRules.builder()
                    .setAllowObfuscation(allowObfuscation)
                    .setOutputConsumer(
                        output != null
                            ? new FileConsumer(output)
                            : new WriterConsumer(null, new PrintWriter(System.out)))
                    .build()));
        break;
      default:
        throw new Unreachable();
    }
    return builder;
  }

  /**
   * This method must match the lookup in {@link
   * com.android.tools.r8.JdkClassFileProvider#fromJdkHome}.
   */
  private static boolean isJdkHome(Path home) {
    Path jrtFsJar = home.resolve("lib").resolve("jrt-fs.jar");
    if (Files.exists(jrtFsJar)) {
      return true;
    }
    // JDK has rt.jar in jre/lib/rt.jar.
    Path rtJar = home.resolve("jre").resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    // JRE has rt.jar in lib/rt.jar.
    rtJar = home.resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    return false;
  }

  static void addLibraryArgument(
      TraceReferencesCommand.Builder builder, Origin origin, String arg) {
    Path path = Paths.get(arg);
    if (isJdkHome(path)) {
      try {
        builder.addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(path));
      } catch (IOException e) {
        builder.error(new ExceptionDiagnostic(e, origin));
      }
    } else {
      builder.addLibraryFiles(path);
    }
  }
}
