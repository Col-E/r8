// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.utils.MissingDefinitionsDiagnosticTestUtils.getMissingClassMessage;
import static com.android.tools.r8.utils.MissingDefinitionsDiagnosticTestUtils.getMissingFieldMessage;
import static com.android.tools.r8.utils.MissingDefinitionsDiagnosticTestUtils.getMissingMethodMessage;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionMethodContextImpl;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
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

  public TraceReferencesDiagnosticTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

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

    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(testDiagnosticMessages)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addSourceFiles(sourceJar)
              .addTargetFiles(targetJar)
              .setConsumer(
                  new TraceReferencesCheckConsumer(TraceReferencesConsumer.emptyConsumer()))
              .build());
      fail("Unexpected success");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    DefinitionContext referencedFrom =
        DefinitionMethodContextImpl.builder()
            .setMethodContext(Reference.methodFromMethod(Source.class.getDeclaredMethod("source")))
            .setOrigin(getOrigin(Source.class))
            .build();
    testDiagnosticMessages.inspectErrors(
        diagnostic ->
            diagnostic
                .assertIsMissingDefinitionsDiagnostic()
                .assertHasMessage(
                    StringUtils.joinLines(
                        getMissingClassMessage(Target1.class, referencedFrom),
                        getMissingMethodMessage(
                            Reference.methodFromMethod(Target1.class.getDeclaredConstructor()),
                            referencedFrom),
                        getMissingClassMessage(Target2.class, referencedFrom),
                        getMissingMethodMessage(
                            Reference.methodFromMethod(Target2.class.getDeclaredConstructor()),
                            referencedFrom),
                        getMissingClassMessage(Target3.class, referencedFrom),
                        getMissingMethodMessage(
                            Reference.methodFromMethod(Target3.class.getDeclaredConstructor()),
                            referencedFrom),
                        getMissingFieldMessage(
                            Reference.fieldFromField(
                                Target.class.getDeclaredField("missingField1")),
                            referencedFrom),
                        getMissingFieldMessage(
                            Reference.fieldFromField(
                                Target.class.getDeclaredField("missingField2")),
                            referencedFrom),
                        getMissingMethodMessage(
                            Reference.methodFromMethod(
                                Target.class.getDeclaredMethod("missingMethod")),
                            referencedFrom)))
                .assertIsAllMissingClasses(Target1.class, Target2.class, Target3.class)
                .assertIsAllMissingFields(
                    Reference.fieldFromField(Target.class.getField("missingField1")),
                    Reference.fieldFromField(Target.class.getField("missingField2")))
                .assertIsAllMissingMethods(
                    Reference.methodFromMethod(Target1.class.getDeclaredConstructor()),
                    Reference.methodFromMethod(Target2.class.getDeclaredConstructor()),
                    Reference.methodFromMethod(Target3.class.getDeclaredConstructor()),
                    Reference.methodFromMethod(Target.class.getMethod("missingMethod"))));
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

    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(testDiagnosticMessages)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addSourceFiles(sourceJar)
              .addTargetFiles(targetJar)
              .setConsumer(
                  new TraceReferencesCheckConsumer(TraceReferencesConsumer.emptyConsumer()))
              .build());
      fail("Unexpected success");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    DefinitionContext referencedFrom =
        DefinitionMethodContextImpl.builder()
            .setMethodContext(Reference.methodFromMethod(Source.class.getDeclaredMethod("source")))
            .setOrigin(getOrigin(Source.class))
            .build();
    testDiagnosticMessages.inspectErrors(
        diagnostic ->
            diagnostic
                .assertIsMissingDefinitionsDiagnostic()
                .assertHasMessage(
                    StringUtils.joinLines(
                        getMissingFieldMessage(
                            Reference.fieldFromField(
                                Target.class.getDeclaredField("missingField1")),
                            referencedFrom),
                        getMissingFieldMessage(
                            Reference.fieldFromField(
                                Target.class.getDeclaredField("missingField2")),
                            referencedFrom),
                        getMissingMethodMessage(
                            Reference.methodFromMethod(
                                Target.class.getDeclaredMethod("missingMethod")),
                            referencedFrom)))
                .assertNoMissingClasses()
                .assertIsAllMissingFields(
                    Reference.fieldFromField(Target.class.getField("missingField1")),
                    Reference.fieldFromField(Target.class.getField("missingField2")))
                .assertIsAllMissingMethods(
                    Reference.methodFromMethod(Target.class.getMethod("missingMethod"))));
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

    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(testDiagnosticMessages)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addSourceFiles(sourceJar)
              .addTargetFiles(targetJar)
              .setConsumer(
                  new TraceReferencesCheckConsumer(TraceReferencesConsumer.emptyConsumer()))
              .build());
      fail("Unexpected success");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    DefinitionContext referencedFrom =
        DefinitionMethodContextImpl.builder()
            .setMethodContext(Reference.methodFromMethod(Source.class.getDeclaredMethod("source")))
            .setOrigin(getOrigin(Source.class))
            .build();
    testDiagnosticMessages.inspectErrors(
        diagnostic ->
            diagnostic
                .assertIsMissingDefinitionsDiagnostic()
                .assertHasMessage(
                    getMissingMethodMessage(
                        Reference.methodFromMethod(Target.class.getDeclaredMethod("missingMethod")),
                        referencedFrom))
                .assertNoMissingClasses()
                .assertNoMissingFields()
                .assertIsAllMissingMethods(
                    Reference.methodFromMethod(Target.class.getMethod("missingMethod"))));
  }

  static class FailingConsumer implements TraceReferencesConsumer {
    private final String where;
    private boolean reported;

    FailingConsumer(String where) {
      this.where = where;
    }

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      if (!reported && where.equals("acceptType")) {
        handler.error(new StringDiagnostic("Error in " + where));
        reported = true;
      }
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      if (!reported && where.equals("acceptField")) {
        handler.error(new StringDiagnostic("Error in " + where));
        reported = true;
      }
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      if (!reported && where.equals("acceptMethod")) {
        handler.error(new StringDiagnostic("Error in " + where));
        reported = true;
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (!reported && where.equals("finished")) {
        handler.error(new StringDiagnostic("Error in " + where));
        reported = true;
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
      TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
      try {
        TraceReferences.run(
            TraceReferencesCommand.builder(testDiagnosticMessages)
                .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
                .addSourceFiles(sourceJar)
                .addTargetFiles(targetJar)
                .setConsumer(new FailingConsumer(where))
                .build());
        fail("Unexpected success");
      } catch (CompilationFailedException e) {
        // Expected.
      }

      testDiagnosticMessages.inspectErrors(
          diagnostic ->
              diagnostic.assertIsStringDiagnostic().assertHasMessage("Error in " + where));
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
