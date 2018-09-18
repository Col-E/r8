// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CheckCastRemovalTest extends JasminTestBase {

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  private final Backend backend;
  private final String CLASS_NAME = "Example";

  public CheckCastRemovalTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void exactMatch() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addDefaultConstructor();
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new Example",
        "dup",
        "invokespecial Example/<init>()V",
        "checkcast Example", // Gone
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, o -> o.enableClassInlining = false, backend);

    checkCheckCasts(app, main, null);
    checkRuntime(builder, app, CLASS_NAME);
  }

  @Test
  public void upCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // A < B < C
    ClassBuilder c = builder.addClass("C");
    c.addDefaultConstructor();
    ClassBuilder b = builder.addClass("B", "C");
    b.addDefaultConstructor();
    ClassBuilder a = builder.addClass("A", "B");
    a.addDefaultConstructor();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new A",
        "dup",
        "invokespecial A/<init>()V",
        "checkcast B", // Gone
        "checkcast C", // Gone
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-keep class A { *; }",
        "-keep class B { *; }",
        "-keep class C { *; }",
        "-dontshrink");
    AndroidApp app =
        compileWithR8(builder, pgConfigs, opts -> opts.enableClassInlining = false, backend);

    checkCheckCasts(app, main, null);
    checkRuntime(builder, app, CLASS_NAME);
  }

  @Test
  public void downCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // C < B < A
    ClassBuilder a = builder.addClass("A");
    a.addDefaultConstructor();
    ClassBuilder b = builder.addClass("B", "A");
    b.addDefaultConstructor();
    ClassBuilder c = builder.addClass("C", "B");
    c.addDefaultConstructor();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new A",
        "dup",
        "invokespecial A/<init>()V",
        "checkcast B", // Gone
        "checkcast C", // Should be kept
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-keep class A { *; }",
        "-keep class B { *; }",
        "-keep class C { *; }",
        "-dontoptimize",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null, backend);

    checkCheckCasts(app, main, "C");
    checkRuntimeException(builder, app, CLASS_NAME, "ClassCastException");
  }

  @Test
  public void bothUpAndDowncast() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 4",
        ".limit locals 1",
        "iconst_1",
        "anewarray java/lang/String", // args parameter
        "dup",
        "iconst_0",
        "ldc \"a string\"",
        "aastore",
        "checkcast [Ljava/lang/Object;",  // This upcast can be removed.
        "iconst_0",
        "aaload",
        "checkcast java/lang/String",  // Then, this downcast can be removed, too.
        "invokevirtual java/lang/String/length()I",
        "return");
    // That is, both checkcasts should be removed together or kept together.

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null, backend);

    checkCheckCasts(app, main, null);
    checkRuntime(builder, app, CLASS_NAME);
  }

  @Test
  public void nullCast() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addField("public", "fld", "Ljava/lang/String;", null);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "aconst_null",
        "checkcast Example", // Should be kept
        "getfield Example.fld Ljava/lang/String;",
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null, backend);

    checkCheckCasts(app, main, "Example");
    checkRuntimeException(builder, app, CLASS_NAME, "NullPointerException");
  }

  private void checkCheckCasts(AndroidApp app, MethodSignature main, String maybeType)
      throws ExecutionException, IOException {
    MethodSubject method = getMethodSubject(app, CLASS_NAME, main);
    assertTrue(method.isPresent());

    // Make sure there is only a single CheckCast with specified type, or no CheckCasts (if
    // maybeType == null).
    Iterator<InstructionSubject> iterator = method.iterateInstructions();
    boolean found = maybeType == null;
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (!instruction.isCheckCast()) {
        continue;
      }
      assertTrue(!found && instruction.isCheckCast(maybeType));
      found = true;
    }
  }

  private void checkRuntime(JasminBuilder builder, AndroidApp app, String className)
      throws Exception {
    String normalOutput = runOnJava(builder, className);
    String optimizedOutput;
    if (backend == Backend.DEX) {
      optimizedOutput = runOnArt(app, className);
    } else {
      assert backend == Backend.CF;
      optimizedOutput = runOnJava(app, className);
    }
    assertEquals(normalOutput, optimizedOutput);
  }

  private void checkRuntimeException(
      JasminBuilder builder, AndroidApp app, String className, String exceptionName)
      throws Exception {
    ProcessResult javaOutput = runOnJavaRaw(builder, className);
    assertEquals(1, javaOutput.exitCode);
    assertTrue(javaOutput.stderr.contains(exceptionName));

    ProcessResult output;

    if (backend == Backend.DEX) {
      output = runOnArtRaw(app, className);
    } else {
      assert backend == Backend.CF;
      output = runOnJavaRaw(app, className, Collections.emptyList());
    }

    assertEquals(1, output.exitCode);
    assertTrue(output.stderr.contains(exceptionName));
  }
}
