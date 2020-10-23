// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SputObject;
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
    NeverInline.class,
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
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testTrivial() throws Exception {
    SingleTestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .addKeepMainRule(main)
            .noMinification()
            .addKeepAttributes("InnerClasses", "EnclosingMethod")
            .addOptionsModification(this::configure)
            .allowAccessModification()
            .setMinApi(parameters.getApiLevel())
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

    assertEquals(
        Lists.newArrayList(
            "STATIC: String SimpleWithParams.bar(String)",
            "STATIC: String SimpleWithParams.foo()",
            "STATIC: String TrivialTestClass.next()"),
        references(clazz, "testSimpleWithParams", "void"));

    ClassSubject simpleWithParams = inspector.clazz(SimpleWithParams.class);
    assertTrue(instanceMethods(simpleWithParams).isEmpty());
    assertThat(simpleWithParams.clinit(), not(isPresent()));

    assertEquals(
        Lists.newArrayList(
            "STATIC: String SimpleWithSideEffects.bar(String)",
            "STATIC: String SimpleWithSideEffects.foo()",
            "STATIC: String TrivialTestClass.next()",
            "SimpleWithSideEffects SimpleWithSideEffects.INSTANCE"),
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
    assertTrue(instanceMethods(simpleWithGetter).isEmpty());
    assertThat(simpleWithGetter.clinit(), not(isPresent()));

    assertEquals(
        Lists.newArrayList(
            "STATIC: SimpleWithThrowingGetter SimpleWithThrowingGetter.getInstance()",
            "STATIC: SimpleWithThrowingGetter SimpleWithThrowingGetter.getInstance()",
            "STATIC: String TrivialTestClass.next()",
            "SimpleWithThrowingGetter SimpleWithThrowingGetter.INSTANCE",
            "VIRTUAL: String SimpleWithThrowingGetter.bar(String)",
            "VIRTUAL: String SimpleWithThrowingGetter.foo()"),
        references(clazz, "testSimpleWithThrowingGetter", "void"));

    ClassSubject simpleWithThrowingGetter = inspector.clazz(SimpleWithThrowingGetter.class);
    assertFalse(instanceMethods(simpleWithThrowingGetter).isEmpty());
    assertThat(simpleWithThrowingGetter.clinit(), isPresent());

    // TODO(b/143389508): add support for lazy init in singleton instance getter.
    assertEquals(
        Lists.newArrayList(
            "DIRECT: void SimpleWithLazyInit.<init>()",
            "DIRECT: void SimpleWithLazyInit.<init>()",
            "STATIC: String TrivialTestClass.next()",
            "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
            "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
            "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
            "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
            "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
            "SimpleWithLazyInit SimpleWithLazyInit.INSTANCE",
            "VIRTUAL: String SimpleWithLazyInit.bar(String)",
            "VIRTUAL: String SimpleWithLazyInit.foo()"),
        references(clazz, "testSimpleWithLazyInit", "void"));

    ClassSubject simpleWithLazyInit = inspector.clazz(SimpleWithLazyInit.class);
    assertFalse(instanceMethods(simpleWithLazyInit).isEmpty());
    assertThat(simpleWithLazyInit.clinit(), not(isPresent()));
  }

  @Test
  public void testMoveToHost_fieldOnly() throws Exception {
    Class<?> main = MoveToHostFieldOnlyTestClass.class;
    Class<?>[] classes = {
        NeverInline.class,
        MoveToHostFieldOnlyTestClass.class,
        HostOkFieldOnly.class,
        CandidateOkFieldOnly.class
    };
    SingleTestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .enableSideEffectAnnotations()
            .addKeepMainRule(main)
            .allowAccessModification()
            .noMinification()
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getApiLevel())
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
        NeverInline.class,
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
            .enableMemberValuePropagationAnnotations()
            .addKeepMainRule(main)
            .allowAccessModification()
            .noMinification()
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    assertEquals(
        Lists.newArrayList(
            "STATIC: String movetohost.HostOk.bar(String)",
            "STATIC: String movetohost.HostOk.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: void movetohost.HostOk.blah(String)"),
        references(clazz, "testOk", "void"));

    assertThat(inspector.clazz(CandidateOk.class), not(isPresent()));

    assertEquals(
        Lists.newArrayList(
            "STATIC: String movetohost.HostOkSideEffects.bar(String)",
            "STATIC: String movetohost.HostOkSideEffects.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "movetohost.HostOkSideEffects movetohost.HostOkSideEffects.INSTANCE"),
        references(clazz, "testOkSideEffects", "void"));

    assertThat(inspector.clazz(CandidateOkSideEffects.class), not(isPresent()));

    assertEquals(
        Lists.newArrayList(
            "DIRECT: void movetohost.HostConflictMethod.<init>()",
            "STATIC: String movetohost.CandidateConflictMethod.bar(String)",
            "STATIC: String movetohost.CandidateConflictMethod.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "VIRTUAL: String movetohost.HostConflictMethod.bar(String)"),
        references(clazz, "testConflictMethod", "void"));

    assertThat(inspector.clazz(CandidateConflictMethod.class), isPresent());

    assertEquals(
        Lists.newArrayList(
            "DIRECT: void movetohost.HostConflictField.<init>()",
            "STATIC: String movetohost.CandidateConflictField.bar(String)",
            "STATIC: String movetohost.CandidateConflictField.foo()",
            "STATIC: String movetohost.MoveToHostTestClass.next()",
            "String movetohost.CandidateConflictField.field"),
        references(clazz, "testConflictField", "void"));

    assertThat(inspector.clazz(CandidateConflictMethod.class), isPresent());
  }

  private List<String> instanceMethods(ClassSubject clazz) {
    assertNotNull(clazz);
    assertThat(clazz, isPresent());
    return Streams.stream(clazz.getDexProgramClass().methods())
        .filter(method -> !method.isStatic())
        .map(method -> method.method.toSourceString())
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
        filterInstructionKind(code, SgetObject.class)
            .map(Instruction::getField)
            .filter(fld -> isTypeOfInterest(fld.holder))
            .map(DexField::toSourceString),
        filterInstructionKind(code, SputObject.class)
            .map(Instruction::getField)
            .filter(fld -> isTypeOfInterest(fld.holder))
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
    options.enableUninstantiatedTypeOptimization = false;
  }

  @Test
  public void dualInlinedMethodRewritten() throws Exception {
    Class<?> main = DualCallTest.class;
    Class<?>[] classes = {
        DualCallTest.class,
        Candidate.class
    };
    String javaOutput = runOnJava(main);
    SingleTestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .addKeepMainRule(main)
            .allowAccessModification()
            .noMinification()
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    // Check that "calledTwice" is removed (inlined into main).
    assertThat(clazz.uniqueMethodWithName("calledTwice"), not(isPresent()));

    // Check that the two inlines of "calledTwice" is correctly rewritten.
    assertThat(clazz.uniqueMethodWithName("foo"), isPresent());
    assertThat(clazz.uniqueMethodWithName("bar"), isPresent());
    assertEquals(
        Lists.newArrayList(
            "STATIC: String dualcallinline.DualCallTest.foo()",
            "STATIC: String dualcallinline.DualCallTest.foo()"),
        references(clazz, "main", "void", "java.lang.String[]"));
  }
}
