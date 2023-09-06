// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.OffOrAuto;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class D8RunExamplesAndroidOTest extends RunExamplesAndroidOTest<D8Command.Builder> {

  class D8TestRunner extends TestRunner<D8TestRunner> {

    D8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    D8TestRunner withMinApiLevel(AndroidApiLevel minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel.getLevel()));
    }

    D8TestRunner withClasspath(Path... classpath) {
      return withBuilderTransformation(b -> b.addClasspathFiles(classpath));
    }

    @Override
    void build(Path inputFile, Path out, OutputMode mode) throws CompilationFailedException {
      D8Command.Builder builder = D8Command.builder().setOutput(out, mode);
      for (Consumer<D8Command.Builder> transformation : builderTransformations) {
        transformation.accept(builder);
      }
      builder.addLibraryFiles(
          ToolHelper.getAndroidJar(
              androidJarVersion == null ? builder.getMinApiLevel() : androidJarVersion.getLevel()));
      builder.addProgramFiles(inputFile);
      visitFiles(getLegacyClassesRoot(inputFile, packageName), builder::addProgramFiles);
      ToolHelper.runD8(builder, this::combinedOptionConsumer);
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
            .withMinApiLevel(AndroidApiLevel.K);
    try  {
      lib1.build();

      // compilation should have failed on CompilationError since A is declaring a default method.
      Assert.fail();
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingInterfaceDesugared() throws Throwable {
    // lib1: interface A { default String foo() { return "A"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(AndroidApiLevel.K);
    lib1.build();

    // lib2: interface B extends A { default String foo() { return "B"; } }
    // lib2 is compiled with full classpath
    D8TestRunner lib2 =
        test("desugaringwithmissingclasslib2", "desugaringwithmissingclasslib2", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(AndroidApiLevel.K);
    lib2.build();

    // test: class ImplementMethodsWithDefault implements A, B {} should get its foo implementation
    // from B.
    // test is compiled with incomplete classpath: lib2 is missing so ImplementMethodsWithDefault is
    // missing one of it interfaces.
    D8TestRunner test =
        test("desugaringwithmissingclasstest1", "desugaringwithmissingclasstest1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(AndroidApiLevel.K);
    test.build();

    // TODO check compilation warnings are correctly reported
    // B is missing so compiled code makes no sense, no need to test execution.
  }

  @Test
  public void testMissingInterfaceDesugared2AndroidK() throws Throwable {
    AndroidApiLevel minApi = AndroidApiLevel.K;

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
    if (!ToolHelper.artSupported() && !ToolHelper.compareAgaintsGoldenFiles()) {
      return;
    }
    if (ToolHelper.artSupported() && !ToolHelper.compareAgaintsGoldenFiles()) {
      thrown.expect(AssertionError.class);
    }
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
    AndroidApiLevel minApi = AndroidApiLevel.O;
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

    AndroidApiLevel minApi = AndroidApiLevel.K;
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

    Assume.assumeTrue(ToolHelper.artSupported() || ToolHelper.compareAgaintsGoldenFiles());

    // Missing interface B is causing the wrong method to be executed.
    if (ToolHelper.artSupported() && !ToolHelper.compareAgaintsGoldenFiles()) {
      thrown.expect(AssertionError.class);
    }
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
    AndroidApiLevel minApi = AndroidApiLevel.O;
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
    AndroidApiLevel minApi = AndroidApiLevel.K;

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
        test("desugaringwithmissingclasstest2", "desugaringwithmissingclasstest2", "N/A");

    TestBase.testForD8(temp)
        .addProgramFiles(test.getInputJar())
        .addClasspathFiles(lib1.getInputJar())
        .addClasspathFiles(lib2.getInputJar())
        .setMinApi(minApi)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyWarnings();
              diagnostics.assertWarningMessageThatMatches(
                  containsString("desugaringwithmissingclasstest2.ImplementMethodsWithDefault"));
              diagnostics.assertWarningMessageThatMatches(
                  containsString("desugaringwithmissingclasslib3.C"));
            });
  }

  @Test
  public void testMissingSuperDesugaredAndroidO() throws Throwable {
    AndroidApiLevel minApi = AndroidApiLevel.O;

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

  @Test
  public void testMissingSuperDesugaredWithLibInterfaceAndroidK() throws Throwable {
    AndroidApiLevel minApi = AndroidApiLevel.K;

    // Reference case: there's nothing to do, we should not complain.
    // Class MissingSuperImplementIterator extends C implements Iterator should not require
    // desugaring because Iterator has no default method at this API level.
    // The test is compiled with incomplete classpath: lib3 is missing so
    // MissingSuperImplementIterator is missing its super class.
    test("desugaringwithmissingclasstest6", "desugaringwithmissingclasstest6", "N/A")
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withAndroidJar(AndroidApiLevel.K)
        .withMinApiLevel(minApi)
        .build();

    // More litigious case, D8 needs to detect that the default method is part of bootclasspath.
    // Class MissingSuperImplementIterator extends C implements Iterator should not require
    // desugaring of Iterator default method because it is part of the Android API and thus is
    // declaring the default method only when default methods are supported by the runtime.
    // test is compiled with incomplete classpath: lib3 is missing so
    // MissingSuperImplementIterator is missing its super class.
    test("desugaringwithmissingclasstest6", "desugaringwithmissingclasstest6", "N/A")
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withAndroidJar(AndroidApiLevel.N)
        .withMinApiLevel(minApi)
        .build();
  }

  @Test
  public void testMissingSuperDesugaredWithProgramCrossImplementationAndroidK() throws Throwable {
    AndroidApiLevel minApi = AndroidApiLevel.K;

    // lib1: interface A { default String foo() { return "A"; } }
    //       interface A2 { default String foo2() { return "A2"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    Path lib1Dex = lib1.build();

    // lib3: class C { /* content irrelevant }
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib3Dex = lib3.build();

    // test: class C2 extends C { public String foo2() { return "C2"; } }
    //       class ImplementMethodsWithDefault extends C2 implements A, A2 {
    //            public String foo() { return "ImplementMethodsWithDefault"; }
    //       }
    // test is compiled with incomplete classpath: lib3 is missing so
    // C2 is missing its super class. But desugaring should be OK since all
    // interface methods are explicitly defined in program classes of the hierarchy.
    D8TestRunner test =
        test("desugaringwithmissingclasstest4", "desugaringwithmissingclasstest4", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path testDex = test.build();

    execute(
        "testMissingSuperDesugaredAndroidKWithCrossImplementation",
        "desugaringwithmissingclasstest4.Main",
        new Path[] {
          lib1.getInputJar(), lib3.getInputJar(), test.getInputJar()
        },
        new Path[] {lib1Dex, lib3Dex, testDex});

  }

  @Test
  public void testMissingSuperDesugaredWithClasspathCrossImplementationAndroidK() throws Throwable {
    AndroidApiLevel minApi = AndroidApiLevel.K;

    // lib1: interface A { default String foo() { return "A"; } }
    //       interface A2 { default String foo2() { return "A2"; } }
    D8TestRunner lib1 =
        test("desugaringwithmissingclasslib1", "desugaringwithmissingclasslib1", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi);
    Path lib1Dex = lib1.build();

    // lib3: class C { /* content irrelevant }
    // lib3 is compiled with full classpath
    D8TestRunner lib3 =
        test("desugaringwithmissingclasslib3", "desugaringwithmissingclasslib3", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar())
            .withMinApiLevel(minApi);
    Path lib3Dex = lib3.build();

    // lib4: class C2 extends C { public String foo2() { return "C2"; } }
    // lib4 is compiled with full classpath
    D8TestRunner lib4 =
        test("desugaringwithmissingclasslib4", "desugaringwithmissingclasslib4", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar(), lib3.getInputJar())
            .withMinApiLevel(minApi);
    Path lib4Dex = lib4.build();

    // test: class ImplementMethodsWithDefault extends C2 implements A, A2 {
    //            public String foo() { return "ImplementMethodsWithDefault"; }
    //       }
    // test is compiled with incomplete classpath: lib3 is missing so
    // C2 is missing its super class. But desugaring should be OK since all
    // interface methods are explicitly defined in program classes of the hierarchy.
    D8TestRunner test =
        test("desugaringwithmissingclasstest4", "desugaringwithmissingclasstest4", "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withClasspath(lib1.getInputJar(), lib4.getInputJar())
            .withMinApiLevel(minApi);
    Path testDex = test.build();

    execute(
        "testMissingSuperDesugaredAndroidKWithCrossImplementation",
        "desugaringwithmissingclasstest4.Main",
        new Path[] {
          lib1.getInputJar(), lib3.getInputJar(), lib4.getInputJar(), test.getInputJar()
        },
        new Path[] {lib1Dex, lib3Dex, lib4Dex, testDex});

  }

  @Override
  D8TestRunner test(String testName, String packageName, String mainClass) {
    return new D8TestRunner(testName, packageName, mainClass);
  }
}
