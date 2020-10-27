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
import java.io.IOException;
import java.nio.file.Files;
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
public class CompileDumpCompatR8 {

  private static final List<String> VALID_OPTIONS =
      Arrays.asList("--classfile", "--compat", "--debug", "--release");

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
          "--threads");

  private static final List<String> VALID_OPTIONS_WITH_TWO_OPERANDS =
      Arrays.asList("--feature-jar");

  private static boolean FileUtils_isArchive(Path path) {
    String name = path.getFileName().toString().toLowerCase();
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
    CompilationMode compilationMode = CompilationMode.RELEASE;
    List<Path> program = new ArrayList<>();
    Map<Path, Path> features = new LinkedHashMap<>();
    List<Path> library = new ArrayList<>();
    List<Path> classpath = new ArrayList<>();
    List<Path> config = new ArrayList<>();
    int minApi = 1;
    int threads = -1;
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
          case "--threads":
            {
              threads = Integer.parseInt(operand);
              break;
            }
          default:
            throw new IllegalArgumentException("Unimplemented option: " + option);
        }
      } else if (VALID_OPTIONS_WITH_TWO_OPERANDS.contains(option)) {
        String firstOperand = args[++i];
        String secondOperand = args[++i];
        switch (option) {
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
            .setOutput(outputPath, outputMode)
            .setMode(compilationMode);
    if (desugaredLibJson != null) {
      commandBuilder.addDesugaredLibraryConfiguration(readAllBytesJava7(desugaredLibJson));
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

  // We cannot use StringResource since this class is added to the class path and has access only
  // to the public APIs.
  private static String readAllBytesJava7(Path filePath) {
    String content = "";

    try {
      content = new String(Files.readAllBytes(filePath));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return content;
  }
}
