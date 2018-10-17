// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.ir.optimize.classinliner.builders.BuildersTestClass;
import com.android.tools.r8.ir.optimize.classinliner.builders.ControlFlow;
import com.android.tools.r8.ir.optimize.classinliner.builders.Pair;
import com.android.tools.r8.ir.optimize.classinliner.builders.PairBuilder;
import com.android.tools.r8.ir.optimize.classinliner.builders.Tuple;
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
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.NewInstanceInstructionSubject;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlinerTest extends TestBase {
  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public ClassInlinerTest(Backend backend) {
    this.backend = backend;
  }

  private String run(AndroidApp app, Class mainClass) throws IOException {
    if (backend == Backend.DEX) {
      return runOnArt(app, mainClass);
    } else {
      assert backend == Backend.CF;
      return runOnJava(app, mainClass);
    }
  }

  @Test
  public void testTrivial() throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(TrivialTestClass.class),
        ToolHelper.getClassAsBytes(TrivialTestClass.Inner.class),
        ToolHelper.getClassAsBytes(ReferencedFields.class),
        ToolHelper.getClassAsBytes(EmptyClass.class),
        ToolHelper.getClassAsBytes(EmptyClassWithInitializer.class),
        ToolHelper.getClassAsBytes(Iface1.class),
        ToolHelper.getClassAsBytes(Iface1Impl.class),
        ToolHelper.getClassAsBytes(Iface2.class),
        ToolHelper.getClassAsBytes(Iface2Impl.class),
        ToolHelper.getClassAsBytes(CycleReferenceAB.class),
        ToolHelper.getClassAsBytes(CycleReferenceBA.class),
        ToolHelper.getClassAsBytes(ClassWithFinal.class)
    };
    AndroidApp app = runR8(buildAndroidApp(classes), TrivialTestClass.class);

    String javaOutput = runOnJava(TrivialTestClass.class);
    String output = run(app, TrivialTestClass.class);
    assertEquals(javaOutput, output);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(TrivialTestClass.class);

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz, "testInner", "void"));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "testConstructorMapping1", "void"));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "testConstructorMapping2", "void"));

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz, "testConstructorMapping3", "void"));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "testEmptyClass", "void"));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.EmptyClassWithInitializer"),
        collectTypes(clazz, "testEmptyClassWithInitializer", "void"));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.ClassWithFinal"),
        collectTypes(clazz, "testClassWithFinalizer", "void"));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "testCallOnIface1", "void"));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.Iface2Impl"),
        collectTypes(clazz, "testCallOnIface2", "void"));

    assertEquals(
        Sets.newHashSet(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceAB",
            "java.lang.StringBuilder"),
        collectTypes(clazz, "testCycles", "void"));

    assertEquals(
        Sets.newHashSet("java.lang.StringBuilder",
            "com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceAB"),
        collectTypes(inspector.clazz(CycleReferenceAB.class), "foo", "void", "int"));

    assertFalse(inspector.clazz(CycleReferenceBA.class).isPresent());
  }

  @Test
  public void testBuilders() throws Exception {
    // TODO(117876041): Doesn't work on CF backend after move-result rewriting disabling.
    Assume.assumeFalse(backend == Backend.CF);
    byte[][] classes = {
        ToolHelper.getClassAsBytes(BuildersTestClass.class),
        ToolHelper.getClassAsBytes(BuildersTestClass.Pos.class),
        ToolHelper.getClassAsBytes(Tuple.class),
        ToolHelper.getClassAsBytes(Pair.class),
        ToolHelper.getClassAsBytes(PairBuilder.class),
        ToolHelper.getClassAsBytes(ControlFlow.class),
    };
    AndroidApp app = runR8(buildAndroidApp(classes), BuildersTestClass.class);

    String javaOutput = runOnJava(BuildersTestClass.class);
    String output = run(app, BuildersTestClass.class);
    assertEquals(javaOutput, output);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(BuildersTestClass.class);

    assertEquals(
        Sets.newHashSet(
            "com.android.tools.r8.ir.optimize.classinliner.builders.Pair",
            "java.lang.StringBuilder"),
        collectTypes(clazz, "testSimpleBuilder", "void"));

    // Note that Pair created instances were also inlined in the following method since
    // we use 'System.out.println(pX.toString())', if we used 'System.out.println(pX)'
    // as in the above method, the instance of pair would be passed to println() which
    // would make it not eligible for inlining.
    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz, "testSimpleBuilderWithMultipleBuilds", "void"));

    assertFalse(inspector.clazz(PairBuilder.class).isPresent());

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz, "testBuilderConstructors", "void"));

    assertFalse(inspector.clazz(Tuple.class).isPresent());

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectTypes(clazz, "testWithControlFlow", "void"));

    assertFalse(inspector.clazz(ControlFlow.class).isPresent());

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "testWithMoreControlFlow", "void"));

    assertFalse(inspector.clazz(BuildersTestClass.Pos.class).isPresent());
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
        compileWithR8(builder.build(), getProguardConfig(mainClass.name), this::configure, backend);

    // Check that the code fails with an IncompatibleClassChangeError with Java.
    ProcessResult javaResult =
        runOnJavaRaw(mainClass.name, builder.buildClasses().toArray(new byte[2][]));
    assertThat(javaResult.stderr, containsString("IncompatibleClassChangeError"));

    assert backend == Backend.DEX || backend == Backend.CF;
    // Check that the code fails with an IncompatibleClassChangeError with ART.
    ProcessResult result =
        backend == Backend.DEX
            ? runOnArtRaw(compiled, mainClass.name)
            : runOnJavaRaw(compiled, mainClass.name, Collections.emptyList());
    assertThat(result.stderr, containsString("IncompatibleClassChangeError"));
  }

  @Test
  public void testCodeSample() throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(C.class),
        ToolHelper.getClassAsBytes(C.L.class),
        ToolHelper.getClassAsBytes(C.F.class),
        ToolHelper.getClassAsBytes(CodeTestClass.class)
    };
    AndroidApp app = runR8(buildAndroidApp(classes), CodeTestClass.class);

    String javaOutput = runOnJava(CodeTestClass.class);
    String output = run(app, CodeTestClass.class);
    assertEquals(javaOutput, output);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(C.class);

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "method1", "int"));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "method2", "int"));

    assertEquals(
        Collections.emptySet(),
        collectTypes(clazz, "method3", "int"));

    assertFalse(inspector.clazz(C.L.class).isPresent());
    assertFalse(inspector.clazz(C.F.class).isPresent());
  }

  @Test
  public void testInvalidatedRoot() throws Exception {
    String prefix = "com.android.tools.r8.ir.optimize.classinliner.invalidroot.";

    byte[][] classes = {
        ToolHelper.getClassAsBytes(InvalidRootsTestClass.class),
        ToolHelper.getClassAsBytes(InvalidRootsTestClass.A.class),
        ToolHelper.getClassAsBytes(InvalidRootsTestClass.B.class),
        ToolHelper.getClassAsBytes(InvalidRootsTestClass.NeverReturnsNormally.class),
        ToolHelper.getClassAsBytes(InvalidRootsTestClass.InitNeverReturnsNormally.class)
    };
    AndroidApp app = runR8(buildAndroidApp(classes), InvalidRootsTestClass.class);

    String javaOutput = runOnJava(InvalidRootsTestClass.class);
    String output = run(app, InvalidRootsTestClass.class);
    assertEquals(javaOutput, output);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(InvalidRootsTestClass.class);

    assertEquals(
        Sets.newHashSet(prefix + "InvalidRootsTestClass$NeverReturnsNormally"),
        collectTypes(clazz, "testExtraNeverReturnsNormally", "void"));

    assertEquals(
        Sets.newHashSet(prefix + "InvalidRootsTestClass$NeverReturnsNormally"),
        collectTypes(clazz, "testDirectNeverReturnsNormally", "void"));

    assertEquals(
        Sets.newHashSet(prefix + "InvalidRootsTestClass$InitNeverReturnsNormally"),
        collectTypes(clazz, "testInitNeverReturnsNormally", "void"));

    assertTrue(inspector.clazz(InvalidRootsTestClass.NeverReturnsNormally.class).isPresent());
    assertTrue(inspector.clazz(InvalidRootsTestClass.InitNeverReturnsNormally.class).isPresent());

    assertEquals(
        Sets.newHashSet(
            "java.lang.StringBuilder",
            "java.lang.RuntimeException"),
        collectTypes(clazz, "testRootInvalidatesAfterInlining", "void"));

    assertFalse(inspector.clazz(InvalidRootsTestClass.A.class).isPresent());
    assertFalse(inspector.clazz(InvalidRootsTestClass.B.class).isPresent());
  }

  @Test
  public void testDesugaredLambdas() throws Exception {
    Assume.assumeFalse(backend == Backend.CF); // No desugaring with CF backend.
    byte[][] classes = {
        ToolHelper.getClassAsBytes(LambdasTestClass.class),
        ToolHelper.getClassAsBytes(LambdasTestClass.Iface.class),
        ToolHelper.getClassAsBytes(LambdasTestClass.IfaceUtil.class),
    };
    AndroidApp app = runR8(buildAndroidApp(classes), LambdasTestClass.class);

    String javaOutput = runOnJava(LambdasTestClass.class);
    String output = run(app, LambdasTestClass.class);
    assertEquals(javaOutput, output);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(LambdasTestClass.class);

    assertEquals(
        Sets.newHashSet(
            "java.lang.StringBuilder"),
        collectTypes(clazz, "testStatelessLambda", "void"));

    assertEquals(
        Sets.newHashSet(
            "java.lang.StringBuilder"),
        collectTypes(clazz, "testStatefulLambda", "void", "java.lang.String", "java.lang.String"));

    assertEquals(0,
        inspector.allClasses().stream()
            .filter(ClassSubject::isSynthesizedJavaLambdaClass).count());
  }

  private Set<String> collectTypes(
      ClassSubject clazz, String methodName, String retValue, String... params) {
    return Stream.concat(
        collectNewInstanceTypesWithRetValue(clazz, methodName, retValue, params),
        collectStaticGetTypesWithRetValue(clazz, methodName, retValue, params)
    ).collect(Collectors.toSet());
  }

  private Stream<String> collectNewInstanceTypesWithRetValue(
      ClassSubject clazz, String methodName, String retValue, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, retValue, params);
    Iterator<InstructionSubject> iterator = clazz.method(signature).iterateInstructions();
    return Streams.stream(iterator)
        .filter(InstructionSubject::isNewInstance)
        .map(is -> ((NewInstanceInstructionSubject) is).getType().toSourceString());
  }

  private Stream<String> collectStaticGetTypesWithRetValue(
      ClassSubject clazz, String methodName, String retValue, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, retValue, params);
    Iterator<InstructionSubject> iterator = clazz.method(signature).iterateInstructions();
    return Streams.stream(iterator)
        .filter(InstructionSubject::isStaticGet)
        .map(is -> (FieldAccessInstructionSubject) is)
        .filter(fais -> fais.holder().is(fais.type()))
        .map(fais -> fais.holder().toString());
  }

  private AndroidApp runR8(AndroidApp app, Class mainClass) throws Exception {
    AndroidApp compiled =
        compileWithR8(
            app, getProguardConfig(mainClass.getCanonicalName()), this::configure, backend);

    // Materialize file for execution.
    Path generatedFile = temp.getRoot().toPath().resolve("classes.jar");
    compiled.writeToZip(generatedFile, outputMode(backend));

    assert backend == Backend.DEX || backend == Backend.CF;

    String output =
        backend == Backend.DEX
            ? ToolHelper.runArtNoVerificationErrors(
                generatedFile.toString(), mainClass.getCanonicalName())
            : ToolHelper.runJava(generatedFile, mainClass.getCanonicalName()).stdout;

    // Compare with Java.
    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(
        ToolHelper.getClassPathForTests(), mainClass.getCanonicalName());

    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM on original program failed for: " + mainClass);
    }
    assertEquals(
        backend == Backend.DEX
            ? "JVM and ART output differ."
            : "Output of original and processed program differ on JVM.",
        javaResult.stdout,
        output);

    return compiled;
  }

  private String getProguardConfig(String main) {
    return StringUtils.joinLines(
        keepMainProguardConfiguration(main),
        "-dontobfuscate",
        "-allowaccessmodification",
        "-keepattributes LineNumberTable");
  }

  private void configure(InternalOptions options) {
    options.enableClassInlining = true;
    options.classInliningInstructionLimit = 10000;
    options.inliningInstructionLimit = 6;
  }
}
