// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.utils.ExceptionUtils.failWithFakeEntry;
import static com.android.tools.r8.utils.ExceptionUtils.withMainProgramHandler;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.Version;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import com.android.tools.r8.utils.PartitionMapZipContainer;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** A tool for creating a partition-map from a proguard map. */
@KeepForApi
public class Partition {

  private static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: partition [options] <proguard-map> "
              + "where <proguard-map> is a generated mapping file and options are:");

  public static List<ParseFlagInfo> getFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(
            ParseFlagInfoImpl.flag1(
                "--output", "<partition-map>", "Output destination of partitioned map"))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(builder, USAGE_MESSAGE);
    new ParseFlagPrinter().addFlags(getFlags()).appendLinesToBuilder(builder);
    return builder.toString();
  }

  private static PartitionCommand.Builder parseArguments(
      String[] args, DiagnosticsHandler diagnosticsHandler) {
    ParseContext context = new ParseContext(args);
    PartitionCommand.Builder builder = PartitionCommand.builder();
    boolean hasSetProguardMap = false;
    while (context.head() != null) {
      Boolean help = OptionsParsing.tryParseBoolean(context, "--help");
      if (help != null) {
        return null;
      }
      String output = OptionsParsing.tryParseSingle(context, "--output", null);
      if (output != null && !output.isEmpty()) {
        builder.setPartitionMapConsumer(
            PartitionMapZipContainer.createPartitionMapZipContainerConsumer(Paths.get(output)));
        continue;
      }
      if (!hasSetProguardMap) {
        builder.setProguardMapProducer(ProguardMapProducer.fromPath(Paths.get(context.head())));
        context.next();
        hasSetProguardMap = true;
      } else {
        diagnosticsHandler.error(new StringDiagnostic(getUsageMessage()));
        throw new RetracePartitionException(
            String.format("Too many arguments specified for builder at '%s'", context.head()));
      }
    }
    return builder;
  }

  public static void run(String[] args) throws RetracePartitionException {
    run(args, new DiagnosticsHandler() {});
  }

  private static void run(String[] args, DiagnosticsHandler diagnosticsHandler) {
    PartitionCommand.Builder builder = parseArguments(args, diagnosticsHandler);
    if (builder == null) {
      assert Arrays.asList(args).contains("--help");
      System.out.println("Partition " + Version.getVersionString());
      System.out.print(getUsageMessage());
      return;
    }
    run(builder.build());
  }

  /**
   * The main entry point for partitioning a map.
   *
   * @param command The command that describes the desired behavior of this partition invocation.
   */
  public static void run(PartitionCommand command) {
    try {
      command
          .getPartitionMapConsumer()
          .acceptMappingPartitionMetadata(
              ProguardMapPartitioner.builder(command.getDiagnosticsHandler())
                  .setProguardMapProducer(command.getProguardMapProducer())
                  .setPartitionConsumer(command.getPartitionMapConsumer()::acceptMappingPartition)
                  .setAllowEmptyMappedRanges(true)
                  .setAllowExperimentalMapping(false)
                  .build()
                  .run());
      command.getPartitionMapConsumer().finished(command.getDiagnosticsHandler());
    } catch (Throwable t) {
      throw failWithFakeEntry(
          command.getDiagnosticsHandler(),
          t,
          (message, cause, ignore) -> new RetracePartitionException(message, cause),
          RetracePartitionException.class);
    }
  }

  /**
   * The main entry point for running a legacy proguard map to partition map from command line.
   *
   * @param args The argument that describes this command.
   */
  public static void main(String... args) {
    withMainProgramHandler(() -> run(args));
  }
}
