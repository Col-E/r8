// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DaggerUtils {

  private static final Path DAGGER_ROOT = Paths.get(ToolHelper.THIRD_PARTY_DIR, "dagger", "2.41");

  private static final String GUAVA = "guava-31.0.1-jre.jar";
  private static final List<Path> DAGGER_COMPILER =
      ImmutableList.of(
              "annotations-13.0.jar",
              "checker-compat-qual-2.5.5.jar",
              "checker-qual-3.12.0.jar",
              "dagger-2.41.jar",
              "dagger-compiler-2.41.jar",
              "dagger-producers-2.41.jar",
              "dagger-spi-2.41.jar",
              "error_prone_annotations-2.7.1.jar",
              "failureaccess-1.0.1.jar",
              "google-java-format-1.5.jar",
              GUAVA,
              "incap-0.2.jar",
              "j2objc-annotations-1.3.jar",
              "javac-shaded-9-dev-r4023-3.jar",
              "javapoet-1.13.0.jar",
              "javax.inject-1.jar",
              "jsr305-3.0.2.jar",
              "kotlin-stdlib-1.5.32.jar",
              "kotlin-stdlib-common-1.5.32.jar",
              "kotlin-stdlib-jdk7-1.5.32.jar",
              "kotlin-stdlib-jdk8-1.5.32.jar",
              "kotlinx-metadata-jvm-0.3.0.jar",
              "symbol-processing-api-1.5.30-1.0.0.jar")
          .stream()
          .map(DAGGER_ROOT::resolve)
          .collect(ImmutableList.toImmutableList());
  private static final List<Path> DAGGER_RUNTIME =
      ImmutableList.of("dagger-2.41.jar", "javax.inject-1.jar").stream()
          .map(DAGGER_ROOT::resolve)
          .collect(ImmutableList.toImmutableList());

  public static Path getGuavaFromDagger() {
    return DAGGER_ROOT.resolve(GUAVA);
  }

  public static List<Path> getDaggerRuntime() {
    return DAGGER_RUNTIME;
  }

  public static Path compileWithAnnotationProcessing(
      String target, Collection<Path> sourceFiles, Collection<Path> classFiles) throws Exception {
    // Class files are provided in JAR files. Extract all the class names to pass to javac for
    // annotation processing.
    List<String> classNames = new ArrayList<>();
    for (Path path : classFiles) {
      ZipUtils.iter(
          path,
          (entry, inputStream) -> {
            String entryString = entry.toString();
            if (FileUtils.isClassFile(entryString)) {
              byte[] bytes = ByteStreams.toByteArray(inputStream);
              classNames.add(TestBase.extractClassName(bytes));
            }
          });
    }
    return TestBase.javac(getJdk(), TestBase.getStaticTemp())
        .setSource(target)
        .setTarget(target)
        .addSourceFiles(sourceFiles)
        .addClassNames(classNames)
        .addClasspathFiles(classFiles)
        .addClasspathFiles(getDaggerRuntime())
        .addAnnotationProcessorPathFiles(DAGGER_COMPILER)
        .addAnnotationProcessors("dagger.internal.codegen.ComponentProcessor")
        .compile();
  }

  public static Path compileWithAnnotationProcessing(String target, Collection<Path> files)
      throws Exception {
    // Split the files passed into source files and class files. Class files are expected to be in
    // JARs.
    List<Path> sourceFiles =
        files.stream().filter(FileUtils::isJavaFile).collect(Collectors.toList());
    List<Path> classFiles =
        files.stream().filter(FileUtils::isJarFile).collect(Collectors.toList());
    assertEquals(files.size(), sourceFiles.size() + classFiles.size());
    return compileWithAnnotationProcessing(target, sourceFiles, classFiles);
  }

  public static Path compileWithoutAnnotationProcessing(String target, Collection<Path> files)
      throws Exception {
    return TestBase.javac(getJdk(), TestBase.getStaticTemp())
        .addSourceFiles(files)
        .addClasspathFiles(getDaggerRuntime())
        .compile();
  }

  public static Path compileWithoutAnnotationProcessing(String target, Path... files)
      throws Exception {
    return compileWithoutAnnotationProcessing(target, Arrays.asList(files));
  }

  public static Path compileWithAnnotationProcessing(String target, Path... files)
      throws Exception {
    return compileWithAnnotationProcessing(target, Arrays.asList(files));
  }

  private static CfRuntime getJdk() {
    return TestRuntime.getCheckedInJdk11();
  }
}
