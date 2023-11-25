// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.ParseFlagInfoImpl.flag0;
import static com.android.tools.r8.ParseFlagInfoImpl.flag1;
import static com.android.tools.r8.ParseFlagInfoImpl.flag2;

import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import com.android.tools.r8.profile.startup.StartupProfileProviderUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.MapIdTemplateProvider;
import com.android.tools.r8.utils.SourceFileTemplateProvider;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class R8CommandParser extends BaseCompilerCommandParser<R8Command, R8Command.Builder> {

  // Note: this must be a super-set of OPTIONS_WITH_TWO_PARAMETERS.
  private static final Set<String> OPTIONS_WITH_ONE_PARAMETER =
      ImmutableSet.of(
          "--output",
          "--lib",
          "--classpath",
          MIN_API_FLAG,
          "--main-dex-rules",
          "--main-dex-list",
          "--feature",
          "--main-dex-list-output",
          "--pg-conf",
          "--pg-conf-output",
          "--pg-map",
          "--pg-map-output",
          "--partition-map-output",
          "--desugared-lib",
          "--desugared-lib-pg-conf-output",
          "--map-id-template",
          "--source-file-template",
          ART_PROFILE_FLAG,
          STARTUP_PROFILE_FLAG,
          THREAD_COUNT_FLAG);

  // Note: this must be a subset of OPTIONS_WITH_ONE_PARAMETER.
  private static final Set<String> OPTIONS_WITH_TWO_PARAMETERS =
      ImmutableSet.of(ART_PROFILE_FLAG, "--feature");

  // Due to the family of flags (for assertions and diagnostics) we can't base the one/two args
  // on this setup of flags. Thus, the flag collection just encodes the descriptive content.
  static List<ParseFlagInfoImpl> getFlags() {
    return ImmutableList.<ParseFlagInfoImpl>builder()
        .add(ParseFlagInfoImpl.getRelease(true))
        .add(ParseFlagInfoImpl.getDebug(false))
        .add(ParseFlagInfoImpl.getDex(true))
        .add(ParseFlagInfoImpl.getClassfile())
        .add(ParseFlagInfoImpl.getOutput())
        .add(ParseFlagInfoImpl.getLib())
        .add(ParseFlagInfoImpl.getClasspath())
        .add(ParseFlagInfoImpl.getMinApi())
        .add(flag0("--pg-compat", "Compile with R8 in Proguard compatibility mode."))
        .add(ParseFlagInfoImpl.getPgConf())
        .add(flag1("--pg-conf-output", "<file>", "Output the collective configuration to <file>."))
        .add(
            ParseFlagInfoImpl.flag1(
                "--pg-map",
                "<file>",
                "Use <file> as a mapping file for distribution "
                    + "and composition with output mapping file."))
        .add(ParseFlagInfoImpl.getPgMapOutput())
        .add(ParseFlagInfoImpl.getPartitionMapOutput())
        .add(ParseFlagInfoImpl.getDesugaredLib())
        .add(
            flag1(
                "--desugared-lib-pg-conf-output",
                "<file>",
                "Output the Proguard configuration for L8 to <file>."))
        .add(flag0("--no-tree-shaking", "Force disable tree shaking of unreachable classes."))
        .add(flag0("--no-minification", "Force disable minification of names."))
        .add(flag0("--no-data-resources", "Ignore all data resources."))
        .add(flag0("--no-desugaring", "Force disable desugaring."))
        .add(ParseFlagInfoImpl.getMainDexRules())
        .add(ParseFlagInfoImpl.getMainDexList())
        .add(
            flag2(
                "--feature",
                "<input>",
                "<output>",
                "Add feature <input> file to <output> file. Several ",
                "occurrences can map to the same output."))
        .add(flag1("--main-dex-list-output", "<file>", "Output the full main-dex list in <file>."))
        .addAll(ParseFlagInfoImpl.getAssertionsFlags())
        .add(ParseFlagInfoImpl.getThreadCount())
        .add(ParseFlagInfoImpl.getMapDiagnostics())
        .add(
            flag1(
                "--map-id-template",
                "<template>",
                "Set the map-id to <template>.",
                "The <template> can reference the variables:",
                "  %MAP_HASH: compiler generated mapping hash."))
        .add(
            flag1(
                "--source-file-template",
                "<template>",
                "Set all source-file attributes to <template>",
                "The <template> can reference the variables:",
                "  %MAP_ID: map id (e.g., value of --map-id-template).",
                "  %MAP_HASH: compiler generated mapping hash."))
        .add(ParseFlagInfoImpl.getAndroidPlatformBuild())
        .add(ParseFlagInfoImpl.getArtProfile())
        .add(ParseFlagInfoImpl.getStartupProfile())
        .add(ParseFlagInfoImpl.getVersion("r8"))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(
        builder,
        "Usage: r8 [options] [@<argfile>] <input-files>",
        " where <input-files> are any combination class, zip, or jar files",
        " and each <argfile> is a file containing additional arguments (one per line)",
        " and options are:");
    new ParseFlagPrinter().addFlags(ImmutableList.copyOf(getFlags())).appendLinesToBuilder(builder);
    return builder.toString();
  }

  // Internal state to verify parsing properties not enforced by the builder.
  private static class ParseState {
    CompilationMode mode = null;
    OutputMode outputMode = null;
    Path outputPath = null;
    boolean hasDefinedApiLevel = false;
    private boolean includeDataResources = true;
  }

  /**
   * Parse the R8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static R8Command.Builder parse(String[] args, Origin origin) {
    return new R8CommandParser().parse(args, origin, R8Command.builder());
  }

  /**
   * Parse the R8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static R8Command.Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return new R8CommandParser().parse(args, origin, R8Command.builder(handler));
  }

  private R8Command.Builder parse(String[] args, Origin origin, R8Command.Builder builder) {
    ParseState state = new ParseState();
    parse(args, origin, builder, state);
    if (state.mode != null) {
      builder.setMode(state.mode);
    }
    Path outputPath = state.outputPath != null ? state.outputPath : Paths.get(".");
    OutputMode outputMode = state.outputMode != null ? state.outputMode : OutputMode.DexIndexed;
    builder.setOutput(outputPath, outputMode, state.includeDataResources);
    builder.setEnableExperimentalMissingLibraryApiModeling(true);
    return builder;
  }

  private void parse(
      String[] args, Origin argsOrigin, R8Command.Builder builder, ParseState state) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    Map<Path, List<Path>> featureSplitJars = new HashMap<>();
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      String nextNextArg = null;
      if (OPTIONS_WITH_ONE_PARAMETER.contains(arg)) {
        if (++i < expandedArgs.length) {
          nextArg = expandedArgs[i];
        } else {
          builder.error(
              new StringDiagnostic(
                  "Missing parameter for " + expandedArgs[i - 1] + ".", argsOrigin));
          break;
        }
        if (OPTIONS_WITH_TWO_PARAMETERS.contains(arg)) {
          if (++i < expandedArgs.length) {
            nextNextArg = expandedArgs[i];
          } else {
            builder.error(
                new StringDiagnostic(
                    "Missing parameter for " + expandedArgs[i - 2] + ".", argsOrigin));
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
        if (state.mode == CompilationMode.RELEASE) {
          builder.error(
              new StringDiagnostic(
                  "Cannot compile in both --debug and --release mode.", argsOrigin));
        }
        state.mode = CompilationMode.DEBUG;
      } else if (arg.equals("--release")) {
        if (state.mode == CompilationMode.DEBUG) {
          builder.error(
              new StringDiagnostic(
                  "Cannot compile in both --debug and --release mode.", argsOrigin));
        }
        state.mode = CompilationMode.RELEASE;
      } else if (arg.equals("--pg-compat")) {
        builder.setProguardCompatibility(true);
      } else if (arg.equals("--dex")) {
        if (state.outputMode == OutputMode.ClassFile) {
          builder.error(
              new StringDiagnostic(
                  "Cannot compile in both --dex and --classfile output mode.", argsOrigin));
        }
        state.outputMode = OutputMode.DexIndexed;
      } else if (arg.equals("--classfile")) {
        if (state.outputMode == OutputMode.DexIndexed) {
          builder.error(
              new StringDiagnostic(
                  "Cannot compile in both --dex and --classfile output mode.", argsOrigin));
        }
        state.outputMode = OutputMode.ClassFile;
      } else if (arg.equals("--output")) {
        if (state.outputPath != null) {
          builder.error(
              new StringDiagnostic(
                  "Cannot output both to '"
                      + state.outputPath.toString()
                      + "' and '"
                      + nextArg
                      + "'",
                  argsOrigin));
        }
        state.outputPath = Paths.get(nextArg);
      } else if (arg.equals("--lib")) {
        addLibraryArgument(builder, argsOrigin, nextArg);
      } else if (arg.equals("--classpath")) {
        builder.addClasspathFiles(Paths.get(nextArg));
      } else if (arg.equals(MIN_API_FLAG)) {
        if (state.hasDefinedApiLevel) {
          builder.error(
              new StringDiagnostic("Cannot set multiple " + MIN_API_FLAG + " options", argsOrigin));
        } else {
          parsePositiveIntArgument(
              builder::error, MIN_API_FLAG, nextArg, argsOrigin, builder::setMinApiLevel);
          state.hasDefinedApiLevel = true;
        }
      } else if (arg.equals(THREAD_COUNT_FLAG)) {
        parsePositiveIntArgument(
            builder::error, THREAD_COUNT_FLAG, nextArg, argsOrigin, builder::setThreadCount);
      } else if (arg.equals("--no-tree-shaking")) {
        builder.setDisableTreeShaking(true);
      } else if (arg.equals("--no-minification")) {
        builder.setDisableMinification(true);
      } else if (arg.equals("--no-desugaring")) {
        builder.setDisableDesugaring(true);
      } else if (arg.equals("--main-dex-rules")) {
        builder.addMainDexRulesFiles(Paths.get(nextArg));
      } else if (arg.equals("--feature")) {
        featureSplitJars
            .computeIfAbsent(Paths.get(nextNextArg), k -> new ArrayList<>())
            .add(Paths.get(nextArg));
      } else if (arg.equals("--main-dex-list")) {
        builder.addMainDexListFiles(Paths.get(nextArg));
      } else if (arg.equals("--main-dex-list-output")) {
        builder.setMainDexListOutputPath(Paths.get(nextArg));
      } else if (arg.equals("--optimize-multidex-for-linearalloc")) {
        builder.setOptimizeMultidexForLinearAlloc(true);
      } else if (arg.equals("--pg-conf")) {
        builder.addProguardConfigurationFiles(Paths.get(nextArg));
      } else if (arg.equals("--pg-conf-output")) {
        FileConsumer consumer = new FileConsumer(Paths.get(nextArg));
        builder.setProguardConfigurationConsumer(consumer);
      } else if (arg.equals("--pg-map")) {
        builder.setProguardMapInputFile(Paths.get(nextArg));
      } else if (arg.equals("--pg-map-output")) {
        builder.setProguardMapOutputPath(Paths.get(nextArg));
      } else if (arg.equals("--partition-map-output")) {
        builder.setPartitionMapOutputPath(Paths.get(nextArg));
      } else if (arg.equals("--desugared-lib")) {
        builder.addDesugaredLibraryConfiguration(StringResource.fromFile(Paths.get(nextArg)));
      } else if (arg.equals("--desugared-lib-pg-conf-output")) {
        StringConsumer consumer = new StringConsumer.FileConsumer(Paths.get(nextArg));
        builder.setDesugaredLibraryKeepRuleConsumer(consumer);
      } else if (arg.equals("--no-data-resources")) {
        state.includeDataResources = false;
      } else if (arg.equals("--map-id-template")) {
        builder.setMapIdProvider(MapIdTemplateProvider.create(nextArg, builder.getReporter()));
      } else if (arg.equals("--source-file-template")) {
        builder.setSourceFileProvider(
            SourceFileTemplateProvider.create(nextArg, builder.getReporter()));
      } else if (arg.equals("--android-platform-build")) {
        builder.setAndroidPlatformBuild(true);
      } else if (arg.equals(ART_PROFILE_FLAG)) {
        Path artProfilePath = Paths.get(nextArg);
        Path rewrittenArtProfilePath = Paths.get(nextNextArg);
        builder.addArtProfileForRewriting(
            ArtProfileProviderUtils.createFromHumanReadableArtProfile(artProfilePath),
            ArtProfileConsumerUtils.create(rewrittenArtProfilePath));
      } else if (arg.equals(STARTUP_PROFILE_FLAG)) {
        Path startupProfilePath = Paths.get(nextArg);
        builder.addStartupProfileProviders(
            StartupProfileProviderUtils.createFromHumanReadableArtProfile(startupProfilePath));
      } else if (arg.startsWith("--")) {
        if (tryParseAssertionArgument(builder, arg, argsOrigin)) {
          continue;
        }
        int argsConsumed = tryParseMapDiagnostics(builder, arg, expandedArgs, i, argsOrigin);
        if (argsConsumed >= 0) {
          i += argsConsumed;
          continue;
        }
        argsConsumed = tryParseDump(builder, arg, expandedArgs, i, argsOrigin);
        if (argsConsumed >= 0) {
          i += argsConsumed;
          continue;
        }
        builder.error(new StringDiagnostic("Unknown option: " + arg, argsOrigin));
      } else if (arg.startsWith("@")) {
        builder.error(new StringDiagnostic("Recursive @argfiles are not supported: ", argsOrigin));
      } else {
        builder.addProgramFiles(Paths.get(arg));
      }
    }
    featureSplitJars.forEach(
        (outputPath, inputJars) -> addFeatureJar(builder, outputPath, inputJars));
  }

  public void addFeatureJar(R8Command.Builder builder, Path outputPath, List<Path> inputJarPaths) {
    builder.addFeatureSplit(
        featureSplitGenerator -> {
          featureSplitGenerator.setProgramConsumer(
              builder.createProgramOutputConsumer(outputPath, OutputMode.DexIndexed, true));
          for (Path inputPath : inputJarPaths) {
            featureSplitGenerator.addProgramResourceProvider(
                ArchiveProgramResourceProvider.fromArchive(inputPath));
          }
          return featureSplitGenerator.build();
        });
  }
}
