// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.dex.Constants.ANDROID_K_API;
import static com.android.tools.r8.dex.Constants.ANDROID_O_API;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.OffOrAuto;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.UnaryOperator;
import org.hamcrest.core.CombinableMatcher;
import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.Test;
import org.junit.internal.matchers.ThrowableMessageMatcher;

public class D8RunExamplesAndroidOTest extends RunExamplesAndroidOTest<D8Command.Builder> {

  class D8TestRunner extends TestRunner<D8TestRunner> {

    D8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    D8TestRunner withMinApiLevel(int minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel));
    }

    D8TestRunner withClasspath(Path... classpath) {
      return withBuilderTransformation(b -> {
        try {
          return b.addClasspathFiles(classpath);
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      });
    }


    @Override
    void build(Path inputFile, Path out) throws Throwable {
      D8Command.Builder builder = D8Command.builder();
      for (UnaryOperator<D8Command.Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }
      builder.addLibraryFiles(
          Paths.get(
              ToolHelper.getAndroidJar(
                  androidJarVersion == null ? builder.getMinApiLevel() : androidJarVersion)));
      D8Command command = builder.addProgramFiles(inputFile).setOutputPath(out).build();
      try {
        ToolHelper.runD8(command, this::combinedOptionConsumer);
      } catch (Unimplemented | CompilationError | InternalCompilerError re) {
        throw re;
      } catch (RuntimeException re) {
        throw re.getCause() == null ? re : re.getCause();
      }
    }

    @Override
    D8TestRunner self() {
      return this;
    }
  }

  @Test
  public void testDefaultInInterfaceWithoutDesugaring() throws Throwable {
    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("testDefaultInInterfaceWithoutDesugaring", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Off)
            .withMinApiLevel(ANDROID_K_API);
    try  {
      lib1.build();

      // compilation should have failed on CompilationError since A is declaring a default method.
      Assert.fail();
    } catch (CompilationError | CompilationException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingInterfaceDesugared() throws Throwable {
    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(ANDROID_K_API);
    lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(ANDROID_K_API);
    lib2.build();

    // test: class ImplementMethodsWithDefault implements A, B {} should get its foo implementation
    // from B.
    // test is compiled with incomplete classpath: lib2 is missing so ImplementMethodsWithDefault is
    // missing one of it interfaces.
    D8TestRunner test =
        test("desugaringwithmissingclasstest1", "desugaringwithmissingclasstest1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(ANDROID_K_API);
    test.build();

    // TODO check compilation warnings are correctly reported
    // B is missing so compiled code makes no sense, no need to test execution.
  }

  @Test
  public void testMissingInterfaceDesugared2AndroidK() throws Throwable {
    int minApi = ANDROID_K_API;

    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    Path lib1Dex = lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib2Dex = lib2.build();

    // lib3:  class C implements A {}
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib3Dex = lib3.build();

    // test: class ImplementMethodsWithDefault extends C implements B should get its foo
    // implementation from B.
    // test is compiled with incomplete classpath: lib2 and lib3 are missing so
    // ImplementMethodsWithDefault is missing all its hierarchy.
    D8TestRunner test =
        test("desugaringwithmissingclasstest2", "desugaringwithmissingclasstest2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path testDex = test.build();
    // TODO check compilation warnings are correctly reported

    // Missing interface B is causing the wrong code to be executed.
    thrown.expect(AssertionError.class);
    execute(
        "testMissingInterfaceDesugared2AndroidK",
        "desugaringwithmissingclasstest2.Main",
        new Path[] {
          lib1.getInputJar(), lib2.getInputJar(), lib3.getInputJar(), test.getInputJar()
        },
        new Path[] {lib1Dex, lib2Dex, lib3Dex, testDex});
  }

  @Test
  public void testMissingInterfaceDesugared2AndroidO() throws Throwable {
    int minApi = ANDROID_O_API;
    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    Path lib1Dex = lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib2Dex = lib2.build();

    // lib3:  class C implements A {}
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib3Dex = lib3.build();

    // test: class ImplementMethodsWithDefault extends C implements B should get its foo
    // implementation from B.
    // test is compiled with incomplete classpath: lib2 and lib3 are missing so
    // ImplementMethodsWithDefault is missing all its hierarchy.
    D8TestRunner test =
        test("desugaringwithmissingclasstest2", "desugaringwithmissingclasstest2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path testDex = test.build();
    execute(
        "testMissingInterfaceDesugared2AndroidO",
        "desugaringwithmissingclasstest2.Main",
        new Path[] {
          lib1.getInputJar(), lib2.getInputJar(), lib3.getInputJar(), test.getInputJar()
        },
        new Path[] {lib1Dex, lib2Dex, lib3Dex, testDex});
  }

  @Test
  public void testCallToMissingSuperInterfaceDesugaredAndroidK() throws Throwable {

    int minApi = ANDROID_K_API;
    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    Path lib1Dex = lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib2Dex = lib2.build();

    // lib3:  class C implements A {}
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib3Dex = lib3.build();

    // test: class ImplementMethodsWithDefault extends C implements B
    // { String getB() { return B.super.foo(); }
    // Should be able to call implementation from B.
    // test is compiled with incomplete classpath: lib2, i.e. B definition, is missing.
    D8TestRunner test =
        test("desugaringwithmissingclasstest3", "desugaringwithmissingclasstest3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar(), lib3.getInputJar())
            .withMinApiLevel(minApi);
    Path testDex = test.build();
    // TODO check compilation warnings are correctly reported

    // Missing interface B is causing the wrong method to be executed.
    thrown.expect(AssertionError.class);
    execute(
        "testCallToMissingSuperInterfaceDesugaredAndroidK",
        "desugaringwithmissingclasstest3.Main",
        new Path[] {
          lib1.getInputJar(), lib2.getInputJar(), lib3.getInputJar(), test.getInputJar()
        },
        new Path[] {lib1Dex, lib2Dex, lib3Dex, testDex});
  }

  @Test
  public void testCallToMissingSuperInterfaceDesugaredAndroidO() throws Throwable {
    int minApi = ANDROID_O_API;
    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    Path lib1Dex = lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib2Dex = lib2.build();

    // lib3:  class C implements A {}
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib3Dex = lib3.build();

    // test: class ImplementMethodsWithDefault extends C implements B
    // { String getB() { return B.super.foo(); }
    // Should be able to call implementation from B.
    // test is compiled with incomplete classpath: lib2, i.e. B definition, is missing.
    D8TestRunner test =
        test("desugaringwithmissingclasstest3", "desugaringwithmissingclasstest3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar(), lib3.getInputJar())
            .withMinApiLevel(minApi);
    Path testDex = test.build();
    execute(
        "testCallToMissingSuperInterfaceDesugaredAndroidO",
        "desugaringwithmissingclasstest3.Main",
        new Path[] {
          lib1.getInputJar(), lib2.getInputJar(), lib3.getInputJar(), test.getInputJar()
        },
        new Path[] {lib1Dex, lib2Dex, lib3Dex, testDex});
  }

  @Test
  public void testMissingSuperDesugaredAndroidK() throws Throwable {
    int minApi = ANDROID_K_API;

    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    lib2.build();

    // lib3:  class C implements A {}
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    lib3.build();

    // test: class ImplementMethodsWithDefault extends C implements B should get its foo
    // implementation from B.
    // test is compiled with incomplete classpath: lib3 is missing so
    // ImplementMethodsWithDefault is missing its super class.
    D8TestRunner test =
        test("desugaringwithmissingclasstest2", "desugaringwithmissingclasstest2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withClasspath(lib2.getInputJar())
            .withMinApiLevel(minApi);
    thrown.expect(
        new CombinableMatcher<CompilationError>(new IsInstanceOf(CompilationError.class))
        .and(new ThrowableMessageMatcher<CompilationError>(
            new StringContains("desugaringwithmissingclasstest2.ImplementMethodsWithDefault")))
        .and(new ThrowableMessageMatcher<CompilationError>(
            new StringContains("desugaringwithmissingclasslib3.C"))));
    test.build();
  }

  @Test
  public void testMissingSuperDesugaredAndroidO() throws Throwable {
    int minApi = ANDROID_O_API;

    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    Path lib1Dex = lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib2Dex = lib2.build();

    // lib3:  class C implements A {}
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib3Dex = lib3.build();

    // test: class ImplementMethodsWithDefault extends C implements B should get its foo
    // implementation from B.
    // test is compiled with incomplete classpath: lib3 is missing so
    // ImplementMethodsWithDefault is missing its super class.
    D8TestRunner test =
        test("desugaringwithmissingclasstest2", "desugaringwithmissingclasstest2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withClasspath(lib2.getInputJar())
            .withMinApiLevel(minApi);
    Path testDex = test.build();

    execute(
        "testMissingSuperDesugaredAndroidO",
        "desugaringwithmissingclasstest2.Main",
        new Path[] {
          lib1.getInputJar(), lib2.getInputJar(), lib3.getInputJar(), test.getInputJar()
        },
        new Path[] {lib1Dex, lib2Dex, lib3Dex, testDex});
  }

  @Override
  D8TestRunner test(String testName, String packageName, String mainClass) {
    return new D8TestRunner(testName, packageName, mainClass);
  }
}
