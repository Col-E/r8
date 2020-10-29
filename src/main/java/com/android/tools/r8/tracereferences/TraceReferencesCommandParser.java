// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.BaseCompilerCommandParser;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.tracereferences.TraceReferencesFormattingConsumer.OutputFormat;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

class TraceReferencesCommandParser {

  private static final Set<String> OPTIONS_WITH_PARAMETER =
      ImmutableSet.of("--lib", "--target", "--source", "--format", "--output");

  static final String USAGE_MESSAGE =
      String.join(
          "\n",
          Iterables.concat(
              Arrays.asList(
                  "Usage: tracereferences [options] [@<argfile>]",
                  " Each <argfile> is a file containing additional arguments (one per line)",
                  " and options are:",
                  "  --lib <file|jdk-home>   # Add <file|jdk-home> runtime library.",
                  "  --source <file>         # Add <file> as a source for tracing references.",
                  "  [--target <file>]       # Add <file> as a target for tracing references. When",
                  "                          # target is not specified all references from source",
                  "                          # outside of library are treated as a missing"
                      + " references.",
                  "  [--format printuses|keep|keepallowobfuscation]",
                  "                          # Format of the output. Default is 'printuses'.",
                  "  --output <file>         # Output result in <outfile>."),
              BaseCompilerCommandParser.MAP_DIAGNOSTICS_USAGE_MESSAGE,
              Arrays.asList(
                  "  --version               # Print the version of tracereferences.",
                  "  --help                  # Print this message.")));
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

  private TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, TraceReferencesCommand.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    Path output = null;
    OutputFormat format = null;
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
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--lib")) {
        addLibraryArgument(builder, origin, nextArg);
      } else if (arg.equals("--target")) {
        builder.addTargetFiles(Paths.get(nextArg));
      } else if (arg.equals("--source")) {
        builder.addSourceFiles(Paths.get(nextArg));
      } else if (arg.equals("--format")) {
        if (format != null) {
          builder.error(new StringDiagnostic("--format specified multiple times"));
        }
        if (nextArg.equals("printuses")) {
          format = TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE;
        }
        if (nextArg.equals("keep")) {
          format = TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES;
        }
        if (nextArg.equals("keepallowobfuscation")) {
          format = TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION;
        }
        if (format == null) {
          builder.error(new StringDiagnostic("Unsupported format '" + nextArg + "'"));
        }
      } else if (arg.equals("--output")) {
        output = Paths.get(nextArg);
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
        builder.error(new StringDiagnostic("Unsupported argument '" + arg + "'"));
      }
    }
    if (format == null) {
      format = TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE;
    }
    final Path finalOutput = output;
    builder.setConsumer(
        new TraceReferencesFormattingConsumer(format) {
          @Override
          public void finished() {
            PrintStream out = System.out;
            if (finalOutput != null) {
              try {
                out = new PrintStream(Files.newOutputStream(finalOutput));
              } catch (IOException e) {
                builder.error(new ExceptionDiagnostic(e));
              }
            }
            out.print(get());
          }
        });
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
