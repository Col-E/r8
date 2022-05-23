// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BackwardsCompatibleSpecificationTest extends DesugaredLibraryTestBase {

  private static final List<String> RELEASES = ImmutableList.of("2.0.74");

  @Parameterized.Parameters(name = "{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(), ImmutableList.of(JDK8), RELEASES);
  }

  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final String release;

  public BackwardsCompatibleSpecificationTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      String release) {
    parameters.assertNoneRuntime();
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.release = release;
  }

  private Path getReleaseJar() {
    return Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8-releases", release, "r8lib.jar");
  }

  @Test
  public void test() throws Exception {
    ArrayList<String> command = new ArrayList<>();
    command.add("com.android.tools.r8.L8");
    command.add("--desugared-lib");
    command.add(libraryDesugaringSpecification.getSpecification().toString());
    for (Path desugarJdkLib : libraryDesugaringSpecification.getDesugarJdkLibs()) {
      command.add(desugarJdkLib.toString());
    }
    ProcessResult result = ToolHelper.runJava(getReleaseJar(), command.toArray(new String[0]));
    assertEquals(result.toString(), 0, result.exitCode);
  }
}
