// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.apiusagesample;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.utils.CompilationFailedException;
import com.android.tools.r8.utils.OutputMode;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class D8Compiler {
  private int minSdkVersion;
  private Path bootclasspath;
  private List<Path> classpath;
  private static ExecutorService pool = Executors.newFixedThreadPool(4);

  private D8Compiler(int minSdkVersion, Path bootclasspath, List<Path> classpath) {
    this.minSdkVersion = minSdkVersion;
    this.bootclasspath = bootclasspath;
    this.classpath = classpath;
  }

  /**
   * java ...Compiler output input minSdkVersion mainDexClasses bootclasspath [classpathEntries]+
   */
  public static void main(String[] args) throws Throwable {
    try {
      int argIndex = 0;
      Path outputDir = Paths.get(args[argIndex++]);
      Path input = Paths.get(args[argIndex++]);
      int minSdkVersion = Integer.parseInt(args[argIndex++]);
      Path mainDexClasses = Paths.get(args[argIndex++]);
      Path bootclasspath = Paths.get(args[argIndex++]);

      List<Path> classpath = new ArrayList<>(args.length - argIndex);
      while (argIndex < args.length) {
        classpath.add(Paths.get(args[argIndex++]));
      }

      D8Compiler compiler = new D8Compiler(minSdkVersion, bootclasspath, classpath);

      List<Path> toMerge = new ArrayList<>(3);

      int intermediateIndex = 0;
      for (Path entry : classpath) {
        Path output = outputDir.resolve(entry.getFileName() + "." + (intermediateIndex++));
        Files.createDirectory(output);
        toMerge.add(output);
        compiler.compile(output, entry);
      }

      Path output = outputDir.resolve("main." + (intermediateIndex++));
      Files.createDirectory(output);
      toMerge.add(output);
      compiler.compile(output, input);

      compiler.merge(outputDir, mainDexClasses, toMerge);
    } finally {
      // Terminate pool threads to prevent the VM to wait on then before exiting.
      pool.shutdown();
    }
  }

  private void compile(Path output, Path input) throws Throwable {
    D8Command.Builder builder =
        D8Command.builder()
            // Compile in debug and merge in release to assert access to both modes
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(minSdkVersion)
            .setIntermediate(true)
            .setEnableDesugaring(true)
            .setOutputPath(output);

    builder.addLibraryResourceProvider(CachingArchiveClassFileProvider.getProvider(bootclasspath));

    for (Path entry : classpath) {
      builder.addClasspathResourceProvider(CachingArchiveClassFileProvider.getProvider(entry));
    }

    if (Files.isRegularFile(input)) {
      builder.setOutputMode(OutputMode.Indexed);
      builder.addProgramFiles(input);
    } else {
      builder.setOutputMode(OutputMode.FilePerInputClass);
      Files.walkFileTree(input, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes)
            throws IOException {
          builder.addClassProgramData(Files.readAllBytes(path));
          return FileVisitResult.CONTINUE;
        }
      });
    }

    D8.run(builder.build(), pool);
  }

  private void merge(Path outputDir, Path mainDexClasses,
      List<Path> toMerge) throws IOException, CompilationException, CompilationFailedException {
    D8Command.Builder merger = D8Command.builder();
    merger.setEnableDesugaring(false);

    for (Path mergeInput : toMerge) {
      Files.walkFileTree(mergeInput, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes)
            throws IOException {
          merger.addDexProgramData(Files.readAllBytes(path));
          return FileVisitResult.CONTINUE;
        }
      });
    }
    if (mainDexClasses != null) {
      merger.addMainDexListFiles(mainDexClasses);
    }
    merger.setMinApiLevel(minSdkVersion)
        .setMode(CompilationMode.RELEASE)
        .setOutputPath(outputDir)
        .setEnableDesugaring(false)
        .setIntermediate(false);
    D8.run(merger.build());
  }
}
