// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug.classinit;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Appears to have been a regression for b/65148874, but reproduction was not possible recently.
@RunWith(Parameterized.class)
public class ClassInitializationTest extends DebugTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  private final TestParameters parameters;

  public ClassInitializationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String fileName(Class<?> clazz) {
    return clazz.getSimpleName() + ".java";
  }

  @Test
  public void testStaticAssignmentInitialization() throws Throwable {
    Class<?> clazz = ClassInitializerAssignmentInitialization.class;
    final String SOURCE_FILE = fileName(clazz);
    final String CLASS = typeName(clazz);
    testForD8(parameters.getBackend())
        .addProgramClasses(clazz)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), clazz)
        .debugger(
            config ->
                runDebugTest(
                    config,
                    clazz,
                    breakpoint(CLASS, "<clinit>"),
                    run(),
                    checkLine(SOURCE_FILE, 8),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(0)),
                    checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
                    checkStaticFieldClinitSafe(CLASS, "z", null, Value.createInt(0)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 11),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(1)),
                    checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
                    checkStaticFieldClinitSafe(CLASS, "z", null, Value.createInt(0)),
                    breakpoint(CLASS, "main"),
                    run(),
                    checkStaticField(CLASS, "x", null, Value.createInt(1)),
                    checkStaticField(CLASS, "y", null, Value.createInt(0)),
                    checkStaticField(CLASS, "z", null, Value.createInt(2)),
                    run()));
  }

  @Test
  public void testBreakpointInEmptyClassInitializer() throws Throwable {
    Class<?> clazz = ClassInitializerEmpty.class;
    final String SOURCE_FILE = fileName(clazz);
    final String CLASS = typeName(clazz);
    testForD8(parameters.getBackend())
        .addProgramClasses(clazz)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), clazz)
        .debugger(
            config ->
                runDebugTest(
                    config,
                    clazz,
                    breakpoint(CLASS, "<clinit>"),
                    run(),
                    checkLine(SOURCE_FILE, 9),
                    run()));
  }

  @Test
  public void testStaticBlockInitialization() throws Throwable {
    Class<?> clazz = ClassInitializerStaticBlockInitialization.class;
    final String SOURCE_FILE = fileName(clazz);
    final String CLASS = typeName(clazz);

    testForD8(parameters.getBackend())
        .addProgramClasses(clazz)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), clazz)
        .debugger(
            config ->
                runDebugTest(
                    config,
                    CLASS,
                    breakpoint(CLASS, "<clinit>"),
                    run(),
                    checkLine(SOURCE_FILE, 13),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(0)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 14),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(1)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 15),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 18),
                    stepOver(),
                    checkLine(SOURCE_FILE, 20),
                    breakpoint(CLASS, "main"),
                    run(),
                    checkLine(SOURCE_FILE, 24),
                    checkStaticField(CLASS, "x", null, Value.createInt(3)),
                    run()));
  }

  @Test
  public void testStaticMixedInitialization() throws Throwable {
    Class<?> clazz = ClassInitializerMixedInitialization.class;
    final String SOURCE_FILE = fileName(clazz);
    final String CLASS = typeName(clazz);

    testForD8(parameters.getBackend())
        .addProgramClasses(clazz)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), clazz)
        .debugger(
            config ->
                runDebugTest(
                    config,
                    CLASS,
                    breakpoint(CLASS, "<clinit>"),
                    run(),
                    checkLine(SOURCE_FILE, 9),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(0)),
                    checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 13),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(1)),
                    checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 14),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
                    checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 17),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
                    checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
                    stepOver(),
                    checkLine(SOURCE_FILE, 19),
                    checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
                    checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(2)),
                    breakpoint(CLASS, "main"),
                    run(),
                    checkLine(SOURCE_FILE, 23),
                    checkStaticField(CLASS, "x", null, Value.createInt(3)),
                    checkStaticField(CLASS, "y", null, Value.createInt(2)),
                    run()));
  }
}
