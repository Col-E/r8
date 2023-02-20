// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.D8CommandParser.OrderedClassFileResourceProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class L8CommandParser extends BaseCompilerCommandParser<L8Command, L8Command.Builder> {

  private static final Set<String> OPTIONS_WITH_ONE_PARAMETER =
      ImmutableSet.of(
          "--output",
          "--lib",
          MIN_API_FLAG,
          "--desugared-lib",
          THREAD_COUNT_FLAG,
          "--pg-conf",
          "--pg-map-output",
          ART_PROFILE_FLAG);

  // Note: this must be a subset of OPTIONS_WITH_ONE_PARAMETER.
  private static final Set<String> OPTIONS_WITH_TWO_PARAMETERS = ImmutableSet.of(ART_PROFILE_FLAG);

  public static void main(String[] args) throws CompilationFailedException {
    L8Command command = parse(args, Origin.root()).build();
    if (command.isPrintHelp()) {
      System.out.println(getUsageMessage());
    } else {
      L8.run(command);
    }
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(
        builder,
        "Usage: l8 [options] <input-files>",
        " where <input-files> are any combination class, zip, or jar files",
        " where <input-files> are any combination of dex, class, zip, jar, or apk files",
        " and options are:");
    new ParseFlagPrinter().addFlags(getFlags()).appendLinesToBuilder(builder);
    return builder.toString();
  }

  static List<ParseFlagInfo> getFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(ParseFlagInfoImpl.getDebug(true))
        .add(ParseFlagInfoImpl.getRelease(false))
        .add(ParseFlagInfoImpl.getOutput())
        .add(ParseFlagInfoImpl.getLib())
        .add(ParseFlagInfoImpl.getMinApi())
        .add(ParseFlagInfoImpl.getPgConf())
        .add(ParseFlagInfoImpl.getPgMapOutput())
        .add(ParseFlagInfoImpl.getDesugaredLib())
        .addAll(ParseFlagInfoImpl.getAssertionsFlags())
        .add(ParseFlagInfoImpl.getThreadCount())
        .add(ParseFlagInfoImpl.getMapDiagnostics())
        .add(ParseFlagInfoImpl.getArtProfile())
        .add(ParseFlagInfoImpl.getVersion("l8"))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  /**
   * Parse the L8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return L8 command builder with state set up according to parsed command line.
   */
  public static L8Command.Builder parse(String[] args, Origin origin) {
    return new L8CommandParser().parse(args, origin, L8Command.builder());
  }

  /**
   * Parse the L8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return L8 command builder with state set up according to parsed command line.
   */
  public static L8Command.Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return new L8CommandParser().parse(args, origin, L8Command.builder(handler));
  }

  private L8Command.Builder parse(String[] args, Origin origin, L8Command.Builder builder) {
    CompilationMode compilationMode = null;
    Path outputPath = null;
    OutputMode outputMode = OutputMode.DexIndexed;
    boolean hasDefinedApiLevel = false;
    OrderedClassFileResourceProvider.Builder classpathBuilder =
        OrderedClassFileResourceProvider.builder();
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      String nextNextArg = null;
      if (OPTIONS_WITH_ONE_PARAMETER.contains(arg)) {
        if (++i < expandedArgs.length) {
          nextArg = expandedArgs[i];
        } else {
          builder.error(
              new StringDiagnostic("Missing parameter for " + expandedArgs[i - 1] + ".", origin));
          break;
        }
        if (OPTIONS_WITH_TWO_PARAMETERS.contains(arg)) {
          if (++i < expandedArgs.length) {
            nextNextArg = expandedArgs[i];
          } else {
            builder.error(
                new StringDiagnostic("Missing parameter for " + expandedArgs[i - 2] + ".", origin));
            break;
          }
        }
      }
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--debug")) {
        if (compilationMode == CompilationMode.RELEASE) {
          builder.error(
              new StringDiagnostic("Cannot compile in both --debug and --release mode.", origin));
          continue;
        }
        compilationMode = CompilationMode.DEBUG;
      } else if (arg.equals("--release")) {
        if (compilationMode == CompilationMode.DEBUG) {
          builder.error(
              new StringDiagnostic("Cannot compile in both --debug and --release mode.", origin));
          continue;
        }
        compilationMode = CompilationMode.RELEASE;
      } else if (arg.equals("--output")) {
        if (outputPath != null) {
          builder.error(
              new StringDiagnostic(
                  "Cannot output both to '" + outputPath.toString() + "' and '" + nextArg + "'",
                  origin));
          continue;
        }
        outputPath = Paths.get(nextArg);
      } else if (arg.equals(MIN_API_FLAG)) {
        if (hasDefinedApiLevel) {
          builder.error(
              new StringDiagnostic("Cannot set multiple " + MIN_API_FLAG + " options", origin));
        } else {
          parsePositiveIntArgument(
              builder::error, MIN_API_FLAG, nextArg, origin, builder::setMinApiLevel);
          hasDefinedApiLevel = true;
        }
      } else if (arg.equals("--lib")) {
        addLibraryArgument(builder, origin, nextArg);
      } else if (arg.equals("--pg-conf")) {
        builder.addProguardConfigurationFiles(Paths.get(nextArg));
      } else if (arg.equals("--pg-map-output")) {
        builder.setProguardMapOutputPath(Paths.get(nextArg));
      } else if (arg.equals("--desugared-lib")) {
        builder.addDesugaredLibraryConfiguration(StringResource.fromFile(Paths.get(nextArg)));
      } else if (arg.equals("--classfile")) {
        outputMode = OutputMode.ClassFile;
      } else if (arg.equals(ART_PROFILE_FLAG)) {
        Path artProfilePath = Paths.get(nextArg);
        Path rewrittenArtProfilePath = Paths.get(nextNextArg);
        builder.addArtProfileForRewriting(
            ArtProfileProviderUtils.createFromHumanReadableArtProfile(artProfilePath),
            ArtProfileConsumerUtils.create(rewrittenArtProfilePath));
      } else if (arg.equals(THREAD_COUNT_FLAG)) {
        parsePositiveIntArgument(
            builder::error, THREAD_COUNT_FLAG, nextArg, origin, builder::setThreadCount);
      } else if (arg.startsWith("--")) {
        if (tryParseAssertionArgument(builder, arg, origin)) {
          continue;
        }
        int argsConsumed = tryParseMapDiagnostics(builder, arg, expandedArgs, i, origin);
        if (argsConsumed >= 0) {
          i += argsConsumed;
          continue;
        }
        argsConsumed = tryParseDump(builder, arg, expandedArgs, i, origin);
        if (argsConsumed >= 0) {
          i += argsConsumed;
          continue;
        }
        builder.error(new StringDiagnostic("Unknown option: " + arg, origin));
      } else {
        builder.addProgramFiles(Paths.get(arg));
      }
    }
    if (!classpathBuilder.isEmpty()) {
      builder.addClasspathResourceProvider(classpathBuilder.build());
    }
    if (compilationMode != null) {
      builder.setMode(compilationMode);
    }
    if (outputPath == null) {
      outputPath = Paths.get(".");
    }
    return builder.setOutput(outputPath, outputMode);
  }
}
