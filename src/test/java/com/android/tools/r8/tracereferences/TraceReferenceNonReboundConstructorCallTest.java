// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferenceNonReboundConstructorCallTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferenceNonReboundConstructorCallTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  static class MissingReferencesConsumer implements TraceReferencesConsumer {

    private Set<MethodReference> foundMethods = new HashSet<>();
    private Set<MethodReference> missingMethods = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {}

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      if (tracedMethod.isMissingDefinition()) {
        missingMethods.add(tracedMethod.getReference());
      } else {
        foundMethods.add(tracedMethod.getReference());
      }
    }
  }

  @Test
  public void testCf() throws Throwable {
    MissingReferencesConsumer consumer =
        runTest(
            ZipBuilder.builder(temp.newFile("source.jar").toPath())
                .addFilesRelative(
                    ToolHelper.getClassPathForTests(),
                    ToolHelper.getClassFileForTestClass(Main.class))
                .build());
    ImmutableSet<MethodReference> expectedFoundMethods = ImmutableSet.of();
    ImmutableSet<MethodReference> expectedMissingMethods =
        ImmutableSet.of(MethodReferenceUtils.instanceConstructor(SubClass.class));
    assertEquals(expectedFoundMethods, consumer.foundMethods);
    assertEquals(expectedMissingMethods, consumer.missingMethods);
  }

  @Test
  public void testDex() throws Throwable {
    MissingReferencesConsumer consumer =
        runTest(
            testForD8(Backend.DEX)
                .addProgramClasses(Main.class)
                .release()
                .setMinApi(AndroidApiLevel.B)
                .compile()
                .writeToZip());
    ImmutableSet<MethodReference> expectedFoundMethods =
        ImmutableSet.of(MethodReferenceUtils.instanceConstructor(SuperClass.class));
    ImmutableSet<MethodReference> expectedMissingMethods = ImmutableSet.of();
    assertEquals(expectedFoundMethods, consumer.foundMethods);
    assertEquals(expectedMissingMethods, consumer.missingMethods);
  }

  private MissingReferencesConsumer runTest(Path sourceFile) throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(SuperClass.class))
            .addBytes(
                binaryName(SubClass.class) + CLASS_EXTENSION,
                transformer(SubClass.class).removeMethodsWithName("<init>").transform())
            .build();
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    MissingReferencesConsumer consumer = new MissingReferencesConsumer();
    TraceReferences.run(
        TraceReferencesCommand.builder(diagnosticsChecker)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addSourceFiles(sourceFile)
            .addTargetFiles(targetJar)
            .setConsumer(consumer)
            .build());
    return consumer;
  }

  static class SuperClass {}

  static class SubClass extends SuperClass {

    // Removed by transformer.
    SubClass() {}
  }

  public static class Main {

    public static void main(String[] args) {
      new SubClass();
    }
  }
}
