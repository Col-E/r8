// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.StringConsumer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wrapper to make it easy to call R8 in compat mode when compiling a dump file.
 *
 * <p>This wrapper will be added to the classpath so it *must* only refer to the public API. See
 * {@code tools/compiledump.py}.
 *
 * <p>It is tempting to have this class share the R8 parser code, but such refactoring would not be
 * valid on past version of the R8 API. Thus there is little else to do than reimplement the parts
 * we want to support for reading dumps.
 */
public class CompileDumpCompatR8 extends CompileDumpBase {

  private static final List<String> VALID_OPTIONS =
      Arrays.asList(
          "--classfile",
          "--compat",
          "--debug",
          "--release",
          "--enable-missing-library-api-modeling",
          "--android-platform-build");

  private static final List<String> VALID_OPTIONS_WITH_SINGLE_OPERAND =
      Arrays.asList(
          "--output",
          "--lib",
          "--classpath",
          "--min-api",
          "--main-dex-rules",
          "--main-dex-list",
          "--main-dex-list-output",
          "--pg-conf",
          "--pg-map-output",
          "--desugared-lib",
          "--desugared-lib-pg-conf-output",
          "--threads",
          "--startup-profile");

  private static final List<String> VALID_OPTIONS_WITH_TWO_OPERANDS =
      Arrays.asList("--art-profile", "--feature-jar");

  private static boolean FileUtils_isArchive(Path path) {
    String name = StringUtils.toLowerCase(path.getFileName().toString());
    return name.endsWith(".apk")
        || name.endsWith(".jar")
        || name.endsWith(".zip")
        || name.endsWith(".aar");
  }

  public static void main(String[] args) throws CompilationFailedException {
    boolean isCompatMode = false;
    OutputMode outputMode = OutputMode.DexIndexed;
    Path outputPath = null;
    Path pgMapOutput = null;
    Path desugaredLibJson = null;
    StringConsumer desugaredLibKeepRuleConsumer = null;
    CompilationMode compilationMode = CompilationMode.RELEASE;
    List<Path> program = new ArrayList<>();
    Map<Path, Path> features = new LinkedHashMap<>();
    List<Path> library = new ArrayList<>();
    List<Path> classpath = new ArrayList<>();
    List<Path> config = new ArrayList<>();
    List<Path> mainDexRulesFiles = new ArrayList<>();
    Map<Path, Path> artProfileFiles = new LinkedHashMap<>();
    List<Path> startupProfileFiles = new ArrayList<>();
    int minApi = 1;
    int threads = -1;
    boolean enableMissingLibraryApiModeling = false;
    boolean androidPlatformBuild = false;
    for (int i = 0; i < args.length; i++) {
      String option = args[i];
      if (VALID_OPTIONS.contains(option)) {
        switch (option) {
          case "--classfile":
            {
              outputMode = OutputMode.ClassFile;
              break;
            }
          case "--compat":
            {
              isCompatMode = true;
              break;
            }
          case "--debug":
            {
              compilationMode = CompilationMode.DEBUG;
              break;
            }
          case "--release":
            {
              compilationMode = CompilationMode.RELEASE;
              break;
            }
          case "--enable-missing-library-api-modeling":
            enableMissingLibraryApiModeling = true;
            break;
          case "--android-platform-build":
            androidPlatformBuild = true;
            break;
          default:
            throw new IllegalArgumentException("Unimplemented option: " + option);
        }
      } else if (VALID_OPTIONS_WITH_SINGLE_OPERAND.contains(option)) {
        String operand = args[++i];
        switch (option) {
          case "--output":
            {
              outputPath = Paths.get(operand);
              break;
            }
          case "--lib":
            {
              library.add(Paths.get(operand));
              break;
            }
          case "--classpath":
            {
              classpath.add(Paths.get(operand));
              break;
            }
          case "--min-api":
            {
              minApi = Integer.parseInt(operand);
              break;
            }
          case "--pg-conf":
            {
              config.add(Paths.get(operand));
              break;
            }
          case "--pg-map-output":
            {
              pgMapOutput = Paths.get(operand);
              break;
            }
          case "--desugared-lib":
            {
              desugaredLibJson = Paths.get(operand);
              break;
            }
          case "--desugared-lib-pg-conf-output":
            {
              desugaredLibKeepRuleConsumer = new StringConsumer.FileConsumer(Paths.get(operand));
              break;
            }
          case "--threads":
            {
              threads = Integer.parseInt(operand);
              break;
            }
          case "--main-dex-rules":
            {
              mainDexRulesFiles.add(Paths.get(operand));
              break;
            }
          case "--startup-profile":
            {
              startupProfileFiles.add(Paths.get(operand));
              break;
            }
          default:
            throw new IllegalArgumentException("Unimplemented option: " + option);
        }
      } else if (VALID_OPTIONS_WITH_TWO_OPERANDS.contains(option)) {
        String firstOperand = args[++i];
        String secondOperand = args[++i];
        switch (option) {
          case "--art-profile":
            {
              artProfileFiles.put(Paths.get(firstOperand), Paths.get(secondOperand));
              break;
            }
          case "--feature-jar":
            {
              Path featureIn = Paths.get(firstOperand);
              Path featureOut = Paths.get(secondOperand);
              if (!FileUtils_isArchive(featureIn)) {
                throw new IllegalArgumentException(
                    "Expected an archive, got `" + featureIn.toString() + "`.");
              }
              features.put(featureIn, featureOut);
              break;
            }
          default:
            throw new IllegalArgumentException("Unimplemented option: " + option);
        }
      } else {
        program.add(Paths.get(option));
      }
    }
    Builder commandBuilder =
        new CompatProguardCommandBuilder(isCompatMode)
            .addProgramFiles(program)
            .addLibraryFiles(library)
            .addClasspathFiles(classpath)
            .addProguardConfigurationFiles(config)
            .addMainDexRulesFiles(mainDexRulesFiles)
            .setOutput(outputPath, outputMode)
            .setMode(compilationMode);
    addArtProfilesForRewriting(commandBuilder, artProfileFiles);
    addStartupProfileProviders(commandBuilder, startupProfileFiles);
    setAndroidPlatformBuild(commandBuilder, androidPlatformBuild);
    setEnableExperimentalMissingLibraryApiModeling(commandBuilder, enableMissingLibraryApiModeling);
    if (desugaredLibJson != null) {
      commandBuilder.addDesugaredLibraryConfiguration(readAllBytesJava7(desugaredLibJson));
    }
    if (desugaredLibKeepRuleConsumer != null) {
      commandBuilder.setDesugaredLibraryKeepRuleConsumer(desugaredLibKeepRuleConsumer);
    }
    if (outputMode != OutputMode.ClassFile) {
      commandBuilder.setMinApiLevel(minApi);
    }
    features.forEach(
        (in, out) ->
            commandBuilder.addFeatureSplit(
                featureBuilder ->
                    featureBuilder
                        .addProgramResourceProvider(ArchiveResourceProvider.fromArchive(in, true))
                        .setProgramConsumer(new ArchiveConsumer(out))
                        .build()));
    if (pgMapOutput != null) {
      commandBuilder.setProguardMapOutputPath(pgMapOutput);
    }
    R8Command command = commandBuilder.build();
    if (threads != -1) {
      ExecutorService executor = Executors.newWorkStealingPool(threads);
      try {
        R8.run(command, executor);
      } finally {
        executor.shutdown();
      }
    } else {
      R8.run(command);
    }
  }
}
