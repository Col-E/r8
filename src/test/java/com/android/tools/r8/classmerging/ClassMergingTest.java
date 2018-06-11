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
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FoundClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.Test;

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
    runR8(EXAMPLE_KEEP, this::configure);
    assertTrue(inspector.clazz("classmerging.SuperClassWithReferencedMethod").isPresent());
    assertTrue(inspector.clazz("classmerging.SubClassThatReferencesSuperMethod").isPresent());
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
    AndroidApp input = readProgramFiles(programFiles);
    AndroidApp output = compileWithR8(input, EXAMPLE_KEEP, this::configure);
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
}
