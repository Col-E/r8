// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApplyMappingAfterVerticalMergingMethodTest extends TestBase {

  private static final String OUTPUT = "LibraryBase::foo";
  private static final String EXPECTED_SUCCESS = StringUtils.lines(OUTPUT);

  // Base class will be vertical class merged into subclass
  public static class LibraryBase {

    @NeverInline
    public String foo() {
      return OUTPUT;
    }
  }

  // Subclass targeted via vertical class merging. The main method ensures a reference to foo.
  public static class LibrarySubclass extends LibraryBase {

    public static void main(String[] args) {
      System.out.println(new LibrarySubclass().foo());
    }
  }

  // Program class that uses LibrarySubclass and accesses foo via its main.
  public static class ProgramClass extends LibrarySubclass {

    public static void main(String[] args) {
      LibrarySubclass.main(args);
    }
  }

  // Test runner code follows.

  private static final Class<?>[] LIBRARY_CLASSES = {
    NeverMerge.class, LibraryBase.class, LibrarySubclass.class
  };

  private static final Class<?>[] PROGRAM_CLASSES = {
      ProgramClass.class
  };

  @Parameterized.Parameters(name = "{0}")
  public static Collection<TestParameters> data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().build();
  }

  @ClassRule
  public static TemporaryFolder staticTemp = ToolHelper.getTemporaryFolderForTest();

  // Cached compilation results.
  private static R8TestCompileResult cfCompiledLibrary = null;
  private static R8TestCompileResult cfCompiledProgram = null;
  private static Path cfCompiledLibraryPath = null;

  private static R8TestCompileResult dexCompiledLibrary = null;
  private static R8TestCompileResult dexCompiledProgram = null;
  private static Path dexCompiledLibraryPath = null;

  @BeforeClass
  public static void compile() throws Exception {
    if (data().stream().anyMatch(TestParameters::isCfRuntime)) {
      cfCompiledLibrary = doCompileLibrary(Backend.CF);
      cfCompiledProgram = doCompileProgram(Backend.CF, cfCompiledLibrary.getProguardMap());
      cfCompiledLibraryPath = cfCompiledLibrary.writeToZip();
    }
    if (data().stream().anyMatch(TestParameters::isDexRuntime)) {
      dexCompiledLibrary = doCompileLibrary(Backend.DEX);
      dexCompiledProgram = doCompileProgram(Backend.DEX, dexCompiledLibrary.getProguardMap());
      dexCompiledLibraryPath = dexCompiledLibrary.writeToZip();
    }
  }

  private static R8TestCompileResult doCompileLibrary(Backend backend) throws Exception {
    return testForR8(staticTemp, backend)
        .enableInliningAnnotations()
        .addProgramClasses(LIBRARY_CLASSES)
        .addKeepMainRule(LibrarySubclass.class)
        .addKeepClassAndDefaultConstructor(LibrarySubclass.class)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspect(inspector -> {
          assertThat(inspector.clazz(LibraryBase.class), not(isPresent()));
          assertThat(inspector.clazz(LibrarySubclass.class), isPresent());
        });
  }

  private static R8TestCompileResult doCompileProgram(Backend backend, String proguardMap)
      throws CompilationFailedException {
    return testForR8(staticTemp, backend)
        .noTreeShaking()
        .noMinification()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(proguardMap)
        .addLibraryClasses(LIBRARY_CLASSES)
        .setMinApi(AndroidApiLevel.B)
        .compile();
  }

  private static Path getCompiledLibraryPath(Backend backend) {
    switch (backend) {
      case CF: return cfCompiledLibraryPath;
      case DEX: return dexCompiledLibraryPath;
      default:
        throw new Unreachable();
    }
  }

  private static R8TestCompileResult getCompiledProgram(Backend backend) {
    switch (backend) {
      case CF: return cfCompiledProgram;
      case DEX: return dexCompiledProgram;
      default:
        throw new Unreachable();
    }
  }

  private TestParameters parameters;

  public ApplyMappingAfterVerticalMergingMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void runOnJvm() throws Throwable {
    Assume.assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }

  @Test
  public void b121042934() throws Exception {
    getCompiledProgram(parameters.getBackend())
        .addRunClasspathFiles(getCompiledLibraryPath(parameters.getBackend()))
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }
}
