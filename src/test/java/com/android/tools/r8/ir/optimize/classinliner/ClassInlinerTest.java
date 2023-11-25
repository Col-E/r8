// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.ir.optimize.classinliner.code.C;
import com.android.tools.r8.ir.optimize.classinliner.code.CodeTestClass;
import com.android.tools.r8.ir.optimize.classinliner.invalidroot.InvalidRootsTestClass;
import com.android.tools.r8.ir.optimize.classinliner.lambdas.LambdasTestClass;
import com.android.tools.r8.ir.optimize.classinliner.trivial.ClassWithFinal;
import com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceAB;
import com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceBA;
import com.android.tools.r8.ir.optimize.classinliner.trivial.EmptyClass;
import com.android.tools.r8.ir.optimize.classinliner.trivial.EmptyClassWithInitializer;
import com.android.tools.r8.ir.optimize.classinliner.trivial.Iface1;
import com.android.tools.r8.ir.optimize.classinliner.trivial.Iface1Impl;
import com.android.tools.r8.ir.optimize.classinliner.trivial.Iface2;
import com.android.tools.r8.ir.optimize.classinliner.trivial.Iface2Impl;
import com.android.tools.r8.ir.optimize.classinliner.trivial.ReferencedFields;
import com.android.tools.r8.ir.optimize.classinliner.trivial.TrivialTestClass;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlinerTest extends ClassInlinerTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testTrivial() throws Exception {
    Class<?> main = TrivialTestClass.class;
    Class<?>[] classes = {
        TrivialTestClass.class,
        TrivialTestClass.Inner.class,
        ReferencedFields.class,
        EmptyClass.class,
        EmptyClassWithInitializer.class,
        Iface1.class,
        Iface1Impl.class,
        Iface2.class,
        Iface2Impl.class,
        CycleReferenceAB.class,
        CycleReferenceBA.class,
        ClassWithFinal.class
    };
    String javaOutput = runOnJava(main);
    SingleTestRunResult<?> result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoMethodStaticizingAnnotations()
            .enableSideEffectAnnotations()
            .addKeepMainRule(main)
            .addKeepAttributes("LineNumberTable")
            .addHorizontallyMergedClassesInspector(
                inspector ->
                    inspector
                        .assertIsCompleteMergeGroup(
                            Iface1Impl.class, Iface2Impl.class, CycleReferenceBA.class)
                        .assertMergedInto(CycleReferenceBA.class, Iface1Impl.class)
                        .assertMergedInto(Iface2Impl.class, Iface1Impl.class))
            .allowAccessModification()
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testInner")));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz.uniqueMethodWithOriginalName("testConstructorMapping1")));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz.uniqueMethodWithOriginalName("testConstructorMapping2")));

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testConstructorMapping3")));

    assertEquals(
        Collections.emptySet(), collectTypes(clazz.uniqueMethodWithOriginalName("testEmptyClass")));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.EmptyClassWithInitializer"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testEmptyClassWithInitializer")));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.ClassWithFinal"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testClassWithFinalizer")));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz.uniqueMethodWithOriginalName("testCallOnIface1")));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz.uniqueMethodWithOriginalName("testCallOnIface2")));

    assertEquals(
        Sets.newHashSet(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceAB",
            "java.lang.StringBuilder"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testCycles")));

    assertEquals(
        Sets.newHashSet(
            "java.lang.StringBuilder",
            "com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceAB"),
        collectTypes(inspector.clazz(CycleReferenceAB.class).uniqueMethodWithOriginalName("foo")));

    assertThat(inspector.clazz(CycleReferenceBA.class), isAbsent());
  }

  @Test
  public void testErroneousInput() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder testClass = builder.addClass("A");
    testClass.addStaticFinalField("f", "I", "123");
    testClass.addDefaultConstructor();

    ClassBuilder mainClass = builder.addClass("Main");
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  new A",
        "  dup",
        "  invokespecial A/<init>()V",
        "  getfield A/f I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    AndroidApp compiled =
        compileWithR8(
            builder.build(), getProguardConfig(mainClass.name), null, parameters.getBackend());

    // Check that the code fails with an IncompatibleClassChangeError with Java.
    ProcessResult javaResult =
        runOnJavaRaw(mainClass.name, builder.buildClasses().toArray(new byte[2][]));
    assertThat(javaResult.stderr, containsString("IncompatibleClassChangeError"));

    // Check that the code fails with an IncompatibleClassChangeError with ART.
    ProcessResult result =
        parameters.isDexRuntime()
            ? runOnArtRaw(compiled, mainClass.name)
            : runOnJavaRaw(compiled, mainClass.name, Collections.emptyList());
    assertThat(result.stderr, containsString("IncompatibleClassChangeError"));
  }

  @Test
  public void testCodeSample() throws Exception {
    Class<?> main = CodeTestClass.class;
    Class<?>[] classes = {
        C.class,
        C.L.class,
        C.F.class,
        CodeTestClass.class
    };
    String javaOutput = runOnJava(main);
    SingleTestRunResult<?> result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableInliningAnnotations()
            .enableSideEffectAnnotations()
            .addKeepMainRule(main)
            .addKeepAttributes("LineNumberTable")
            .allowAccessModification()
            .addDontObfuscate()
            .run(main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(C.class);

    assertEquals(
        Collections.emptySet(), collectTypes(clazz.uniqueMethodWithOriginalName("method1")));

    assertEquals(
        Collections.emptySet(), collectTypes(clazz.uniqueMethodWithOriginalName("method2")));

    assertEquals(
        Collections.emptySet(), collectTypes(clazz.uniqueMethodWithOriginalName("method3")));

    assertFalse(inspector.clazz(C.L.class).isPresent());
    assertFalse(inspector.clazz(C.F.class).isPresent());
  }

  @Test
  public void testInvalidatedRoot() throws Exception {
    Class<?> main = InvalidRootsTestClass.class;
    Class<?>[] classes = {
        InvalidRootsTestClass.class,
        InvalidRootsTestClass.A.class,
        InvalidRootsTestClass.B.class,
        InvalidRootsTestClass.NeverReturnsNormally.class,
        InvalidRootsTestClass.InitNeverReturnsNormally.class
    };
    String javaOutput = runOnJava(main);
    SingleTestRunResult<?> result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .enableConstantArgumentAnnotations()
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .addKeepMainRule(main)
            .addKeepAttributes("LineNumberTable")
            .addOptionsModification(
                o -> {
                  // TODO(b/143129517, 141719453): The limit seems to only be needed for DEX...
                  o.classInlinerOptions().classInliningInstructionAllowance = 1000;
                })
            .allowAccessModification()
            .addDontObfuscate()
            .run(main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    // TODO(b/143129517, 141719453): This expectation relies on the class inlining limits.
    assertEquals(
        Sets.newHashSet("java.lang.StringBuilder", "java.lang.RuntimeException"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testExtraNeverReturnsNormally")));

    assertEquals(
        Sets.newHashSet("java.lang.StringBuilder", "java.lang.RuntimeException"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testDirectNeverReturnsNormally")));

    assertEquals(
        Sets.newHashSet("java.lang.StringBuilder", "java.lang.RuntimeException"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testInitNeverReturnsNormally")));

    assertThat(
        inspector.clazz(InvalidRootsTestClass.NeverReturnsNormally.class),
        notIf(isPresent(), parameters.isCfRuntime()));
    assertThat(
        inspector.clazz(InvalidRootsTestClass.InitNeverReturnsNormally.class), not(isPresent()));

    // TODO(b/143129517, b/141719453): This expectation relies on the class inlining limits.
    assertEquals(
        Sets.newHashSet("java.lang.StringBuilder", "java.lang.RuntimeException"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testRootInvalidatesAfterInlining")));

    assertThat(inspector.clazz(InvalidRootsTestClass.A.class), not(isPresent()));
    assertThat(inspector.clazz(InvalidRootsTestClass.B.class), not(isPresent()));
  }

  @Test
  public void testDesugaredLambdas() throws Exception {
    Assume.assumeFalse("No desugaring with CF backend", parameters.isCfRuntime());
    Class<?> main = LambdasTestClass.class;
    Class<?>[] classes = {
        LambdasTestClass.class,
        LambdasTestClass.Iface.class,
        LambdasTestClass.IfaceUtil.class
    };
    String javaOutput = runOnJava(main);
    SingleTestRunResult<?> result =
        testForR8(parameters.getBackend())
            .addProgramClasses(classes)
            .addKeepMainRule(main)
            .addKeepAttributes("LineNumberTable")
            .allowAccessModification()
            .enableInliningAnnotations()
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), main)
            .assertSuccessWithOutput(javaOutput);

    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(main);

    List<String> synthesizedJavaLambdaClasses =
        inspector.allClasses().stream()
            .filter(FoundClassSubject::isSynthesizedJavaLambdaClass)
            .map(FoundClassSubject::getFinalName)
            .collect(Collectors.toList());
    assertEquals(Collections.emptyList(), synthesizedJavaLambdaClasses);

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testStatelessLambda")));

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz.uniqueMethodWithOriginalName("testStatefulLambda")));
  }

  private String getProguardConfig(String main) {
    return StringUtils.joinLines(
        keepMainProguardConfiguration(main),
        "-dontobfuscate",
        "-allowaccessmodification",
        "-keepattributes LineNumberTable");
  }
}
