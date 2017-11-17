// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.D8Output;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DexVersionTests {

  private static final Path ARITHMETIC_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "/arithmetic.jar");

  private static final Path ARRAYACCESS_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "/arrayaccess.jar");

  @Rule public TemporaryFolder defaultApiFolder1 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder defaultApiFolder2 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidOApiFolder1 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidOApiFolder2 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidNApiFolder1 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidNApiFolder2 = ToolHelper.getTemporaryFolderForTest();

  @Before
  public void compileVersions() throws Exception {
    D8Command.Builder arithmeticBuilder = D8Command.builder().addProgramFiles(ARITHMETIC_JAR);
    D8Command.Builder arrayAccessBuilder = D8Command.builder().addProgramFiles(ARRAYACCESS_JAR);
    D8Output output = D8.run(arrayAccessBuilder.build());
    output.write(defaultApiFolder1.getRoot().toPath());
    output = D8.run(arrayAccessBuilder.setMinApiLevel(AndroidApiLevel.O.getLevel()).build());
    output.write(androidOApiFolder1.getRoot().toPath());
    output = D8.run(arrayAccessBuilder.setMinApiLevel(AndroidApiLevel.N.getLevel()).build());
    output.write(androidNApiFolder1.getRoot().toPath());
    output = D8.run(arithmeticBuilder.build());
    output.write(defaultApiFolder2.getRoot().toPath());
    output = D8.run(arithmeticBuilder.setMinApiLevel(AndroidApiLevel.O.getLevel()).build());
    output.write(androidOApiFolder2.getRoot().toPath());
    output = D8.run(arithmeticBuilder.setMinApiLevel(AndroidApiLevel.N.getLevel()).build());
    output.write(androidNApiFolder2.getRoot().toPath());
  }

  private Path default1() {
    return defaultApiFolder1.getRoot().toPath().resolve("classes.dex");
  }

  private Path default2() {
    return defaultApiFolder2.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidO1() {
    return androidOApiFolder1.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidO2() {
    return androidOApiFolder2.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidN1() {
    return androidNApiFolder1.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidN2() {
    return androidNApiFolder2.getRoot().toPath().resolve("classes.dex");
  }

  @Test
  public void mergeCompatibleVersions() throws Exception {
    // Verify that we can merge between all versions when no explicit min sdk version is set.
    D8.run(D8Command.builder().addProgramFiles(default1()).addProgramFiles(default2()).build());
    D8.run(D8Command.builder().addProgramFiles(default1()).addProgramFiles(androidO2()).build());
    D8.run(D8Command.builder().addProgramFiles(default1()).addProgramFiles(androidN2()).build());
    D8.run(D8Command.builder().addProgramFiles(androidO1()).addProgramFiles(androidN2()).build());
    D8.run(D8Command.builder().addProgramFiles(androidO1()).addProgramFiles(androidO2()).build());
    D8.run(D8Command.builder().addProgramFiles(androidN1()).addProgramFiles(androidN2()).build());
    // Verify that we can merge between all version when api version is explicitly
    // set to Android O.
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.O.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(default2())
            .build());
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.O.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(androidO2())
            .build());
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.O.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(androidN2())
            .build());
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.O.getLevel())
            .addProgramFiles(androidO1())
            .addProgramFiles(androidN2())
            .build());
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.O.getLevel())
            .addProgramFiles(androidO1())
            .addProgramFiles(androidO2())
            .build());
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.O.getLevel())
            .addProgramFiles(androidN1())
            .addProgramFiles(androidN2())
            .build());
    // Verify that we can merge up to version N when api version is explicitly set to
    // Android N.
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.N.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(default2())
            .build());
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.N.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(androidN2())
            .build());
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.N.getLevel())
            .addProgramFiles(androidN1())
            .addProgramFiles(androidN2())
            .build());
    // Verify that we can merge default api version when api version is explicitly set to
    // Android K.
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(default2())
            .build());
  }

  @Test(expected = CompilationError.class)
  public void mergeErrorVersionNWithVersionOInput() throws Exception {
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.N.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(androidO2())
            .build());
  }

  @Test(expected = CompilationError.class)
  public void mergeErrorVersionKWithVersionOInput() throws Exception {
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(androidO2())
            .build());
  }

  @Test(expected = CompilationError.class)
  public void mergeErrorVersionKWithVersionNInput() throws Exception {
    D8.run(
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .addProgramFiles(default1())
            .addProgramFiles(androidN2())
            .build());
  }
}
