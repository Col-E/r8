// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.MoveException;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FoundClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.InternalOptions;
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
import org.junit.Ignore;
import org.junit.Test;

// TODO(christofferqa): Add tests to check that statically typed invocations on method handles
// continue to work after class merging. Rewriting of method handles should be carried out by
// LensCodeRewriter.rewriteDexMethodHandle.
public class ClassMergingTest extends TestBase {

  private static final Path CF_DIR =
      Paths.get(ToolHelper.BUILD_DIR).resolve("classes/examples/classmerging");
  private static final Path EXAMPLE_JAR = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR)
      .resolve("classmerging.jar");
  private static final Path EXAMPLE_KEEP = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve("classmerging").resolve("keep-rules.txt");
  private static final Path DONT_OPTIMIZE = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve("classmerging").resolve("keep-rules-dontoptimize.txt");

  private void configure(InternalOptions options) {
    options.enableClassMerging = true;
    options.enableClassInlining = false;
    options.enableMinification = false;
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

  @Test
  public void testConflictWasDetected() throws Exception {
    runR8(EXAMPLE_KEEP, this::configure);
    assertTrue(inspector.clazz("classmerging.ConflictingInterface").isPresent());
    assertTrue(inspector.clazz("classmerging.ConflictingInterfaceImpl").isPresent());
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
    runTest(main, programFiles, preservedClassNames);
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
        ImmutableList.of(),
        0,
        "invoke-direct {p0}, Lclassmerging/SuperClassWithReferencedMethod;-><init>()V",
        "return-void");
    smaliBuilder.addInstanceMethod(
        "java.lang.String",
        "referencedMethod",
        ImmutableList.of(),
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
        preservedClassNames,
        // Prevent class merging, such that the generated code would be invalid if we rewrite the
        // invoke-super instruction into an invoke-direct instruction.
        getProguardConfig(EXAMPLE_KEEP, "-keep class *"));
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
    runTest(main, programFiles, preservedClassNames);
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
    DexInspector inspector = runTest(main, programFiles, preservedClassNames);

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
  public void testNoIllegalClassAccess() throws Exception {
    String main = "classmerging.SimpleInterfaceAccessTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("SimpleInterface.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever$SimpleInterfaceImpl.class")
        };
    // SimpleInterface cannot be merged into SimpleInterfaceImpl because SimpleInterfaceImpl
    // is in a different package and is not public.
    ImmutableSet<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.SimpleInterface",
            "classmerging.SimpleInterfaceAccessTest",
            "classmerging.pkg.SimpleInterfaceImplRetriever",
            "classmerging.pkg.SimpleInterfaceImplRetriever$SimpleInterfaceImpl");
    runTest(main, programFiles, preservedClassNames);
  }

  @Ignore("b/73958515")
  @Test
  public void testNoIllegalClassAccessWithAccessModifications() throws Exception {
    // If access modifications are allowed then SimpleInterface should be merged into
    // SimpleInterfaceImpl.
    String main = "classmerging.SimpleInterfaceAccessTest";
    Path[] programFiles =
        new Path[] {
          CF_DIR.resolve("SimpleInterface.class"),
          CF_DIR.resolve("SimpleInterfaceAccessTest.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever.class"),
          CF_DIR.resolve("pkg/SimpleInterfaceImplRetriever$SimpleInterfaceImpl.class")
        };
    ImmutableSet<String> preservedClassNames =
        ImmutableSet.of(
            "classmerging.SimpleInterfaceAccessTest",
            "classmerging.pkg.SimpleInterfaceImplRetriever",
            "classmerging.pkg.SimpleInterfaceImplRetriever$SimpleInterfaceImpl");
    // Allow access modifications (and prevent SimpleInterfaceImplRetriever from being removed as
    // a result of inlining).
    runTest(
        main,
        programFiles,
        preservedClassNames,
        getProguardConfig(
            EXAMPLE_KEEP,
            "-allowaccessmodification",
            "-keep public class classmerging.pkg.SimpleInterfaceImplRetriever"));
  }

  @Ignore("b/73958515")
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
        preservedClassNames,
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
    runTest(main, programFiles, preservedClassNames);
  }

  private DexInspector runTest(String main, Path[] programFiles, Set<String> preservedClassNames)
      throws Exception {
    return runTest(main, programFiles, preservedClassNames, getProguardConfig(EXAMPLE_KEEP));
  }

  private DexInspector runTest(
      String main, Path[] programFiles, Set<String> preservedClassNames, String proguardConfig)
      throws Exception {
    return runTestOnInput(
        main, readProgramFiles(programFiles), preservedClassNames, proguardConfig);
  }

  private DexInspector runTestOnInput(
      String main, AndroidApp input, Set<String> preservedClassNames, String proguardConfig)
      throws Exception {
    AndroidApp output = compileWithR8(input, proguardConfig, this::configure);
    DexInspector inspector = new DexInspector(output);
    // Check that all classes in [preservedClassNames] are in fact preserved.
    for (String className : preservedClassNames) {
      assertTrue(
          "Class " + className + " should be present", inspector.clazz(className).isPresent());
    }
    // Check that all other classes have been removed.
    for (FoundClassSubject classSubject : inspector.allClasses()) {
      String className = classSubject.getDexClass().toSourceString();
      assertTrue(
          "Class " + className + " should be absent", preservedClassNames.contains(className));
    }
    // Check that the R8-generated code produces the same result as D8-generated code.
    assertEquals(runOnArt(compileWithD8(input), main), runOnArt(output, main));
    return inspector;
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
