// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesArrayTypesTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferencesArrayTypesTest(TestParameters parameters) {}

  static class MissingReferencesConsumer implements TraceReferencesConsumer {

    Set<TypeReference> tracedTypes = Sets.newHashSet();
    boolean acceptFieldCalled;
    boolean acceptMethodCalled;

    @Override
    public void acceptType(TracedClass tracedClass) {
      assertFalse(tracedClass.isMissingDefinition());
      tracedTypes.add(tracedClass.getReference());
    }

    @Override
    public void acceptField(TracedField tracedField) {
      acceptFieldCalled = true;
      assertFalse(tracedField.isMissingDefinition());
      assertEquals(
          Reference.classFromClass(Target.class), tracedField.getReference().getHolderClass());
      assertEquals("field", tracedField.getReference().getFieldName());
      assertTrue(tracedField.getReference().getFieldType().isArray());
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod) {
      acceptMethodCalled = true;
      assertFalse(tracedMethod.isMissingDefinition());
      assertEquals(
          Reference.classFromClass(Target.class), tracedMethod.getReference().getHolderClass());
      assertEquals("method", tracedMethod.getReference().getMethodName());
      assertTrue(tracedMethod.getReference().getReturnType().isArray());
      assertTrue(tracedMethod.getReference().getFormalTypes().get(0).isArray());
    }
  }

  @Test
  public void arrayTypes() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Target.class),
                ToolHelper.getClassFileForTestClass(TargetFieldType.class),
                ToolHelper.getClassFileForTestClass(TargetArgType.class),
                ToolHelper.getClassFileForTestClass(TargetReturnType.class),
                ToolHelper.getClassFileForTestClass(TargetInstantiatedType.class),
                ToolHelper.getClassFileForTestClass(TargetInstanceOfType.class),
                ToolHelper.getClassFileForTestClass(TargetCheckCastType.class),
                ToolHelper.getClassFileForTestClass(TargetArrayCloneType.class),
                ToolHelper.getClassFileForTestClass(TargetArrayCloneType2.class))
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(Source.class))
            .build();
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    MissingReferencesConsumer consumer = new MissingReferencesConsumer();

    TraceReferences.run(
        TraceReferencesCommand.builder(diagnosticsChecker)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(consumer)
            .build());

    assertEquals(
        ImmutableSet.of(
            Reference.classFromClass(Target.class),
            Reference.classFromClass(TargetFieldType.class),
            Reference.classFromClass(TargetArgType.class),
            Reference.classFromClass(TargetReturnType.class),
            Reference.classFromClass(TargetInstantiatedType.class),
            Reference.classFromClass(TargetInstanceOfType.class),
            Reference.classFromClass(TargetCheckCastType.class),
            Reference.classFromClass(TargetArrayCloneType.class),
            Reference.classFromClass(TargetArrayCloneType2.class)),
        consumer.tracedTypes);
    assertTrue(consumer.acceptFieldCalled);
    assertTrue(consumer.acceptMethodCalled);
  }

  static class TargetFieldType {}

  static class TargetArgType {}

  static class TargetReturnType {}

  static class TargetInstantiatedType {}

  static class TargetInstanceOfType {}

  static class TargetCheckCastType {}

  static class TargetArrayCloneType {}

  static class TargetArrayCloneType2 {}

  static class Target {
    public static TargetFieldType[] field;

    public static TargetReturnType[] method(TargetArgType[] arg) {
      return null;
    }
  }

  static class Source {
    public static void source() {
      Target.field = null;
      Target.method(null);
      Object x = new TargetInstantiatedType[] {};
      boolean y = null instanceof TargetInstanceOfType;
      Object z = (TargetCheckCastType) null;
      Object c = ((TargetArrayCloneType[]) null).clone();
      Object c2 = ((TargetArrayCloneType2[][][][]) null).clone();
    }
  }
}
