// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.defaultmethods;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class DefaultMethodsTest extends TestBase {
  private void runTest(List<String> additionalKeepRules, Consumer<DexInspector> inspection)
      throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(InterfaceWithDefaultMethods.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(ClassImplementingInterface.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(AndroidApiLevel.O.getLevel());
    // Always keep main in the test class, so the output never becomes empty.
    builder.addProguardConfiguration(ImmutableList.of(
        "-keep class " + TestClass.class.getCanonicalName() + "{",
        "  public static void main(java.lang.String[]);",
        "}",
        "-dontobfuscate"),
        Origin.unknown());
    builder.addProguardConfiguration(additionalKeepRules, Origin.unknown());
    AndroidApp app = ToolHelper.runR8(builder.build(), o -> o.enableClassInlining = false);
    inspection.accept(new DexInspector(app));
  }

  private void interfaceNotKept(DexInspector inspector) {
    assertFalse(inspector.clazz(InterfaceWithDefaultMethods.class).isPresent());
  }

  private void defaultMethodNotKept(DexInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertTrue(clazz.isPresent());
    assertFalse(clazz.method("int", "method", ImmutableList.of()).isPresent());
  }

  private void defaultMethodKept(DexInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method("int", "method", ImmutableList.of());
    assertTrue(method.isPresent());
    assertFalse(method.isAbstract());
  }

  private void defaultMethodAbstract(DexInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method("int", "method", ImmutableList.of());
    assertTrue(method.isPresent());
    assertTrue(method.isAbstract());
  }

  @Test
  public void test() throws Exception {
    runTest(ImmutableList.of(), this::interfaceNotKept);
    runTest(ImmutableList.of(
        "-keep interface " + InterfaceWithDefaultMethods.class.getCanonicalName() + "{",
        "}"
    ), this::defaultMethodNotKept);
    runTest(ImmutableList.of(
        "-keep interface " + InterfaceWithDefaultMethods.class.getCanonicalName() + "{",
        "  <methods>;",
        "}"
    ), this::defaultMethodKept);
    runTest(ImmutableList.of(
        "-keep interface " + InterfaceWithDefaultMethods.class.getCanonicalName() + "{",
        "  public int method();",
        "}"
    ), this::defaultMethodKept);
    runTest(ImmutableList.of(
        "-keep class " + ClassImplementingInterface.class.getCanonicalName() + "{",
        "  <methods>;",
        "}"
    ), this::defaultMethodNotKept);
    runTest(ImmutableList.of(
        "-keep class " + ClassImplementingInterface.class.getCanonicalName() + "{",
        "  <methods>;",
        "}",
        "-keep class " + TestClass.class.getCanonicalName() + "{",
        "  public void useInterfaceMethod();",
        "}"
    ), this::defaultMethodAbstract);
  }
}
