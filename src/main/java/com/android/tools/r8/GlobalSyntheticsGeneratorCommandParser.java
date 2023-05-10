// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.BaseCompilerCommandParser.parsePositiveIntArgument;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class GlobalSyntheticsGeneratorCommandParser {

  private static final String LOWER_CASE_NAME = "globalsyntheticsgenerator";
  private static final String MIN_API_FLAG = "--min-api";

  private static final String USAGE_MESSAGE =
      StringUtils.lines("Usage: " + LOWER_CASE_NAME + " [options] " + "where options are:");

  public static List<ParseFlagInfo> getFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(ParseFlagInfoImpl.getMinApi())
        .add(ParseFlagInfoImpl.getLib())
        .add(ParseFlagInfoImpl.flag1("--output", "<dex-file>", "Output result in <dex-file>."))
        .add(ParseFlagInfoImpl.getVersion(LOWER_CASE_NAME))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(builder, USAGE_MESSAGE);
    new ParseFlagPrinter().addFlags(getFlags()).appendLinesToBuilder(builder);
    return builder.toString();
  }

  private static final Set<String> OPTIONS_WITH_ONE_PARAMETER =
      ImmutableSet.of("--output", "--lib", MIN_API_FLAG);

  public static GlobalSyntheticsGeneratorCommand.Builder parse(String[] args, Origin origin) {
    return new GlobalSyntheticsGeneratorCommandParser()
        .parse(args, origin, GlobalSyntheticsGeneratorCommand.builder());
  }

  public static GlobalSyntheticsGeneratorCommand.Builder parse(
      String[] args, Origin origin, DiagnosticsHandler handler) {
    return new GlobalSyntheticsGeneratorCommandParser()
        .parse(args, origin, GlobalSyntheticsGeneratorCommand.builder(handler));
  }

  private GlobalSyntheticsGeneratorCommand.Builder parse(
      String[] args, Origin origin, GlobalSyntheticsGeneratorCommand.Builder builder) {
    Path outputPath = null;
    boolean hasDefinedApiLevel = false;
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_ONE_PARAMETER.contains(arg)) {
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
      } else if (arg.equals("--output")) {
        if (outputPath != null) {
          builder.error(
              new StringDiagnostic(
                  "Cannot output both to '" + outputPath + "' and '" + nextArg + "'", origin));
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
        builder.addLibraryFiles(Paths.get(nextArg));
      } else if (arg.startsWith("--")) {
        builder.error(new StringDiagnostic("Unknown option: " + arg, origin));
      }
    }
    if (outputPath == null) {
      outputPath = Paths.get(".");
    }
    return builder.setProgramConsumerOutput(outputPath);
  }
}
