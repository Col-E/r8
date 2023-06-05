// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.ParseFlagInfoImpl.flag1;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import com.android.tools.r8.profile.startup.StartupProfileProviderUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class D8CommandParser extends BaseCompilerCommandParser<D8Command, D8Command.Builder> {

  private static final Set<String> OPTIONS_WITH_ONE_PARAMETER =
      ImmutableSet.of(
          "--output",
          "--lib",
          "--classpath",
          "--pg-map",
          "--pg-map-output",
          "--partition-map-output",
          MIN_API_FLAG,
          "--main-dex-rules",
          "--main-dex-list",
          "--main-dex-list-output",
          "--desugared-lib",
          "--desugared-lib-pg-conf-output",
          THREAD_COUNT_FLAG,
          ART_PROFILE_FLAG,
          STARTUP_PROFILE_FLAG);

  // Note: this must be a subset of OPTIONS_WITH_ONE_PARAMETER.
  private static final Set<String> OPTIONS_WITH_TWO_PARAMETERS = ImmutableSet.of(ART_PROFILE_FLAG);

  public static List<ParseFlagInfo> getFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(ParseFlagInfoImpl.getDebug(true))
        .add(ParseFlagInfoImpl.getRelease(false))
        .add(ParseFlagInfoImpl.getOutput())
        .add(ParseFlagInfoImpl.getLib())
        .add(ParseFlagInfoImpl.getClasspath())
        .add(ParseFlagInfoImpl.getMinApi())
        .add(
            ParseFlagInfoImpl.flag1(
                "--pg-map", "<file>", "Use <file> as a mapping file for distribution."))
        // TODO(b/183125319): Add help info once supported.
        // "  --pg-map-output <file>  # Enable line optimization and output mapping to <file>.",
        // "  --partition-map-output <file>  # Enable line optimization and output mapping to
        //   <file>.",
        .add(
            ParseFlagInfoImpl.flag0(
                "--intermediate", "Compile an intermediate result intended for later", "merging."))
        .add(
            ParseFlagInfoImpl.flag0(
                "--file-per-class",
                "Produce a separate dex file per class.",
                "Synthetic classes are in their own file."))
        .add(
            ParseFlagInfoImpl.flag0(
                "--file-per-class-file",
                "Produce a separate dex file per input .class file.",
                "Synthetic classes are with their originating class."))
        .add(ParseFlagInfoImpl.flag0("--no-desugaring", "Force disable desugaring."))
        .add(ParseFlagInfoImpl.getDesugaredLib())
        .add(
            flag1(
                "--desugared-lib-pg-conf-output",
                "<file>",
                "Output the Proguard configuration for L8 to <file>."))
        .add(ParseFlagInfoImpl.getMainDexRules())
        .add(ParseFlagInfoImpl.getMainDexList())
        .add(ParseFlagInfoImpl.getMainDexListOutput())
        .addAll(ParseFlagInfoImpl.getAssertionsFlags())
        .add(ParseFlagInfoImpl.getThreadCount())
        .add(ParseFlagInfoImpl.getMapDiagnostics())
        .add(ParseFlagInfoImpl.getAndroidPlatformBuild())
        .add(ParseFlagInfoImpl.getArtProfile())
        .add(ParseFlagInfoImpl.getStartupProfile())
        .add(ParseFlagInfoImpl.getVersion("d8"))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  private static final String APK_EXTENSION = ".apk";
  private static final String JAR_EXTENSION = ".jar";
  private static final String ZIP_EXTENSION = ".zip";

  private static boolean isArchive(Path path) {
    String name = StringUtils.toLowerCase(path.getFileName().toString());
    return name.endsWith(APK_EXTENSION)
        || name.endsWith(JAR_EXTENSION)
        || name.endsWith(ZIP_EXTENSION);
  }

  static class OrderedClassFileResourceProvider implements ClassFileResourceProvider {
    static class Builder {
      private final ImmutableList.Builder<ClassFileResourceProvider> builder =
          ImmutableList.builder();
      boolean empty = true;

      OrderedClassFileResourceProvider build() {
        return new OrderedClassFileResourceProvider(builder.build());
      }

      Builder addClassFileResourceProvider(ClassFileResourceProvider provider) {
        builder.add(provider);
        empty = false;
        return this;
      }

      boolean isEmpty() {
        return empty;
      }
    }

    final List<ClassFileResourceProvider> providers;
    final Set<String> descriptors = Sets.newHashSet();

    private OrderedClassFileResourceProvider(ImmutableList<ClassFileResourceProvider> providers) {
      this.providers = providers;
      // Collect all descriptors that can be provided.
      this.providers.forEach(provider -> this.descriptors.addAll(provider.getClassDescriptors()));
    }

    static Builder builder() {
      return new Builder();
    }

    @Override
    public Set<String> getClassDescriptors() {
      return descriptors;
    }

    @Override
    public ProgramResource getProgramResource(String descriptor) {
      // Search the providers in order. Return the program resource from the first provider that
      // can provide it.
      for (ClassFileResourceProvider provider : providers) {
        if (provider.getClassDescriptors().contains(descriptor)) {
          return provider.getProgramResource(descriptor);
        }
      }
      return null;
    }
  }

  public static void main(String[] args) throws CompilationFailedException {
    D8Command command = parse(args, Origin.root()).build();
    if (command.isPrintHelp()) {
      System.out.println(getUsageMessage());
    } else {
      D8.run(command);
    }
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(
        builder,
        "Usage: d8 [options] [@<argfile>] <input-files>",
        " where <input-files> are any combination of dex, class, zip, jar, or apk files",
        " and each <argfile> is a file containing additional arguments (one per line)",
        " and options are:");
    new ParseFlagPrinter().addFlags(ImmutableList.copyOf(getFlags())).appendLinesToBuilder(builder);
    return builder.toString();
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static D8Command.Builder parse(String[] args, Origin origin) {
    return new D8CommandParser().parse(args, origin, D8Command.builder());
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static D8Command.Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return new D8CommandParser().parse(args, origin, D8Command.builder(handler));
  }

  private D8Command.Builder parse(String[] args, Origin origin, D8Command.Builder builder) {
    CompilationMode compilationMode = null;
    Path outputPath = null;
    OutputMode outputMode = null;
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
      } else if (arg.equals("--file-per-class")) {
        outputMode = OutputMode.DexFilePerClass;
      } else if (arg.equals("--file-per-class-file")) {
        outputMode = OutputMode.DexFilePerClassFile;
      } else if (arg.equals("--classfile")) {
        outputMode = OutputMode.ClassFile;
      } else if (arg.equals("--pg-map")) {
        builder.setProguardInputMapFile(Paths.get(nextArg));
      } else if (arg.equals("--pg-map-output")) {
        builder.setProguardMapOutputPath(Paths.get(nextArg));
      } else if (arg.equals("--partition-map-output")) {
        builder.setPartitionMapOutputPath(Paths.get(nextArg));
      } else if (arg.equals("--output")) {
        if (outputPath != null) {
          builder.error(
              new StringDiagnostic(
                  "Cannot output both to '" + outputPath.toString() + "' and '" + nextArg + "'",
                  origin));
          continue;
        }
        outputPath = Paths.get(nextArg);
      } else if (arg.equals("--lib")) {
        addLibraryArgument(builder, origin, nextArg);
      } else if (arg.equals("--classpath")) {
        Path file = Paths.get(nextArg);
        try {
          if (!Files.exists(file)) {
            throw new NoSuchFileException(file.toString());
          }
          if (isArchive(file)) {
            classpathBuilder.addClassFileResourceProvider(new ArchiveClassFileProvider(file));
          } else if (Files.isDirectory(file)) {
            classpathBuilder.addClassFileResourceProvider(
                DirectoryClassFileProvider.fromDirectory(file));
          } else {
            builder.error(
                new StringDiagnostic("Unsupported classpath file type", new PathOrigin(file)));
          }
        } catch (IOException e) {
          builder.error(new ExceptionDiagnostic(e, new PathOrigin(file)));
        }
      } else if (arg.equals("--main-dex-rules")) {
        builder.addMainDexRulesFiles(Paths.get(nextArg));
      } else if (arg.equals("--main-dex-list")) {
        builder.addMainDexListFiles(Paths.get(nextArg));
      } else if (arg.equals("--main-dex-list-output")) {
        builder.setMainDexListOutputPath(Paths.get(nextArg));
      } else if (arg.equals("--optimize-multidex-for-linearalloc")) {
        builder.setOptimizeMultidexForLinearAlloc(true);
      } else if (arg.equals(MIN_API_FLAG)) {
        if (hasDefinedApiLevel) {
          builder.error(
              new StringDiagnostic("Cannot set multiple " + MIN_API_FLAG + " options", origin));
        } else {
          parsePositiveIntArgument(
              builder::error, MIN_API_FLAG, nextArg, origin, builder::setMinApiLevel);
          hasDefinedApiLevel = true;
        }
      } else if (arg.equals(THREAD_COUNT_FLAG)) {
        parsePositiveIntArgument(
            builder::error, THREAD_COUNT_FLAG, nextArg, origin, builder::setThreadCount);
      } else if (arg.equals("--intermediate")) {
        builder.setIntermediate(true);
      } else if (arg.equals("--no-desugaring")) {
        builder.setDisableDesugaring(true);
      } else if (arg.equals("--desugared-lib")) {
        builder.addDesugaredLibraryConfiguration(StringResource.fromFile(Paths.get(nextArg)));
      } else if (arg.equals("--desugared-lib-pg-conf-output")) {
        StringConsumer consumer = new StringConsumer.FileConsumer(Paths.get(nextArg));
        builder.setDesugaredLibraryKeepRuleConsumer(consumer);
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
      } else if (arg.startsWith("@")) {
        builder.error(new StringDiagnostic("Recursive @argfiles are not supported: ", origin));
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
    if (outputMode == null) {
      outputMode = OutputMode.DexIndexed;
    }
    if (outputPath == null) {
      outputPath = Paths.get(".");
    }
    return builder.setOutput(outputPath, outputMode);
  }
}
