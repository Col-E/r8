// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
/* This is regression test for b/226170842. */
public class TraceSuperMethodResolutionWithLibraryAndProgramClassTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  static class SeenReferencesConsumer implements TraceReferencesConsumer {

    private final Set<MethodReference> seenMethods = new HashSet<>();
    private final Set<MethodReference> seenMissingMethods = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {}

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      if (tracedMethod.isMissingDefinition()) {
        seenMissingMethods.add(tracedMethod.getReference());
      } else {
        seenMethods.add(tracedMethod.getReference());
      }
    }
  }

  @Test
  public void testValidResolution() throws Exception {
    Path dir = temp.newFolder().toPath();
    Path libJar =
        ZipBuilder.builder(dir.resolve("lib.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(A.class))
            .build();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(A.class))
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Main.class),
                ToolHelper.getClassFileForTestClass(ProgramClass.class))
            .build();
    SeenReferencesConsumer consumer = new SeenReferencesConsumer();
    InternalOptions internalOptions = new InternalOptions();
    internalOptions.loadAllClassDefinitions = true;
    // TODO(b/231928368): Remove this when enabled by default.
    TraceReferences.runForTesting(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addLibraryFiles(libJar)
            .addTargetFiles(targetJar)
            .addSourceFiles(sourceJar)
            .setConsumer(consumer)
            .build(),
        internalOptions);
    ImmutableSet<MethodReference> foundSet =
        ImmutableSet.of(
            Reference.methodFromMethod(A.class.getMethod("foo")),
            Reference.methodFromMethod(A.class.getConstructor()));
    assertEquals(foundSet, consumer.seenMethods);
    assertEquals(Collections.emptySet(), consumer.seenMissingMethods);
  }

  // A is added to both library and program.
  public static class A {

    public void foo() {
      System.out.println("A::foo");
    }
  }

  public static class ProgramClass extends A {

    @Override
    public void foo() {
      super.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ProgramClass().foo();
    }
  }
}
