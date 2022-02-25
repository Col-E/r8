// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import java.nio.file.Path;
import java.nio.file.Paths;

public class BenchmarkDependency {

  public static BenchmarkDependency getRuntimeJarJava8() {
    return new BenchmarkDependency("openjdk-rt-1.8", Paths.get("third_party", "openjdk"));
  }

  public static BenchmarkDependency getAndroidJar30() {
    return new BenchmarkDependency("lib-v30", Paths.get("third_party", "android_jar"));
  }

  // Directory name of the dependency.
  private final String directoryName;

  // Location in the R8 source tree.
  // This should never be directly exposed as its actual location will differ on golem.
  // See `getRoot` to obtain the actual dependency root.
  private final Path location;

  public BenchmarkDependency(String directoryName, Path location) {
    this.directoryName = directoryName;
    this.location = location;
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
