// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesSymLinkTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("link created.", "true", "Symlink:true", "true");
  private static final String EXPECTED_RESULT_DESUGARING =
      StringUtils.lines(
          "class java.lang.UnsupportedOperationException :: null",
          "true",
          "class java.lang.UnsupportedOperationException :: null",
          "true");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withCfRuntime(CfVm.JDK11)
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public FilesSymLinkTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    if (parameters.isCfRuntime()) {
      // Reference runtime, we use Jdk 11 since this is Jdk 11 desugared library, not that Jdk 8
      // behaves differently on this test.
      Assume.assumeTrue(parameters.isCfRuntime(CfVm.JDK11) && !ToolHelper.isWindows());
      testForJvm(parameters)
          .addInnerClasses(getClass())
          .run(parameters.getRuntime(), TestClass.class, "10000")
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(
            parameters.getRuntime(),
            TestClass.class,
            String.valueOf(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(
            libraryDesugaringSpecification.usesPlatformFileSystem(parameters)
                ? EXPECTED_RESULT
                : EXPECTED_RESULT_DESUGARING);
  }

  public static class TestClass {

    public static void main(String[] args) throws Throwable {
      Path target = Files.createTempFile("guinea_pig_", ".txt");
      Path link = target.getParent().resolve("link");
      try {
        Files.createLink(link, target);
        System.out.println("link created.");
      } catch (Throwable t) {
        printError(t);
      }
      Files.deleteIfExists(link);
      System.out.println((Files.exists(target)));

      Path symLink = Paths.get("symLink");
      try {
        Files.createSymbolicLink(symLink, target);
        Path path = Files.readSymbolicLink(symLink);
        System.out.println("Symlink:" + path.equals(target));
      } catch (Throwable t) {
        printError(t);
      }
      Files.deleteIfExists(symLink);
      System.out.println((Files.exists(target)));
      Files.delete(target);
    }

    private static void printError(Throwable t) {
      System.out.println(t.getClass() + " :: " + t.getMessage());
    }
  }
}
