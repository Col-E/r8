// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexSgetObject;
import com.android.tools.r8.dex.code.DexSputObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.staticizer.dualcallinline.Candidate;
import com.android.tools.r8.ir.optimize.staticizer.dualcallinline.DualCallTest;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateConflictField;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateConflictMethod;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateOk;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateOkFieldOnly;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.CandidateOkSideEffects;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostConflictField;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostConflictMethod;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostOk;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostOkFieldOnly;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.HostOkSideEffects;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.MoveToHostFieldOnlyTestClass;
import com.android.tools.r8.ir.optimize.staticizer.movetohost.MoveToHostTestClass;
import com.android.tools.r8.ir.optimize.staticizer.trivial.Simple;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithGetter;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithLazyInit;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithParams;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithPhi;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithSideEffects;
import com.android.tools.r8.ir.optimize.staticizer.trivial.SimpleWithThrowingGetter;
import com.android.tools.r8.ir.optimize.staticizer.trivial.TrivialTestClass;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassStaticizerTest extends TestBase {
  private final TestParameters parameters;

  private static final String EXPECTED =
      StringUtils.lines(
          "Simple::bar(Simple::foo())",
          "Simple::bar(0)",
          "SimpleWithPhi$Companion::bar(SimpleWithPhi$Companion::foo()) true",
          "SimpleWithSideEffects::<clinit>()",
          "SimpleWithSideEffects::bar(SimpleWithSideEffects::foo())",
          "SimpleWithSideEffects::bar(1)",
          "SimpleWithParams::bar(SimpleWithParams::foo())",
          "SimpleWithParams::bar(2)",
          "SimpleWithGetter::bar(SimpleWithGetter::foo())",
          "SimpleWithGetter::bar(3)",
          "Simple::bar(Simple::foo())",
          "Simple::bar(4)",
          "Simple::bar(Simple::foo())",
          "Simple::bar(5)");

  private static final Class<?> main = TrivialTestClass.class;
  private static final Class<?>[] classes = {
    TrivialTestClass.class,
    Simple.class,
    SimpleWithGetter.class,
    SimpleWithLazyInit.class,
    SimpleWithParams.class,
    SimpleWithPhi.class,
    SimpleWithPhi.Companion.class,
    SimpleWithSideEffects.class,
    SimpleWithThrowingGetter.class
  };

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/112831361): support for class staticizer in CF backend.
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ClassStaticizerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testWithoutAccessModification()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(classes)
        .addKeepMainRule(main)
        .addKeepAttributes("InnerClasses", "EnclosingMethod")
        .addOptionsModification(this::configure)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testTrivial() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .addKeepMainRule(main)
            .addDontObfuscate()
            .addKeepAttributes("InnerClasses", "EnclosingMethod")
            .addOptionsModification(this::configure)
            .allowAccessModification()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(EXPECTED);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    assertEquals(
        Lists.newArrayList(
            "STATIC: String Simple.bar(String)",
            "STATIC: String Simple.foo()",
            "STATIC: String TrivialTestClass.next()"),
        references(clazz, "testSimple", "void"));

    ClassSubject simple = inspector.clazz(Simple.class);
    assertTrue(instanceMethods(simple).isEmpty());
    assertThat(simple.clinit(), not(isPresent()));

    assertEquals(
        Lists.newArrayList(
            "STATIC: String SimpleWithPhi.bar(String)",
            "STATIC: String SimpleWithPhi.foo()",
            "STATIC: String SimpleWithPhi.foo()",
            "STATIC: String TrivialTestClass.next()"),
        references(clazz, "testSimpleWithPhi", "void", "int"));

    ClassSubject simpleWithPhi = inspector.clazz(SimpleWithPhi.class);
    assertTrue(instanceMethods(simpleWithPhi).isEmpty());
    assertThat(simpleWithPhi.clinit(), not(isPresent()));

    // TODO(b/200498092): SimpleWithParams should be staticized, but due to reprocessing the
    //  instantiation of SimpleWithParams, it is marked as ineligible for staticizing.
    assertEquals(
        Lists.newArrayList(
            "STATIC: String SimpleWithParams.bar(String)",
            "STATIC: String SimpleWithParams.foo()",
            "STATIC: String TrivialTestClass.next()"),
        references(clazz, "testSimpleWithParams", "void"));

    ClassSubject simpleWithParams = inspector.clazz(SimpleWithParams.class);
    assertTrue(instanceMethods(simpleWithParams).isEmpty());
    assertThat(simpleWithParams.clinit(), isAbsent());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String SimpleWithSideEffects.bar(String)",
            "STATIC: String SimpleWithSideEffects.foo()",
            "STATIC: String TrivialTestClass.next()"),
        references(clazz, "testSimpleWithSideEffects", "void"));

    ClassSubject simpleWithSideEffects = inspector.clazz(SimpleWithSideEffects.class);
    assertTrue(instanceMethods(simpleWithSideEffects).isEmpty());
    // As its name implies, its clinit has side effects.
    assertThat(simpleWithSideEffects.clinit(), isPresent());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String SimpleWithGetter.bar(String)",
            "STATIC: String SimpleWithGetter.foo()",
            "STATIC: String TrivialTestClass.next()"),
        references(clazz, "testSimpleWithGetter", "void"));

    ClassSubject simpleWithGetter = inspector.clazz(SimpleWithGetter.class);
    assertEquals(0, instanceMethods(simpleWithGetter).size());
    assertThat(simpleWithGetter.clinit(), isAbsent());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String SimpleWithLazyInit.bar$1(String)",
            "STATIC: String SimpleWithLazyInit.foo$1()",
            "STATIC: String TrivialTestClass.next()"),
        references(clazz, "testSimpleWithThrowingGetter", "void"));

    ClassSubject simpleWithThrowingGetter = inspector.clazz(SimpleWithThrowingGetter.class);
    assertThat(simpleWithThrowingGetter, isAbsent());

    // TODO(b/143389508): add support for lazy init in singleton instance getter.
    List<String> expectedReferencesInTestSimpleWithLazyInit = new ArrayList<>();
    if (!parameters.canHaveNonReboundConstructorInvoke()) {
      Collections.addAll(
          expectedReferencesInTestSimpleWithLazyInit,
          "DIRECT: void SimpleWithLazyInit.<init>()",
          "DIRECT: void SimpleWithLazyInit.<init>()");
    }
    Collections.addAll(
        expectedReferencesInTestSimpleWithLazyInit,
        "STATIC: String SimpleWithLazyInit.bar(String)",
        "STATIC: String SimpleWithLazyInit.foo()",
        "STATIC: String TrivialTestClass.next()",
        "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
        "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
        "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
        "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
        "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
        "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE");
    assertEquals(
        expectedReferencesInTestSimpleWithLazyInit,
        references(clazz, "testSimpleWithLazyInit", "void"));

    ClassSubject simpleWithLazyInit = inspector.clazz(SimpleWithLazyInit.class);
    assertEquals(
        parameters.canHaveNonReboundConstructorInvoke(),
        instanceMethods(simpleWithLazyInit).isEmpty());
    assertThat(simpleWithLazyInit.clinit(), isPresent());
  }

  @Test
  public void testMoveToHost_fieldOnly() throws Exception {
    Class<?> main = MoveToHostFieldOnlyTestClass.class;
    Class<?>[] classes = {
        MoveToHostFieldOnlyTestClass.class,
        HostOkFieldOnly.class,
        CandidateOkFieldOnly.class
    };
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .enableSideEffectAnnotations()
            .addKeepMainRule(main)
            .allowAccessModification()
            .addDontObfuscate()
            .addOptionsModification(this::configure)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), main);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    assertEquals(
        Lists.newArrayList(),
        references(clazz, "testOk_fieldOnly", "void"));

    assertThat(inspector.clazz(CandidateOkFieldOnly.class), not(isPresent()));
  }

  @Test
  public void testMoveToHost() throws Exception {
    Class<?> main = MoveToHostTestClass.class;
    Class<?>[] classes = {
        MoveToHostTestClass.class,
        HostOk.class,
        CandidateOk.class,
        HostOkSideEffects.class,
        CandidateOkSideEffects.class,
        HostConflictMethod.class,
        CandidateConflictMethod.class,
        HostConflictField.class,
        CandidateConflictField.class
    };
    String javaOutput = runOnJava(main);
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addKeepMainRule(main)
            .allowAccessModification()
            .addDontObfuscate()
            .addOptionsModification(this::configure)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    assertEquals(
        Lists.newArrayList(
            "STATIC: String movetohost.CandidateOk.bar(String)",
            "STATIC: String movetohost.CandidateOk.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: void movetohost.CandidateOk.blah(String)"),
        references(clazz, "testOk", "void"));

    assertThat(inspector.clazz(HostOk.class), isAbsent());
    assertThat(inspector.clazz(CandidateOk.class), isPresent());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String movetohost.CandidateOkSideEffects.bar(String)",
            "STATIC: String movetohost.CandidateOkSideEffects.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()"),
        references(clazz, "testOkSideEffects", "void"));

    assertThat(inspector.clazz(HostOkSideEffects.class), isPresent());
    assertThat(inspector.clazz(CandidateOkSideEffects.class), isPresent());

    assertEquals(
        Lists.newArrayList(
            "STATIC: String movetohost.CandidateConflictMethod.bar(String)",
            "STATIC: String movetohost.CandidateConflictMethod.foo()",
            "STATIC: String movetohost.HostConflictMethod.bar(String)",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: String movetohost.MoveToHostTestClass.next()"),
        references(clazz, "testConflictMethod", "void"));

    assertThat(inspector.clazz(HostConflictMethod.class), isPresent());
    assertThat(inspector.clazz(CandidateConflictMethod.class), isPresent());

    List<String> expectedReferencesInTestConflictField = new ArrayList<>();
    if (!parameters.canHaveNonReboundConstructorInvoke()) {
      Collections.addAll(
          expectedReferencesInTestConflictField,
          "DIRECT: void movetohost.HostConflictField.<init>()");
    }
    Collections.addAll(
        expectedReferencesInTestConflictField,
        "STATIC: String movetohost.CandidateConflictField.bar(String)",
        "STATIC: String movetohost.CandidateConflictField.foo()",
        "STATIC: String movetohost.MoveToHostTestClass.next()");
    assertEquals(
        expectedReferencesInTestConflictField, references(clazz, "testConflictField", "void"));

    assertThat(inspector.clazz(CandidateConflictMethod.class), isPresent());
  }

  private List<String> instanceMethods(ClassSubject clazz) {
    assertNotNull(clazz);
    assertThat(clazz, isPresent());
    return Streams.stream(clazz.getDexProgramClass().methods())
        .filter(method -> !method.isStatic())
        .map(method -> method.getReference().toSourceString())
        .sorted()
        .collect(Collectors.toList());
  }

  private List<String> references(
      ClassSubject clazz, String methodName, String retValue, String... params) {
    assertNotNull(clazz);
    assertThat(clazz, isPresent());

    MethodSignature signature = new MethodSignature(methodName, retValue, params);
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return Streams.concat(
            filterInstructionKind(code, DexSgetObject.class)
                .map(DexInstruction::getField)
                .filter(fld -> isTypeOfInterest(fld.holder))
                .map(DexField::toSourceString),
            filterInstructionKind(code, DexSputObject.class)
                .map(DexInstruction::getField)
                .filter(fld -> isTypeOfInterest(fld.holder))
                .map(DexField::toSourceString),
            filterInstructionKind(code, DexInvokeStatic.class)
                .map(insn -> (DexInvokeStatic) insn)
                .map(DexInvokeStatic::getMethod)
                .filter(method -> isTypeOfInterest(method.holder))
                .map(method -> "STATIC: " + method.toSourceString()),
            filterInstructionKind(code, DexInvokeVirtual.class)
                .map(insn -> (DexInvokeVirtual) insn)
                .map(DexInvokeVirtual::getMethod)
                .filter(method -> isTypeOfInterest(method.holder))
                .map(method -> "VIRTUAL: " + method.toSourceString()),
            filterInstructionKind(code, DexInvokeDirect.class)
                .map(insn -> (DexInvokeDirect) insn)
                .map(DexInvokeDirect::getMethod)
                .filter(method -> isTypeOfInterest(method.holder))
                .map(method -> "DIRECT: " + method.toSourceString()))
        .map(txt -> txt.replace("java.lang.", ""))
        .map(txt -> txt.replace("com.android.tools.r8.ir.optimize.staticizer.trivial.", ""))
        .map(txt -> txt.replace("com.android.tools.r8.ir.optimize.staticizer.", ""))
        .sorted()
        .collect(Collectors.toList());
  }

  private boolean isTypeOfInterest(DexType type) {
    return type.toSourceString().startsWith("com.android.tools.r8.ir.optimize.staticizer");
  }

  private void configure(InternalOptions options) {
    options.enableClassInlining = false;
  }

  @Test
  public void dualInlinedMethodRewritten() throws Exception {
    Class<?> main = DualCallTest.class;
    Class<?>[] classes = {
        DualCallTest.class,
        Candidate.class
    };
    String javaOutput = runOnJava(main);
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableConstantArgumentAnnotations()
            .enableInliningAnnotations()
            .addKeepMainRule(main)
            .allowAccessModification()
            .addDontObfuscate()
            .addOptionsModification(this::configure)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    // Check that "calledTwice" is removed (inlined into main).
    assertThat(clazz.uniqueMethodWithOriginalName("calledTwice"), not(isPresent()));

    // Check that the two inlines of "calledTwice" is correctly rewritten.
    ClassSubject candidateClassSubject = inspector.clazz(Candidate.class);
    assertThat(candidateClassSubject, isPresent());
    assertThat(candidateClassSubject.uniqueMethodWithOriginalName("foo"), isPresent());
    assertThat(candidateClassSubject.uniqueMethodWithOriginalName("bar"), isPresent());
    assertEquals(
        Lists.newArrayList(
            "STATIC: String dualcallinline.Candidate.foo()",
            "STATIC: String dualcallinline.Candidate.foo()"),
        references(clazz, "main", "void", "java.lang.String[]"));
  }
}
