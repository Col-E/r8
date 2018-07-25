// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateConflictField;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateConflictMethod;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateOk;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateOkSideEffects;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostConflictField;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostConflictMethod;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostOk;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostOkSideEffects;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.MoveToHostTestClass;
import com.android.tools.r8.ir.optimize.staticizer.trivial.Simple;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithGetter;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithParams;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithSideEffects;
import com.android.tools.r8.ir.optimize.staticizer.trivial.TrivialTestClass;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class ClassStaticizerTest extends TestBase {
  @Test
  public void testTrivial() throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(TrivialTestClass.class),
        ToolHelper.getClassAsBytes(Simple.class),
        ToolHelper.getClassAsBytes(SimpleWithSideEffects.class),
        ToolHelper.getClassAsBytes(SimpleWithParams.class),
        ToolHelper.getClassAsBytes(SimpleWithGetter.class),
    };
    AndroidApp app = runR8(buildAndroidApp(classes), TrivialTestClass.class);

    String javaOutput = runOnJava(TrivialTestClass.class);
    String artOutput = runOnArt(app, TrivialTestClass.class);
    assertEquals(javaOutput, artOutput);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(TrivialTestClass.class);

    assertEquals(
        Lists.newArrayList(
            "STATIC: String trivial.Simple.bar(String)",
            "STATIC: String trivial.Simple.foo()",
            "STATIC: String trivial.TrivialTestClass.next()"),
        references(clazz, "testSimple", "void"));

    assertTrue(instanceMethods(inspector.clazz(Simple.class)).isEmpty());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String trivial.SimpleWithParams.bar(String)",
            "STATIC: String trivial.SimpleWithParams.foo()",
            "STATIC: String trivial.TrivialTestClass.next()"),
        references(clazz, "testSimpleWithParams", "void"));

    assertTrue(instanceMethods(inspector.clazz(SimpleWithParams.class)).isEmpty());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String trivial.SimpleWithSideEffects.bar(String)",
            "STATIC: String trivial.SimpleWithSideEffects.foo()",
            "STATIC: String trivial.TrivialTestClass.next()",
            "trivial.SimpleWithSideEffects trivial.SimpleWithSideEffects.INSTANCE",
            "trivial.SimpleWithSideEffects trivial.SimpleWithSideEffects.INSTANCE"),
        references(clazz, "testSimpleWithSideEffects", "void"));

    assertTrue(instanceMethods(inspector.clazz(SimpleWithSideEffects.class)).isEmpty());

    // TODO(b/111832046): add support for singleton instance getters.
    assertEquals(
        Lists.newArrayList(
            "STATIC: String trivial.TrivialTestClass.next()",
            "VIRTUAL: String trivial.SimpleWithGetter.bar(String)",
            "VIRTUAL: String trivial.SimpleWithGetter.foo()",
            "trivial.SimpleWithGetter trivial.SimpleWithGetter.INSTANCE",
            "trivial.SimpleWithGetter trivial.SimpleWithGetter.INSTANCE"),
        references(clazz, "testSimpleWithGetter", "void"));

    assertFalse(instanceMethods(inspector.clazz(SimpleWithGetter.class)).isEmpty());
  }

  @Test
  public void testMoveToHost() throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(MoveToHostTestClass.class),
        ToolHelper.getClassAsBytes(HostOk.class),
        ToolHelper.getClassAsBytes(CandidateOk.class),
        ToolHelper.getClassAsBytes(HostOkSideEffects.class),
        ToolHelper.getClassAsBytes(CandidateOkSideEffects.class),
        ToolHelper.getClassAsBytes(HostConflictMethod.class),
        ToolHelper.getClassAsBytes(CandidateConflictMethod.class),
        ToolHelper.getClassAsBytes(HostConflictField.class),
        ToolHelper.getClassAsBytes(CandidateConflictField.class),
    };
    AndroidApp app = runR8(buildAndroidApp(classes), MoveToHostTestClass.class);

    String javaOutput = runOnJava(MoveToHostTestClass.class);
    String artOutput = runOnArt(app, MoveToHostTestClass.class);
    assertEquals(javaOutput, artOutput);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(MoveToHostTestClass.class);

    assertEquals(
        Lists.newArrayList(
            "STATIC: String movetohost.HostOk.bar(String)",
            "STATIC: String movetohost.HostOk.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: void movetohost.HostOk.blah(String)"),
        references(clazz, "testOk", "void"));

    assertFalse(inspector.clazz(CandidateOk.class).isPresent());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String movetohost.HostOkSideEffects.bar(String)",
            "STATIC: String movetohost.HostOkSideEffects.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "movetohost.HostOkSideEffects movetohost.HostOkSideEffects.INSTANCE",
            "movetohost.HostOkSideEffects movetohost.HostOkSideEffects.INSTANCE"),
        references(clazz, "testOkSideEffects", "void"));

    assertFalse(inspector.clazz(CandidateOkSideEffects.class).isPresent());

    assertEquals(
        Lists.newArrayList(
            "DIRECT: void movetohost.HostConflictMethod.<init>()",
            "STATIC: String movetohost.CandidateConflictMethod.bar(String)",
            "STATIC: String movetohost.CandidateConflictMethod.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "VIRTUAL: String movetohost.HostConflictMethod.bar(String)"),
        references(clazz, "testConflictMethod", "void"));

    assertTrue(inspector.clazz(CandidateConflictMethod.class).isPresent());

    assertEquals(
        Lists.newArrayList(
            "DIRECT: void movetohost.HostConflictField.<init>()",
            "STATIC: String movetohost.CandidateConflictField.bar(String)",
            "STATIC: String movetohost.CandidateConflictField.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "String movetohost.CandidateConflictField.field"),
        references(clazz, "testConflictField", "void"));

    assertTrue(inspector.clazz(CandidateConflictMethod.class).isPresent());
  }

  private List<String> instanceMethods(ClassSubject clazz) {
    assertNotNull(clazz);
    assertTrue(clazz.isPresent());
    return Streams.stream(clazz.getDexClass().methods())
        .filter(method -> !method.isStaticMethod())
        .map(method -> method.method.toSourceString())
        .sorted()
        .collect(Collectors.toList());
  }

  private List<String> references(
      ClassSubject clazz, String methodName, String retValue, String... params) {
    assertNotNull(clazz);
    assertTrue(clazz.isPresent());

    MethodSignature signature = new MethodSignature(methodName, retValue, params);
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return Streams.concat(
        filterInstructionKind(code, SgetObject.class)
            .map(Instruction::getField)
            .filter(fld -> isTypeOfInterest(fld.clazz))
            .map(DexField::toSourceString),
        filterInstructionKind(code, SputObject.class)
            .map(Instruction::getField)
            .filter(fld -> isTypeOfInterest(fld.clazz))
            .map(DexField::toSourceString),
        filterInstructionKind(code, InvokeStatic.class)
            .map(insn -> (InvokeStatic) insn)
            .map(InvokeStatic::getMethod)
            .filter(method -> isTypeOfInterest(method.holder))
            .map(method -> "STATIC: " + method.toSourceString()),
        filterInstructionKind(code, InvokeVirtual.class)
            .map(insn -> (InvokeVirtual) insn)
            .map(InvokeVirtual::getMethod)
            .filter(method -> isTypeOfInterest(method.holder))
            .map(method -> "VIRTUAL: " + method.toSourceString()),
        filterInstructionKind(code, InvokeDirect.class)
            .map(insn -> (InvokeDirect) insn)
            .map(InvokeDirect::getMethod)
            .filter(method -> isTypeOfInterest(method.holder))
            .map(method -> "DIRECT: " + method.toSourceString()))
        .map(txt -> txt.replace("java.lang.", ""))
        .map(txt -> txt.replace("com.android.tools.r8.ir.optimize.staticizer.", ""))
        .sorted()
        .collect(Collectors.toList());
  }

  private boolean isTypeOfInterest(DexType type) {
    return type.toSourceString().startsWith("com.android.tools.r8.ir.optimize.staticizer");
  }

  private AndroidApp runR8(AndroidApp app, Class mainClass) throws Exception {
    AndroidApp compiled =
        compileWithR8(app, getProguardConfig(mainClass.getCanonicalName()), this::configure);

    // Materialize file for execution.
    Path generatedDexFile = temp.getRoot().toPath().resolve("classes.jar");
    compiled.writeToZip(generatedDexFile, OutputMode.DexIndexed);

    // Run with ART.
    String artOutput = ToolHelper.runArtNoVerificationErrors(
        generatedDexFile.toString(), mainClass.getCanonicalName());

    // Compare with Java.
    ProcessResult javaResult = ToolHelper.runJava(
        ToolHelper.getClassPathForTests(), mainClass.getCanonicalName());

    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }
    assertEquals("JVM and ART output differ", javaResult.stdout, artOutput);

    return compiled;
  }

  private String getProguardConfig(String main) {
    return keepMainProguardConfiguration(main)
        + System.lineSeparator()
        + "-dontobfuscate"
        + System.lineSeparator()
        + "-allowaccessmodification";
  }

  private void configure(InternalOptions options) {
    options.enableClassInlining = false;
  }
}
