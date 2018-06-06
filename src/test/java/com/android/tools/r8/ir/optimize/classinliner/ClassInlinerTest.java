// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.ir.optimize.classinliner.builders.BuildersTestClass;
import com.android.tools.r8.ir.optimize.classinliner.builders.ControlFlow;
import com.android.tools.r8.ir.optimize.classinliner.builders.Pair;
import com.android.tools.r8.ir.optimize.classinliner.builders.PairBuilder;
import com.android.tools.r8.ir.optimize.classinliner.builders.Tuple;
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
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
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
    String main = TrivialTestClass.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);

    AndroidApp app = runR8(buildAndroidApp(classes), TrivialTestClass.class);

    DexInspector inspector = new DexInspector(app);
    ClassSubject clazz = inspector.clazz(TrivialTestClass.class);

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectNewInstanceTypes(clazz, "testInner"));

    assertEquals(
        Collections.emptySet(),
        collectNewInstanceTypes(clazz, "testConstructorMapping1"));

    assertEquals(
        Collections.emptySet(),
        collectNewInstanceTypes(clazz, "testConstructorMapping2"));

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectNewInstanceTypes(clazz, "testConstructorMapping3"));

    assertEquals(
        Collections.emptySet(),
        collectNewInstanceTypes(clazz, "testEmptyClass"));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.EmptyClassWithInitializer"),
        collectNewInstanceTypes(clazz, "testEmptyClassWithInitializer"));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.ClassWithFinal"),
        collectNewInstanceTypes(clazz, "testClassWithFinalizer"));

    assertEquals(
        Collections.emptySet(),
        collectNewInstanceTypes(clazz, "testCallOnIface1"));

    assertEquals(
        Collections.singleton(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.Iface2Impl"),
        collectNewInstanceTypes(clazz, "testCallOnIface2"));

    assertEquals(
        Sets.newHashSet(
            "com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceAB",
            "java.lang.StringBuilder"),
        collectNewInstanceTypes(clazz, "testCycles"));

    assertEquals(
        Sets.newHashSet("java.lang.StringBuilder",
            "com.android.tools.r8.ir.optimize.classinliner.trivial.CycleReferenceAB"),
        collectNewInstanceTypes(inspector.clazz(CycleReferenceAB.class), "foo", "int"));

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
    String main = BuildersTestClass.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);

    AndroidApp app = runR8(buildAndroidApp(classes), BuildersTestClass.class);

    DexInspector inspector = new DexInspector(app);
    ClassSubject clazz = inspector.clazz(BuildersTestClass.class);

    assertEquals(
        Sets.newHashSet(
            "com.android.tools.r8.ir.optimize.classinliner.builders.Pair",
            "java.lang.StringBuilder"),
        collectNewInstanceTypes(clazz, "testSimpleBuilder"));

    // Note that Pair created instances were also inlined in the following method since
    // we use 'System.out.println(pX.toString())', if we used 'System.out.println(pX)'
    // as in the above method, the instance of pair would be passed to println() which
    // would make it not eligible for inlining.
    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectNewInstanceTypes(clazz, "testSimpleBuilderWithMultipleBuilds"));

    assertFalse(inspector.clazz(PairBuilder.class).isPresent());

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectNewInstanceTypes(clazz, "testBuilderConstructors"));

    assertFalse(inspector.clazz(Tuple.class).isPresent());

    assertEquals(
        Collections.singleton("java.lang.StringBuilder"),
        collectNewInstanceTypes(clazz, "testWithControlFlow"));

    assertFalse(inspector.clazz(ControlFlow.class).isPresent());

    assertEquals(
        Collections.emptySet(),
        collectNewInstanceTypes(clazz, "testWithMoreControlFlow"));

    assertFalse(inspector.clazz(BuildersTestClass.Pos.class).isPresent());
  }

  private Set<String> collectNewInstanceTypes(
      ClassSubject clazz, String methodName, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return filterInstructionKind(code, NewInstance.class)
        .map(insn -> ((NewInstance) insn).getType().toSourceString())
        .collect(Collectors.toSet());
  }

  private AndroidApp runR8(AndroidApp app, Class mainClass) throws Exception {
    String config = keepMainProguardConfiguration(mainClass) + "\n"
        + "-dontobfuscate\n"
        + "-allowaccessmodification";

    AndroidApp compiled = compileWithR8(app, config,
        o -> {
          o.enableClassInlining = true;
          o.classInliningInstructionLimit = 10000;
        });

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
}
