// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesMissingReferencesInDexTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferencesMissingReferencesInDexTest(TestParameters parameters) {}

  static class MissingReferencesConsumer implements TraceReferencesConsumer {

    boolean acceptTypeCalled;
    boolean acceptFieldCalled;
    boolean acceptMethodCalled;

    @Override
    public void acceptType(TracedClass tracedClass) {
      acceptTypeCalled = true;
      assertEquals(Reference.classFromClass(Target.class), tracedClass.getReference());
      assertTrue(tracedClass.isMissingDefinition());
    }

    @Override
    public void acceptField(TracedField tracedField) {
      acceptFieldCalled = true;
      assertEquals(
          Reference.classFromClass(Target.class), tracedField.getReference().getHolderClass());
      assertEquals("field", tracedField.getReference().getFieldName());
      assertTrue(tracedField.isMissingDefinition());
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod) {
      acceptMethodCalled = true;
      assertEquals(
          Reference.classFromClass(Target.class), tracedMethod.getReference().getHolderClass());
      assertEquals("target", tracedMethod.getReference().getMethodName());
      assertTrue(tracedMethod.isMissingDefinition());
    }
  }

  @Test
  public void missingClassReferenced() throws Throwable {
    Path sourceDex = testForD8(Backend.DEX).addProgramClasses(Source.class).compile().writeToZip();

    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    MissingReferencesConsumer consumer = new MissingReferencesConsumer();

    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(diagnosticsChecker)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addSourceFiles(sourceDex)
              .setConsumer(consumer)
              .build());
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    assertTrue(consumer.acceptTypeCalled);
    assertTrue(consumer.acceptFieldCalled);
    assertTrue(consumer.acceptMethodCalled);
  }

  @Test
  public void missingFieldAndMethodReferenced() throws Throwable {
    Path sourceDex =
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .addProgramClassFileData(getClassWithTargetRemoved())
            .compile()
            .writeToZip();

    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    MissingReferencesConsumer consumer = new MissingReferencesConsumer();

    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(diagnosticsChecker)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addSourceFiles(sourceDex)
              .setConsumer(consumer)
              .build());
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    assertFalse(consumer.acceptTypeCalled);
    assertTrue(consumer.acceptFieldCalled);
    assertTrue(consumer.acceptMethodCalled);
  }

  private byte[] getClassWithTargetRemoved() throws IOException {
    return transformer(Target.class)
        .removeMethods((access, name, descriptor, signature, exceptions) -> name.equals("target"))
        .removeFields((access, name, descriptor, signature, value) -> name.equals("field"))
        .transform();
  }

  static class Target {
    public static int field;

    public static void target(int i) {}
  }

  static class Source {
    public static void source() {
      Target.target(Target.field);
    }
  }
}
