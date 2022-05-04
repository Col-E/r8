// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.jdk11.DesugaredLibraryJDK11Undesugarer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class LibraryDesugaringSpecification {

  private static final String RELEASES_DIR = "third_party/openjdk/desugar_jdk_libs_releases/";
  private static final Path DESUGARED_JDK_11_LIB_JAR =
      DesugaredLibraryJDK11Undesugarer.undesugaredJarJDK11(
          Paths.get("third_party/openjdk/desugar_jdk_libs_11/desugar_jdk_libs.jar"));

  // Main head specifications.
  public static LibraryDesugaringSpecification JDK8 =
      new LibraryDesugaringSpecification(
          "JDK8",
          Paths.get("third_party/openjdk/desugar_jdk_libs/desugar_jdk_libs.jar"),
          Paths.get("src/library_desugar/desugar_jdk_libs.json"));
  public static LibraryDesugaringSpecification JDK11 =
      new LibraryDesugaringSpecification(
          "JDK11",
          DESUGARED_JDK_11_LIB_JAR,
          Paths.get("src/library_desugar/jdk11/desugar_jdk_libs.json"));
  public static LibraryDesugaringSpecification JDK11_MINIMAL =
      new LibraryDesugaringSpecification(
          "JDK11_MINIMAL",
          DESUGARED_JDK_11_LIB_JAR,
          Paths.get("src/library_desugar/jdk11/desugar_jdk_libs_minimal.json"));
  public static LibraryDesugaringSpecification JDK11_PATH =
      new LibraryDesugaringSpecification(
          "JDK11_PATH",
          DESUGARED_JDK_11_LIB_JAR,
          Paths.get("src/library_desugar/jdk11/desugar_jdk_libs_path.json"));

  // Legacy specifications.
  public static LibraryDesugaringSpecification JDK11_PATH_ALTERNATIVE_3 =
      new LibraryDesugaringSpecification(
          "JDK11_PATH_ALTERNATIVE_3",
          DESUGARED_JDK_11_LIB_JAR,
          Paths.get("src/library_desugar/jdk11/desugar_jdk_libs_path_alternative_3.json"));
  public static LibraryDesugaringSpecification JDK11_LEGACY =
      new LibraryDesugaringSpecification(
          "DESUGARED_JDK_11_LIB_JAR",
          DESUGARED_JDK_11_LIB_JAR,
          Paths.get("src/library_desugar/jdk11/desugar_jdk_libs_legacy.json"));
  private static final LibraryDesugaringSpecification RELEASED_1_0_9 =
      new LibraryDesugaringSpecification("1.0.9");
  private static final LibraryDesugaringSpecification RELEASED_1_0_10 =
      new LibraryDesugaringSpecification("1.0.10");
  private static final LibraryDesugaringSpecification RELEASED_1_1_0 =
      new LibraryDesugaringSpecification("1.1.0");
  private static final LibraryDesugaringSpecification RELEASED_1_1_1 =
      new LibraryDesugaringSpecification("1.1.1");
  private static final LibraryDesugaringSpecification RELEASED_1_1_5 =
      new LibraryDesugaringSpecification("1.1.5");

  private final String name;
  private final Set<Path> desugarJdkLibs;
  private final Path specification;

  LibraryDesugaringSpecification(String name, Path desugarJdkLibs, Path specification) {
    this(name, ImmutableSet.of(desugarJdkLibs, ToolHelper.DESUGAR_LIB_CONVERSIONS), specification);
  }

  public LibraryDesugaringSpecification(String name, Path specification) {
    this(name, DESUGARED_JDK_11_LIB_JAR, specification);
  }

  public LibraryDesugaringSpecification(String name, Set<Path> desugarJdkLibs, Path specification) {
    this.name = name;
    this.desugarJdkLibs = desugarJdkLibs;
    this.specification = specification;
  }

  LibraryDesugaringSpecification(String version) {
    this(
        "Release_" + version,
        ImmutableSet.of(
            Paths.get(RELEASES_DIR, version, "desugar_jdk_libs.jar"),
            Paths.get(RELEASES_DIR, version, "desugar_jdk_libs_configuration.jar")),
        Paths.get(RELEASES_DIR, version, "desugar.json"));
  }

  @Override
  public String toString() {
    return name;
  }

  public Set<Path> getDesugarJdkLibs() {
    return desugarJdkLibs;
  }

  public Path getSpecification() {
    return specification;
  }

  public Path getAndroidJar() {
    if (this == JDK8) {
      return ToolHelper.getAndroidJar(AndroidApiLevel.P);
    }
    return ToolHelper.getAndroidJar(AndroidApiLevel.R);
  }

  public static List<LibraryDesugaringSpecification> getReleased() {
    return ImmutableList.of(
        RELEASED_1_0_9, RELEASED_1_0_10, RELEASED_1_1_0, RELEASED_1_1_1, RELEASED_1_1_5);
  }

  public static List<LibraryDesugaringSpecification> getJdk8Jdk11() {
    return ImmutableList.of(JDK8, JDK11);
  }
}
