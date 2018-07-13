// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging;

import static com.android.tools.r8.smali.SmaliBuilder.buildCode;
import static com.android.tools.r8.utils.dexinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreForRangeOfVmVersions;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.MoveException;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.FoundClassSubject;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(christofferqa): Add tests to check that statically typed invocations on method handles
// continue to work after class merging.
@RunWith(VmTestRunner.class)
public class ClassMergingTest extends TestBase {

  private static final Path CF_DIR =
      Paths.get(ToolHelper.BUILD_DIR).resolve("test/examples/classes/classmerging");
  private static final Path JAVA8_CF_DIR =
      Paths.get(ToolHelper.BUILD_DIR).resolve("test/examplesAndroidO/classes/classmerging");
  private static final Path EXAMPLE_JAR = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR)
      .resolve("classmerging.jar");
  private static final Path EXAMPLE_KEEP = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve("classmerging").resolve("keep-rules.txt");
  private static final Path JAVA8_EXAMPLE_KEEP = Paths.get(ToolHelper.EXAMPLES_ANDROID_O_DIR)
      .resolve("classmerging").resolve("keep-rules.txt");
  private static final Path DONT_OPTIMIZE = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve("classmerging").resolve("keep-rules-dontoptimize.txt");

  private void configure(InternalOptions options) {
    options.enableClassMerging = true;
    options.enableClassInlining = false;
    options.enableMinification = false;
    options.testing.nondeterministicCycleElimination = true;
  }

  private void runR8(Path proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws IOException, ExecutionException, CompilationFailedException {
    ToolHelper.runR8(
        R8Command.builder()
            .setOutput(Paths.get(temp.getRoot().getCanonicalPath()), OutputMode.DexIndexed)
            .addProgramFiles(EXAMPLE_JAR)
            .addProguardConfigurationFiles(proguardConfig)
            .setDisableMinification(true)
            .build(),
        optionsConsumer);
    inspector = new DexInspector(
        Paths.get(temp.getRoot().getCanonicalPath()).resolve("classes.dex"));
  }

  private DexInspector inspector;

  private final List<String> CAN_BE_MERGED = ImmutableList.of(
      "classmerging.GenericInterface",
      "classmerging.GenericAbstractClass",
      "classmerging.Outer$SuperClass",
      "classmerging.SuperClass"
  );

  @Test
  public void testClassesHaveBeenMerged() throws Exception {
    runR8(EXAMPLE_KEEP, this::configure);
    // GenericInterface should be merged into GenericInterfaceImpl.
    for (String candidate : CAN_BE_MERGED) {
      assertFalse(inspector.clazz(candidate).isPresent());
    }
    assertTrue(inspector.clazz("classmerging.GenericInterfaceImpl").isPresent());
    assertTrue(inspector.clazz("classmerging.Outer$SubClass").isPresent());
    assertTrue(inspector.clazz("classmerging.SubClass").isPresent());
  }

  @Test
  public void testClassesHaveNotBeenMerged() throws Exception {
    runR8(DONT_OPTIMIZE, null);
    for (String candidate : CAN_BE_MERGED) {
      assertTrue(inspector.clazz(candidate).isPresent());
    }
  }

  // This test has a cycle in the call graph consisting of the methods A.<init> and B.<init>.
  // When nondeterministicCycleElimination is enabled, we shuffle the nodes in the call graph
  // before the cycle elimination. Therefore, it is nondeterministic if the cycle detector will
  // detect the cycle upon the call edge A.<init> to B.<init>, or B.<init> to A.<init>. In order
  // increase the likelihood that this test encounters both orderings, the test is repeated 5 times.
  // Assuming that the chance of observing one of the two orderings is 50%, this test therefore has
  // approximately 3% chance of succeeding although there is an issue.
  @Test
  public void testCallGraphCycle() throws Exception {
    String main = "classmerging.CallGraphCycleTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("CallGraphCycleTest.class"),
          CF_DIR.resolve("CallGraphCycleTest$A.class"),
          CF_DIR.resolve("CallGraphCycleTest$B.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of("classmerging.CallGraphCycleTest", "classmerging.CallGraphCycleTest$B");
    for (int i = 0; i < 5; i++) {
      runTest(main, programFiles, preservedClassNames::contains);
    }
  }

  @Test
  public void testConflictInGeneratedName() throws Exception {
    String main = "classmerging.ConflictInGeneratedNameTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("ConflictInGeneratedNameTest.class"),
          CF_DIR.resolve("ConflictInGeneratedNameTest$A.class"),
          CF_DIR.resolve("ConflictInGeneratedNameTest$B.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.ConflictInGeneratedNameTest",
            "classmerging.ConflictInGeneratedNameTest$B");
    DexInspector inspector =
        runTestOnInput(
            main,
            readProgramFiles(programFiles),
            preservedClassNames::contains,
            getProguardConfig(EXAMPLE_KEEP),
            options -> {
              configure(options);
              // Avoid that direct methods in B get inlined.
              options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
            });

    ClassSubject clazzSubject = inspector.clazz("classmerging.ConflictInGeneratedNameTest$B");
    assertThat(clazzSubject, isPresent());

    String suffix = "$classmerging$ConflictInGeneratedNameTest$A";
    List<String> EMPTY = ImmutableList.of();

    // There should be three fields.
    assertThat(clazzSubject.field("java.lang.String", "name"), isPresent());
    assertThat(clazzSubject.field("java.lang.String", "name" + suffix), isPresent());
    assertThat(clazzSubject.field("java.lang.String", "name" + suffix + "2"), isPresent());

    // The direct method "constructor$classmerging$ConflictInGeneratedNameTest$A" is processed after
    // the method "<init>" is renamed to exactly that name. Therefore the conflict should have been
    // resolved by appending [suffix] to it.
    assertThat(clazzSubject.method("void", "constructor" + suffix + suffix, EMPTY), isPresent());

    // There should be two foo's.
    assertThat(clazzSubject.method("void", "foo", EMPTY), isPresent());
    assertThat(clazzSubject.method("void", "foo" + suffix, EMPTY), isPresent());

    // There should be two bar's.
    assertThat(clazzSubject.method("void", "bar", EMPTY), isPresent());
    assertThat(clazzSubject.method("void", "bar" + suffix, EMPTY), isPresent());

    // There should be three baz's.
    assertThat(clazzSubject.method("void", "baz", EMPTY), isPresent());
    assertThat(clazzSubject.method("void", "baz" + suffix, EMPTY), isPresent());
    assertThat(clazzSubject.method("void", "baz" + suffix + "2", EMPTY), isPresent());

    // There should be three boo's.
    assertThat(clazzSubject.method("void", "boo", EMPTY), isPresent());
    assertThat(clazzSubject.method("void", "boo" + suffix, EMPTY), isPresent());
    assertThat(clazzSubject.method("void", "boo" + suffix + "2", EMPTY), isPresent());

    // There should be two getName's.
    assertThat(clazzSubject.method("java.lang.String", "getName", EMPTY), isPresent());
    assertThat(clazzSubject.method("java.lang.String", "getName" + suffix, EMPTY), isPresent());
  }

  @Test
  public void testConflictWasDetected() throws Exception {
    runR8(EXAMPLE_KEEP, this::configure);
    assertTrue(inspector.clazz("classmerging.ConflictingInterface").isPresent());
    assertTrue(inspector.clazz("classmerging.ConflictingInterfaceImpl").isPresent());
  }

  @Test
  public void testFieldCollision() throws Exception {
    String main = "classmerging.FieldCollisionTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("FieldCollisionTest.class"),
          CF_DIR.resolve("FieldCollisionTest$A.class"),
          CF_DIR.resolve("FieldCollisionTest$B.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.FieldCollisionTest",
            "classmerging.FieldCollisionTest$B");
    runTest(main, programFiles, preservedClassNames::contains);
  }

  @Test
  public void testLambdaRewriting() throws Exception {
    String main = "classmerging.LambdaRewritingTest";
    Path[] programFiles =
        new Path[] {
          JAVA8_CF_DIR.resolve("LambdaRewritingTest.class"),
          JAVA8_CF_DIR.resolve("LambdaRewritingTest$Function.class"),
          JAVA8_CF_DIR.resolve("LambdaRewritingTest$FunctionImpl.class"),
          JAVA8_CF_DIR.resolve("LambdaRewritingTest$Interface.class"),
          JAVA8_CF_DIR.resolve("LambdaRewritingTest$InterfaceImpl.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.LambdaRewritingTest",
            "classmerging.LambdaRewritingTest$Function",
            "classmerging.LambdaRewritingTest$InterfaceImpl");
    runTest(
        main,
        programFiles,
        name -> preservedClassNames.contains(name) || name.contains("$Lambda$"),
        getProguardConfig(JAVA8_EXAMPLE_KEEP));
  }

  @Test
  public void testMethodCollision() throws Exception {
    String main = "classmerging.MethodCollisionTest";
    Path[] programFiles =
        new Path[] {
            CF_DIR.resolve("MethodCollisionTest.class"),
            CF_DIR.resolve("MethodCollisionTest$A.class"),
            CF_DIR.resolve("MethodCollisionTest$B.class"),
            CF_DIR.resolve("MethodCollisionTest$C.class"),
            CF_DIR.resolve("MethodCollisionTest$D.class")
        };
    // TODO(christofferqa): Currently we do not allow merging A into B because we find a collision.
    // However, we are free to change the names of private methods, so we should handle them similar
    // to fields (i.e., we should allow merging A into B). This would also improve the performance
    // of the collision detector, because it would only have to consider non-private methods.
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.MethodCollisionTest",
            "classmerging.MethodCollisionTest$A",
            "classmerging.MethodCollisionTest$B",
            "classmerging.MethodCollisionTest$C",
            "classmerging.MethodCollisionTest$D");
    runTest(main, programFiles, preservedClassNames::contains);
  }

  @Test
  public void testNestedDefaultInterfaceMethodsTest() throws Exception {
    String main = "classmerging.NestedDefaultInterfaceMethodsTest";
    Path[] programFiles =
        new Path[] {
          JAVA8_CF_DIR.resolve("NestedDefaultInterfaceMethodsTest.class"),
          JAVA8_CF_DIR.resolve("NestedDefaultInterfaceMethodsTest$A.class"),
          JAVA8_CF_DIR.resolve("NestedDefaultInterfaceMethodsTest$B.class"),
          JAVA8_CF_DIR.resolve("NestedDefaultInterfaceMethodsTest$C.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.NestedDefaultInterfaceMethodsTest",
            "classmerging.NestedDefaultInterfaceMethodsTest$B",
            "classmerging.NestedDefaultInterfaceMethodsTest$C");
    runTest(
        main, programFiles, preservedClassNames::contains, getProguardConfig(JAVA8_EXAMPLE_KEEP));
  }

  @Test
  public void testNestedDefaultInterfaceMethodsWithCDumpTest() throws Exception {
    String main = "classmerging.NestedDefaultInterfaceMethodsTest";
    Path[] programFiles =
        new Path[] {
          JAVA8_CF_DIR.resolve("NestedDefaultInterfaceMethodsTest.class"),
          JAVA8_CF_DIR.resolve("NestedDefaultInterfaceMethodsTest$A.class"),
          JAVA8_CF_DIR.resolve("NestedDefaultInterfaceMethodsTest$B.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.NestedDefaultInterfaceMethodsTest",
            "classmerging.NestedDefaultInterfaceMethodsTest$B",
            "classmerging.NestedDefaultInterfaceMethodsTest$C");
    runTestOnInput(
        main,
        AndroidApp.builder()
            .addProgramFiles(programFiles)
            .addClassProgramData(
                NestedDefaultInterfaceMethodsTestDump.CDump.dump(), Origin.unknown())
            .build(),
        preservedClassNames::contains,
        getProguardConfig(JAVA8_EXAMPLE_KEEP));
  }

  @Test
  public void testPinnedParameterTypes() throws Exception {
    String main = "classmerging.PinnedParameterTypesTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("PinnedParameterTypesTest.class"),
          CF_DIR.resolve("PinnedParameterTypesTest$Interface.class"),
          CF_DIR.resolve("PinnedParameterTypesTest$InterfaceImpl.class"),
          CF_DIR.resolve("PinnedParameterTypesTest$TestClass.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.PinnedParameterTypesTest",
            "classmerging.PinnedParameterTypesTest$Interface",
            "classmerging.PinnedParameterTypesTest$InterfaceImpl",
            "classmerging.PinnedParameterTypesTest$TestClass");
    runTest(
        main,
        programFiles,
        preservedClassNames::contains,
        getProguardConfig(EXAMPLE_KEEP, "-keepparameternames"));
  }

  @Test
  public void testSuperCallWasDetected() throws Exception {
    String main = "classmerging.SuperCallRewritingTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("SubClassThatReferencesSuperMethod.class"),
          CF_DIR.resolve("SuperClassWithReferencedMethod.class"),
          CF_DIR.resolve("SuperCallRewritingTest.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.SubClassThatReferencesSuperMethod",
            "classmerging.SuperCallRewritingTest");
    runTest(main, programFiles, preservedClassNames::contains);
  }

  // When a subclass A has been merged into its subclass B, we rewrite invoke-super calls that hit
  // methods in A to invoke-direct calls. However, we should be careful not to transform invoke-
  // super instructions into invoke-direct instructions simply because the static target is a method
  // in the enclosing class.
  //
  // This test hand-crafts an invoke-super instruction in SubClassThatReferencesSuperMethod that
  // targets SubClassThatReferencesSuperMethod.referencedMethod. When running without class
  // merging, R8 should not rewrite the invoke-super instruction into invoke-direct.
  @Test
  public void testSuperCallNotRewrittenToDirect() throws Exception {
    String main = "classmerging.SuperCallRewritingTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("SuperClassWithReferencedMethod.class"),
          CF_DIR.resolve("SuperCallRewritingTest.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.SubClassThatReferencesSuperMethod",
            "classmerging.SuperClassWithReferencedMethod",
            "classmerging.SuperCallRewritingTest");

    // Build SubClassThatReferencesMethod.
    SmaliBuilder smaliBuilder =
        new SmaliBuilder(
            "classmerging.SubClassThatReferencesSuperMethod",
            "classmerging.SuperClassWithReferencedMethod");
    smaliBuilder.addInitializer(
        0,
        "invoke-direct {p0}, Lclassmerging/SuperClassWithReferencedMethod;-><init>()V",
        "return-void");
    smaliBuilder.addInstanceMethod(
        "java.lang.String",
        "referencedMethod",
        2,
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"In referencedMethod on SubClassThatReferencesSuperMethod\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "invoke-super {p0}, Lclassmerging/SubClassThatReferencesSuperMethod;->referencedMethod()Ljava/lang/String;",
        "move-result-object v1",
        "return-object v1");

    // Build app.
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(programFiles);
    builder.addDexProgramData(smaliBuilder.compile(), Origin.unknown());

    // Run test.
    runTestOnInput(
        main,
        builder.build(),
        preservedClassNames::contains,
        // Prevent class merging, such that the generated code would be invalid if we rewrite the
        // invoke-super instruction into an invoke-direct instruction.
        getProguardConfig(EXAMPLE_KEEP, "-keep class *"));
  }

  // The following test checks that our rewriting of invoke-super instructions in a class F works
  // if a super type of F has previously been merged into its sub class.
  //
  //   class A {}                                               <- A is kept to preserve the error
  //   class B extends A {                                         in F.invokeMethodOnA().
  //     public void m() {}
  //   }
  //   class C extends B {                                      <- C will be merged into D.
  //     @Override
  //     public void m() { "invoke-super B.m()" }
  //   }
  //   class D extends C {
  //     @Override
  //     public void m() { "invoke-super C.m()" }
  //   }
  //   class E extends D {                                      <- E will be merged into F.
  //     @Override
  //     public void m() { "invoke-super D.m()" }
  //   }
  //   class F extends E {
  //     @Override
  //     public void m() { throw new Exception() }              <- Should be dead code.
  //
  //     public void invokeMethodOnA() { "invoke-super A.m()" } <- Should yield NoSuchMethodError.
  //     public void invokeMethodOnB() { "invoke-super B.m()" }
  //     public void invokeMethodOnC() { "invoke-super C.m()" }
  //     public void invokeMethodOnD() { "invoke-super D.m()" }
  //     public void invokeMethodOnE() { "invoke-super E.m()" }
  //     public void invokeMethodOnF() { "invoke-super F.m()" }
  //   }
  @Test
  @IgnoreForRangeOfVmVersions(from = Version.V5_1_1, to = Version.V6_0_1)
  public void testSuperCallToMergedClassIsRewritten() throws Exception {
    String main = "classmerging.SuperCallToMergedClassIsRewrittenTest";
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.SuperCallToMergedClassIsRewrittenTest",
            "classmerging.A",
            "classmerging.B",
            "classmerging.D",
            "classmerging.F");

    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder classBuilder = jasminBuilder.addClass(main);
    classBuilder.addMainMethod(
        ".limit locals 1",
        ".limit stack 2",
        // Instantiate B so that it is not merged into C.
        "new classmerging/B",
        "dup",
        "invokespecial classmerging/B/<init>()V",
        "invokevirtual classmerging/B/m()V",
        // Instantiate D so that it is not merged into E.
        "new classmerging/D",
        "dup",
        "invokespecial classmerging/D/<init>()V",
        "invokevirtual classmerging/D/m()V",
        // Start the actual testing.
        "new classmerging/F",
        "dup",
        "invokespecial classmerging/F/<init>()V",
        "dup",
        "invokevirtual classmerging/F/invokeMethodOnB()V",
        "dup",
        "invokevirtual classmerging/F/invokeMethodOnC()V",
        "dup",
        "invokevirtual classmerging/F/invokeMethodOnD()V",
        "dup",
        "invokevirtual classmerging/F/invokeMethodOnE()V",
        "dup",
        "invokevirtual classmerging/F/invokeMethodOnF()V",
        "dup",
        // The method invokeMethodOnA() should yield a NoSuchMethodError.
        "try_start:",
        "invokevirtual classmerging/F/invokeMethodOnA()V",
        "try_end:",
        "return",
        "catch:",
        jasminCodeForPrinting("NoSuchMethodError"),
        "return",
        ".catch java/lang/NoSuchMethodError from try_start to try_end using catch");

    // Class A deliberately has no method m. We need to make sure that the "invoke-super A.m()"
    // instruction in class F is not rewritten into something that does not throw.
    classBuilder = jasminBuilder.addClass("classmerging.A");
    classBuilder.addDefaultConstructor();

    // Class B declares a virtual method m() that prints "In B.m()".
    classBuilder = jasminBuilder.addClass("classmerging.B", "classmerging.A");
    classBuilder.addDefaultConstructor();
    classBuilder.addVirtualMethod(
        "m", "V", ".limit locals 1", ".limit stack 2", jasminCodeForPrinting("In B.m()"), "return");

    // Class C, D, and E declare a virtual method m() that prints "In C.m()", "In D.m()", and
    // "In E.m()", respectively.
    String[][] pairs =
        new String[][] {new String[] {"C", "B"}, new String[] {"D", "C"}, new String[] {"E", "D"}};
    for (String[] pair : pairs) {
      String name = pair[0], superName = pair[1];
      classBuilder = jasminBuilder.addClass("classmerging." + name, "classmerging." + superName);
      classBuilder.addDefaultConstructor();
      classBuilder.addVirtualMethod(
          "m",
          "V",
          ".limit locals 1",
          ".limit stack 2",
          jasminCodeForPrinting("In " + name + ".m()"),
          "aload_0",
          "invokespecial classmerging/" + superName + "/m()V",
          "return");
    }

    // Class F declares a virtual method m that throws an exception (it is expected to be dead).
    //
    // Note that F is generated from smali since it is the only way to generate an instruction on
    // the form "invoke-super F.m()".
    SmaliBuilder smaliBuilder = new SmaliBuilder();
    smaliBuilder.addClass("classmerging.F", "classmerging.E");
    smaliBuilder.addDefaultConstructor();
    smaliBuilder.addInstanceMethod(
        "void",
        "m",
        1,
        "new-instance v1, Ljava/lang/Exception;",
        "invoke-direct {v1}, Ljava/lang/Exception;-><init>()V",
        "throw v1");
    // Add methods to F with an "invoke-super X.m()" instruction for X in {A, B, C, D, E, F}.
    for (String type : ImmutableList.of("A", "B", "C", "D", "E", "F")) {
      String code =
          buildCode("invoke-super {p0}, Lclassmerging/" + type + ";->m()V", "return-void");
      smaliBuilder.addInstanceMethod("void", "invokeMethodOn" + type, 0, code);
    }

    // Build app.
    AndroidApp.Builder appBuilder = AndroidApp.builder();
    appBuilder.addClassProgramData(jasminBuilder.buildClasses());
    appBuilder.addDexProgramData(smaliBuilder.compile(), Origin.unknown());

    // Run test.
    runTestOnInput(
        main,
        appBuilder.build(),
        preservedClassNames::contains,
        String.format("-keep class %s { public static void main(...); }", main));
  }

  private static String jasminCodeForPrinting(String message) {
    return buildCode(
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        String.format("ldc \"%s\"", message),
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V");
  }

  @Test
  public void testConflictingInterfaceSignatures() throws Exception {
    String main = "classmerging.ConflictingInterfaceSignaturesTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("ConflictingInterfaceSignaturesTest.class"),
          CF_DIR.resolve("ConflictingInterfaceSignaturesTest$A.class"),
          CF_DIR.resolve("ConflictingInterfaceSignaturesTest$B.class"),
          CF_DIR.resolve("ConflictingInterfaceSignaturesTest$InterfaceImpl.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.ConflictingInterfaceSignaturesTest",
            "classmerging.ConflictingInterfaceSignaturesTest$InterfaceImpl");
    runTest(main, programFiles, preservedClassNames::contains);
  }

  // If an exception class A is merged into another exception class B, then all exception tables
  // should be updated, and class A should be removed entirely.
  @Test
  public void testExceptionTables() throws Exception {
    String main = "classmerging.ExceptionTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("ExceptionTest.class"),
          CF_DIR.resolve("ExceptionTest$ExceptionA.class"),
          CF_DIR.resolve("ExceptionTest$ExceptionB.class"),
          CF_DIR.resolve("ExceptionTest$Exception1.class"),
          CF_DIR.resolve("ExceptionTest$Exception2.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.ExceptionTest",
            "classmerging.ExceptionTest$ExceptionB",
            "classmerging.ExceptionTest$Exception2");
    DexInspector inspector = runTest(main, programFiles, preservedClassNames::contains);

    ClassSubject mainClass = inspector.clazz(main);
    assertThat(mainClass, isPresent());

    MethodSubject mainMethod =
        mainClass.method("void", "main", ImmutableList.of("java.lang.String[]"));
    assertThat(mainMethod, isPresent());

    // Check that the second catch handler has been removed.
    DexCode code = mainMethod.getMethod().getCode().asDexCode();
    int numberOfMoveExceptionInstructions = 0;
    for (Instruction instruction : code.instructions) {
      if (instruction instanceof MoveException) {
        numberOfMoveExceptionInstructions++;
      }
    }
    assertEquals(2, numberOfMoveExceptionInstructions);
  }

  @Test
  public void testMergeDefaultMethodIntoClass() throws Exception {
    String main = "classmerging.MergeDefaultMethodIntoClassTest";
    Path[] programFiles =
        new Path[] {
          JAVA8_CF_DIR.resolve("MergeDefaultMethodIntoClassTest.class"),
          JAVA8_CF_DIR.resolve("MergeDefaultMethodIntoClassTest$A.class"),
          JAVA8_CF_DIR.resolve("MergeDefaultMethodIntoClassTest$B.class")
        };
    ImmutableSet<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.MergeDefaultMethodIntoClassTest",
            "classmerging.MergeDefaultMethodIntoClassTest$B");
    AndroidApp app =
        AndroidApp.builder()
            .addProgramFiles(programFiles)
            .addLibraryFile(ToolHelper.getAndroidJar(AndroidApiLevel.O))
            .build();

    // Sanity check that there is actually an invoke-interface instruction in the input. We need
    // to make sure that this invoke-interface instruction is translated to invoke-virtual after
    // the classes A and B are merged.
    DexInspector inputInspector = new DexInspector(app);
    ClassSubject clazz = inputInspector.clazz("classmerging.MergeDefaultMethodIntoClassTest");
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("void", "main", ImmutableList.of("java.lang.String[]"));
    assertThat(method, isPresent());
    assertThat(
        method.getMethod().getCode().asJarCode().toString(),
        containsString("INVOKEINTERFACE classmerging/MergeDefaultMethodIntoClassTest$A.f"));

    runTestOnInput(main, app, preservedClassNames::contains, getProguardConfig(JAVA8_EXAMPLE_KEEP));
  }

  @Test
  public void testNativeMethod() throws Exception {
    String main = "classmerging.ClassWithNativeMethodTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("ClassWithNativeMethodTest.class"),
          CF_DIR.resolve("ClassWithNativeMethodTest$A.class"),
          CF_DIR.resolve("ClassWithNativeMethodTest$B.class")
        };
    // Ensures that the class A with a native method has not been merged into its subclass B.
    ImmutableSet<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.ClassWithNativeMethodTest",
            "classmerging.ClassWithNativeMethodTest$A",
            "classmerging.ClassWithNativeMethodTest$B");
    runTest(main, programFiles, preservedClassNames::contains);
  }

  @Test
  public void testNoIllegalClassAccess() throws Exception {
    String main = "classmerging.SimpleInterfaceAccessTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("SimpleInterfaceAccessTest.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest$SimpleInterface.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest$OtherSimpleInterface.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest$OtherSimpleInterfaceImpl.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever$SimpleInterfaceImpl.class")
        };
    // SimpleInterface cannot be merged into SimpleInterfaceImpl because SimpleInterfaceImpl
    // is in a different package and is not public.
    ImmutableSet<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.SimpleInterfaceAccessTest",
            "classmerging.SimpleInterfaceAccessTest$SimpleInterface",
            "classmerging.SimpleInterfaceAccessTest$OtherSimpleInterface",
            "classmerging.SimpleInterfaceAccessTest$OtherSimpleInterfaceImpl",
            "classmerging.pkg.SimpleInterfaceImplRetriever",
            "classmerging.pkg.SimpleInterfaceImplRetriever$SimpleInterfaceImpl");
    runTest(main, programFiles, preservedClassNames::contains);
  }

  @Test
  public void testNoIllegalClassAccessWithAccessModifications() throws Exception {
    // If access modifications are allowed then SimpleInterface should be merged into
    // SimpleInterfaceImpl.
    String main = "classmerging.SimpleInterfaceAccessTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("SimpleInterfaceAccessTest.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest$SimpleInterface.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest$OtherSimpleInterface.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest$OtherSimpleInterfaceImpl.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever$SimpleInterfaceImpl.class")
        };
    ImmutableSet<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.SimpleInterfaceAccessTest",
            "classmerging.SimpleInterfaceAccessTest$OtherSimpleInterfaceImpl",
            "classmerging.pkg.SimpleInterfaceImplRetriever",
            "classmerging.pkg.SimpleInterfaceImplRetriever$SimpleInterfaceImpl");
    // Allow access modifications (and prevent SimpleInterfaceImplRetriever from being removed as
    // a result of inlining).
    runTest(
        main,
        programFiles,
        preservedClassNames::contains,
        getProguardConfig(
            EXAMPLE_KEEP,
            "-allowaccessmodification",
            "-keep public class classmerging.pkg.SimpleInterfaceImplRetriever"));
  }

  // TODO(christofferqa): This test checks that the invoke-super instruction in B is not rewritten
  // into an invoke-direct instruction after it gets merged into class C. We should add a test that
  // checks that this works with and without inlining of the method B.m().
  @Test
  public void testRewritePinnedMethod() throws Exception {
    String main = "classmerging.RewritePinnedMethodTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("RewritePinnedMethodTest.class"),
          CF_DIR.resolve("RewritePinnedMethodTest$A.class"),
          CF_DIR.resolve("RewritePinnedMethodTest$B.class"),
          CF_DIR.resolve("RewritePinnedMethodTest$C.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.RewritePinnedMethodTest",
            "classmerging.RewritePinnedMethodTest$A",
            "classmerging.RewritePinnedMethodTest$C");
    runTest(
        main,
        programFiles,
        preservedClassNames::contains,
        getProguardConfig(
            EXAMPLE_KEEP, "-keep class classmerging.RewritePinnedMethodTest$A { *; }"));
  }

  @Test
  public void testTemplateMethodPattern() throws Exception {
    String main = "classmerging.TemplateMethodTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("TemplateMethodTest.class"),
          CF_DIR.resolve("TemplateMethodTest$AbstractClass.class"),
          CF_DIR.resolve("TemplateMethodTest$AbstractClassImpl.class")
        };
    Set<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.TemplateMethodTest", "classmerging.TemplateMethodTest$AbstractClassImpl");
    runTest(main, programFiles, preservedClassNames::contains);
  }

  private DexInspector runTest(
      String main, Path[] programFiles, Predicate<String> preservedClassNames) throws Exception {
    return runTest(main, programFiles, preservedClassNames, getProguardConfig(EXAMPLE_KEEP));
  }

  private DexInspector runTest(
      String main,
      Path[] programFiles,
      Predicate<String> preservedClassNames,
      String proguardConfig)
      throws Exception {
    return runTestOnInput(
        main, readProgramFiles(programFiles), preservedClassNames, proguardConfig);
  }

  private DexInspector runTestOnInput(
      String main, AndroidApp input, Predicate<String> preservedClassNames, String proguardConfig)
      throws Exception {
    return runTestOnInput(main, input, preservedClassNames, proguardConfig, this::configure);
  }

  private DexInspector runTestOnInput(
      String main,
      AndroidApp input,
      Predicate<String> preservedClassNames,
      String proguardConfig,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    AndroidApp output = compileWithR8(input, proguardConfig, optionsConsumer);
    DexInspector inputInspector = new DexInspector(input);
    DexInspector outputInspector = new DexInspector(output);
    // Check that all classes in [preservedClassNames] are in fact preserved.
    for (FoundClassSubject classSubject : inputInspector.allClasses()) {
      String className = classSubject.getOriginalName();
      boolean shouldBePresent = preservedClassNames.test(className);
      assertEquals(
          "Class " + className + " should be " + (shouldBePresent ? "present" : "absent"),
          shouldBePresent,
          outputInspector.clazz(className).isPresent());
    }
    // Check that the R8-generated code produces the same result as D8-generated code.
    assertEquals(runOnArt(compileWithD8(input), main), runOnArt(output, main));
    return outputInspector;
  }

  private String getProguardConfig(Path path, String... additionalRules) throws IOException {
    StringBuilder builder = new StringBuilder();
    for (String line : Files.readAllLines(path)) {
      builder.append(line);
      builder.append(System.lineSeparator());
    }
    for (String rule : additionalRules) {
      builder.append(rule);
      builder.append(System.lineSeparator());
    }
    return builder.toString();
  }
}
