// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.Sget;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.ir.optimize.classinliner.builders.BuildersTestClass;
import com.android.tools.r8.ir.optimize.classinliner.builders.ControlFlow;
import com.android.tools.r8.ir.optimize.classinliner.builders.Pair;
import com.android.tools.r8.ir.optimize.classinliner.builders.PairBuilder;
import com.android.tools.r8.ir.optimize.classinliner.builders.Tuple;
import com.android.tools.r8.ir.optimize.classinliner.code.C;
import com.android.tools.r8.ir.optimize.classinliner.code.CodeTestClass;
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
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class ClassInlinerTest extends TestBase {
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
    String artOutput = runOnArt(app, TrivialTestClass.class);
    assertEquals(javaOutput, artOutput);

    DexInspector inspector = new DexInspector(app);
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
    String artOutput = runOnArt(app, BuildersTestClass.class);
    assertEquals(javaOutput, artOutput);

    DexInspector inspector = new DexInspector(app);
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
        compileWithR8(builder.build(), getProguardConfig(mainClass.name), this::configure);

    // Check that the code fails with an IncompatibleClassChangeError with Java.
    ProcessResult javaResult =
        runOnJavaRaw(mainClass.name, builder.buildClasses().toArray(new byte[2][]));
    assertThat(javaResult.stderr, containsString("IncompatibleClassChangeError"));

    // Check that the code fails with an IncompatibleClassChangeError with ART.
    ProcessResult artResult = runOnArtRaw(compiled, mainClass.name);
    assertThat(artResult.stderr, containsString("IncompatibleClassChangeError"));
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
    String artOutput = runOnArt(app, CodeTestClass.class);
    assertEquals(javaOutput, artOutput);

    DexInspector inspector = new DexInspector(app);
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
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return filterInstructionKind(code, NewInstance.class)
        .map(insn -> ((NewInstance) insn).getType().toSourceString());
  }

  private Stream<String> collectStaticGetTypesWithRetValue(
      ClassSubject clazz, String methodName, String retValue, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, retValue, params);
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return filterInstructionKind(code, Sget.class)
        .map(Instruction::getField)
        .filter(field -> field.clazz == field.type)
        .map(field -> field.clazz.toSourceString());
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
    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(
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
        + "\n"
        + "-dontobfuscate\n"
        + "-allowaccessmodification";
  }

  private void configure(InternalOptions options) {
    options.enableClassInlining = true;
    options.classInliningInstructionLimit = 10000;
  }
}
