// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class BenchmarkDependency {

  public static BenchmarkDependency getRuntimeJarJava8() {
    return new BenchmarkDependency(
        "java8rtjar", "openjdk-rt-1.8", Paths.get("third_party", "openjdk"));
  }

  public static BenchmarkDependency getAndroidJar30() {
    return new BenchmarkDependency(
        "android30jar", "lib-v30", Paths.get("third_party", "android_jar"));
  }

  // Nice name of the dependency. Must be a valid dart identifier.
  private final String name;

  // Directory name of the dependency.
  private final String directoryName;

  // Location in the R8 source tree.
  // This should never be directly exposed as its actual location will differ on golem.
  // See `getRoot` to obtain the actual dependency root.
  private final Path location;

  public BenchmarkDependency(String name, String directoryName, Path location) {
    this.name = name;
    this.directoryName = directoryName;
    this.location = location;
    String firstChar = name.substring(0, 1);
    if (!firstChar.equals(firstChar.toLowerCase(Locale.ROOT)) || name.contains("_")) {
      throw new BenchmarkConfigError("Benchmark name should use lowerCamelCase, found: " + name);
    }
  }

  public String getName() {
    return name;
  }

  public Path getTarball() {
    return location.resolve(directoryName + ".tar.gz");
  }

  public Path getSha1() {
    return location.resolve(directoryName + ".tar.gz.sha1");
  }

  public Path getRoot(BenchmarkEnvironment environment) {
    return environment.translateDependencyPath(directoryName, location);
  }
}
