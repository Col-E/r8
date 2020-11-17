// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesDiagnosticTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferencesDiagnosticTest(TestParameters parameters) {}

  @Test
  public void traceReferencesDiagnosticClassesFieldsAndMethods() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(Target.class),
                transformer(Target.class)
                    .removeFields(
                        (access, name, descriptor, signature, value) ->
                            name.equals("missingField1"))
                    .removeFields(
                        (access, name, descriptor, signature, value) ->
                            name.equals("missingField2"))
                    .removeMethods(
                        (access, name, descriptor, signature, exceptions) ->
                            name.equals("missingMethod"))
                    .transform())
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Source.class))
            .build();

    String prefix = "  Lcom/android/tools/r8/tracereferences/TraceReferencesDiagnosticTest$";
    List<String> snippets =
        ImmutableList.of(
            "Tracereferences found 3 classe(s), 2 field(s) and 4 method(s) without definition",
            StringUtils.lines(
                "Classe(s) without definition:",
                prefix + "Target1;",
                prefix + "Target2;",
                prefix + "Target3;"),
            StringUtils.lines(
                "Field(s) without definition:",
                prefix + "Target;missingField1:I",
                prefix + "Target;missingField2:I"),
            StringUtils.lines(
                "Method(s) without definition:",
                prefix + "Target1;<init>()V",
                prefix + "Target2;<init>()V",
                prefix + "Target3;<init>()V",
                prefix + "Target;missingMethod()V"));
    try {
      DiagnosticsChecker.checkErrorDiagnostics(
          checker -> {
            DiagnosticsChecker.checkContains(snippets, checker.errors);
            try {
              assertEquals(1, checker.errors.size());
              assertTrue(checker.errors.get(0) instanceof MissingDefinitionsDiagnostic);
              MissingDefinitionsDiagnostic diagnostic =
                  (MissingDefinitionsDiagnostic) checker.errors.get(0);
              assertEquals(
                  diagnostic.getMissingClasses(),
                  ImmutableSet.of(
                      Reference.classFromClass(Target1.class),
                      Reference.classFromClass(Target2.class),
                      Reference.classFromClass(Target3.class)));
              assertEquals(
                  diagnostic.getMissingFields(),
                  ImmutableSet.of(
                      Reference.fieldFromField(Target.class.getField("missingField1")),
                      Reference.fieldFromField(Target.class.getField("missingField2"))));
              assertEquals(
                  diagnostic.getMissingMethods(),
                  ImmutableSet.of(
                      Reference.methodFromMethod(Target1.class.getDeclaredConstructor()),
                      Reference.methodFromMethod(Target2.class.getDeclaredConstructor()),
                      Reference.methodFromMethod(Target3.class.getDeclaredConstructor()),
                      Reference.methodFromMethod(Target.class.getMethod("missingMethod"))));
            } catch (ReflectiveOperationException e) {
              fail("Unexpected exception");
            }
          },
          handler ->
              TraceReferences.run(
                  TraceReferencesCommand.builder(handler)
                      .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
                      .addSourceFiles(sourceJar)
                      .addTargetFiles(targetJar)
                      .setConsumer(TraceReferencesConsumer.emptyConsumer())
                      .build()));
      fail("Unexpected success");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void traceReferencesDiagnosticFieldsAndMethods() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Target1.class),
                ToolHelper.getClassFileForTestClass(Target2.class),
                ToolHelper.getClassFileForTestClass(Target3.class))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(Target.class),
                transformer(Target.class)
                    .removeFields(
                        (access, name, descriptor, signature, value) ->
                            name.equals("missingField1"))
                    .removeFields(
                        (access, name, descriptor, signature, value) ->
                            name.equals("missingField2"))
                    .removeMethods(
                        (access, name, descriptor, signature, exceptions) ->
                            name.equals("missingMethod"))
                    .transform())
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Source.class))
            .build();

    String prefix = "  Lcom/android/tools/r8/tracereferences/TraceReferencesDiagnosticTest$";
    List<String> snippets =
        ImmutableList.of(
            "Tracereferences found 2 field(s) and 1 method(s) without definition",
            StringUtils.lines(
                "Field(s) without definition:",
                prefix + "Target;missingField1:I",
                prefix + "Target;missingField2:I"),
            StringUtils.lines("Method(s) without definition:", prefix + "Target;missingMethod()V"));
    try {
      DiagnosticsChecker.checkErrorDiagnostics(
          checker -> {
            DiagnosticsChecker.checkContains(snippets, checker.errors);
            try {
              assertEquals(1, checker.errors.size());
              assertTrue(checker.errors.get(0) instanceof MissingDefinitionsDiagnostic);
              MissingDefinitionsDiagnostic diagnostic =
                  (MissingDefinitionsDiagnostic) checker.errors.get(0);
              assertEquals(diagnostic.getMissingClasses(), ImmutableSet.of());
              assertEquals(
                  diagnostic.getMissingFields(),
                  ImmutableSet.of(
                      Reference.fieldFromField(Target.class.getField("missingField1")),
                      Reference.fieldFromField(Target.class.getField("missingField2"))));
              assertEquals(
                  diagnostic.getMissingMethods(),
                  ImmutableSet.of(
                      Reference.methodFromMethod(Target.class.getMethod("missingMethod"))));
            } catch (ReflectiveOperationException e) {
              fail("Unexpected exception");
            }
          },
          handler ->
              TraceReferences.run(
                  TraceReferencesCommand.builder(handler)
                      .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
                      .addSourceFiles(sourceJar)
                      .addTargetFiles(targetJar)
                      .setConsumer(TraceReferencesConsumer.emptyConsumer())
                      .build()));
      fail("Unexpected success");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void traceReferencesDiagnosticMethods() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Target1.class),
                ToolHelper.getClassFileForTestClass(Target2.class),
                ToolHelper.getClassFileForTestClass(Target3.class))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(Target.class),
                transformer(Target.class)
                    .removeMethods(
                        (access, name, descriptor, signature, exceptions) ->
                            name.equals("missingMethod"))
                    .transform())
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Source.class))
            .build();

    String prefix = "  Lcom/android/tools/r8/tracereferences/TraceReferencesDiagnosticTest$";
    List<String> snippets =
        ImmutableList.of(
            "Tracereferences found 1 method(s) without definition",
            StringUtils.lines("Method(s) without definition:", prefix + "Target;missingMethod()V"));
    try {
      DiagnosticsChecker.checkErrorDiagnostics(
          checker -> {
            DiagnosticsChecker.checkContains(snippets, checker.errors);
            try {
              assertEquals(1, checker.errors.size());
              assertTrue(checker.errors.get(0) instanceof MissingDefinitionsDiagnostic);
              MissingDefinitionsDiagnostic diagnostic =
                  (MissingDefinitionsDiagnostic) checker.errors.get(0);
              assertEquals(diagnostic.getMissingClasses(), ImmutableSet.of());
              assertEquals(diagnostic.getMissingFields(), ImmutableSet.of());
              assertEquals(
                  diagnostic.getMissingMethods(),
                  ImmutableSet.of(
                      Reference.methodFromMethod(Target.class.getMethod("missingMethod"))));
            } catch (ReflectiveOperationException e) {
              fail("Unexpected exception");
            }
          },
          handler ->
              TraceReferences.run(
                  TraceReferencesCommand.builder(handler)
                      .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
                      .addSourceFiles(sourceJar)
                      .addTargetFiles(targetJar)
                      .setConsumer(TraceReferencesConsumer.emptyConsumer())
                      .build()));
      fail("Unexpected success");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  static class FailingConsumer implements TraceReferencesConsumer {
    private final String where;

    FailingConsumer(String where) {
      this.where = where;
    }

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      if (where.equals("acceptType")) {
        handler.error(new StringDiagnostic("Error in " + where));
      }
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      if (where.equals("acceptField")) {
        handler.error(new StringDiagnostic("Error in " + where));
      }
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      if (where.equals("acceptMethod")) {
        handler.error(new StringDiagnostic("Error in " + where));
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (where.equals("finished")) {
        handler.error(new StringDiagnostic("Error in " + where));
      }
    }
  }

  @Test
  public void traceReferencesConsumerError() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Target.class),
                ToolHelper.getClassFileForTestClass(Target1.class),
                ToolHelper.getClassFileForTestClass(Target2.class),
                ToolHelper.getClassFileForTestClass(Target3.class))
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Source.class))
            .build();

    for (String where : new String[] {"acceptType", "acceptField", "acceptMethod", "finished"}) {
      try {
        DiagnosticsChecker.checkErrorsContains(
            "Error in " + where,
            handler ->
                TraceReferences.run(
                    TraceReferencesCommand.builder(handler)
                        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
                        .addSourceFiles(sourceJar)
                        .addTargetFiles(targetJar)
                        .setConsumer(new FailingConsumer(where))
                        .build()));
        fail("Unexpected success");
      } catch (CompilationFailedException e) {
        // Expected.
      }
    }
  }

  static class Target1 {}

  static class Target2 {}

  static class Target3 {}

  static class Target {
    public static int missingField1;
    public static int missingField2;

    public static void missingMethod() {}
  }

  static class Source {
    public static void source() {
      new Target1();
      new Target2();
      new Target3();

      Target.missingField1 = 1;
      Target.missingField2 = 2;
      Target.missingMethod();
    }
  }
}
