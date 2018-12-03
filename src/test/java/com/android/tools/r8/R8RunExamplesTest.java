// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestCondition.R8_COMPILER;
import static com.android.tools.r8.TestCondition.match;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8RunExamplesTest extends R8RunExamplesCommon {

  private static final boolean ONLY_RUN_CF_TESTS = false;

  @Parameters(name = "{0}_{1}_{2}_{3}_{5}_{6}")
  public static Collection<String[]> data() {
    String[] tests = {
        "abstractmethodremoval.AbstractMethodRemoval",
        "arithmetic.Arithmetic",
        "arrayaccess.ArrayAccess",
        "barray.BArray",
        "bridge.BridgeMethod",
        "catchhandleroverlap.CatchHandlerOverlap",
        "cse.CommonSubexpressionElimination",
        "constants.Constants",
        "controlflow.ControlFlow",
        "conversions.Conversions",
        "floating_point_annotations.FloatingPointValuedAnnotationTest",
        "filledarray.FilledArray",
        "hello.Hello",
        "ifstatements.IfStatements",
        "inlining.Inlining",
        "instancevariable.InstanceVariable",
        "instanceofstring.InstanceofString",
        "invoke.Invoke",
        "invokeempty.InvokeEmpty",
        "jumbostring.JumboString",
        "loadconst.LoadConst",
        "loop.UdpServer",
        "nestedtrycatches.NestedTryCatches",
        "newarray.NewArray",
        "regalloc.RegAlloc",
        "returns.Returns",
        "staticfield.StaticField",
        "stringbuilding.StringBuilding",
        "switches.Switches",
        "sync.Sync",
        "throwing.Throwing",
        "trivial.Trivial",
        "trycatch.TryCatch",
        "trycatchmany.TryCatchMany",
        "regress.Regress",
        "regress2.Regress2",
        "regress_37726195.Regress",
        "regress_37658666.Regress",
        "regress_37875803.Regress",
        "regress_37955340.Regress",
        "regress_62300145.Regress",
        "regress_64881691.Regress",
        "regress_65104300.Regress",
        "regress_70703087.Test",
        "regress_70736958.Test",
        "regress_70737019.Test",
        "regress_72361252.Test",
        "regress_110373181.Regress",
        "memberrebinding2.Memberrebinding",
        "memberrebinding3.Memberrebinding",
        "minification.Minification",
        "enclosingmethod.Main",
        "enclosingmethod_proguarded.Main",
        "interfaceinlining.Main",
        "switchmaps.Switches",
        "uninitializedfinal.UninitializedFinalFieldLeak",
    };

    List<String[]> fullTestList = new ArrayList<>(tests.length * 2);
    for (String test : tests) {
      if (!ONLY_RUN_CF_TESTS) {
        fullTestList.add(makeTest(Input.JAVAC, CompilerUnderTest.D8, CompilationMode.DEBUG, test));
        fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.D8, CompilationMode.DEBUG,
            test));
        fullTestList.add(makeTest(Input.JAVAC_NONE, CompilerUnderTest.D8, CompilationMode.DEBUG,
            test));
        fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.D8, CompilationMode.RELEASE,
            test));
        fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.R8, CompilationMode.RELEASE,
            test));
        fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.R8, CompilationMode.DEBUG,
            test));
        fullTestList.add(makeTest(Input.DX, CompilerUnderTest.R8, CompilationMode.RELEASE, test));
      }
      fullTestList.add(
          makeTest(
              Input.JAVAC_ALL, CompilerUnderTest.R8, CompilationMode.RELEASE, test, Output.CF));
      fullTestList.add(
          makeTest(
              Input.JAVAC_ALL,
              CompilerUnderTest.R8,
              CompilationMode.RELEASE,
              test,
              Frontend.CF,
              Output.CF));
      fullTestList.add(
          makeTest(
              Input.JAVAC_ALL,
              CompilerUnderTest.R8,
              CompilationMode.RELEASE,
              test,
              Frontend.CF,
              Output.DEX));
    }
    return fullTestList;
  }

  public R8RunExamplesTest(
      String pkg,
      String input,
      String compiler,
      String mode,
      String mainClass,
      String frontend,
      String output) {
    super(pkg, input, compiler, mode, mainClass, frontend, output);
  }

  @Override
  protected String getExampleDir() {
    return ToolHelper.EXAMPLES_BUILD_DIR;
  }

  @Override
  protected Map<String, TestCondition> getFailingRun() {
    return new ImmutableMap.Builder<String, TestCondition>()
        .put("memberrebinding2.Test", match(R8_COMPILER)) // b/38187737
        .build();
  }

  @Override
  protected Map<String, TestCondition> getFailingRunCf() {
    return new ImmutableMap.Builder<String, TestCondition>()
        .build();
  }

  @Override
  protected Set<String> getFailingRunCfToDex() {
    return new ImmutableSet.Builder<String>()
        // TODO(b/109788783): Implement byte/boolean distinction for array load/store.
        .add("arrayaccess.ArrayAccess")
        .add("barray.BArray")
        .add("filledarray.FilledArray")
        .build();
  }

  @Override
  protected Set<String> getFailingCompileCfToDex() {
    return new ImmutableSet.Builder<String>()
        // TODO(b/109789541): Implement method synchronization for DEX backend.
        .add("sync.Sync")
        // TODO(b/109789539): Implement CfMultiANewArray.buildIR() for DEX backend.
        .add("newarray.NewArray")
        .add("trycatch.TryCatch")
        .add("regress_70737019.Test")
        .add("regress_72361252.Test")
        .build();
  }

  @Override
  protected Set<String> getFailingCompileCf() {
    return new ImmutableSet.Builder<String>()
        .build();
  }

  @Override
  protected Set<String> getFailingOutputCf() {
    return new ImmutableSet.Builder<String>()
        .add("regress_62300145.Regress") // annotations
        .add("throwing.Throwing") // no line info
        .build();
  }

  @Override
  protected Map<String, TestCondition> getOutputNotIdenticalToJVMOutput() {
    return new ImmutableMap.Builder<String, TestCondition>()
        // Traverses stack frames that contain Art specific frames.
        .put("throwing.Throwing", TestCondition.any())
        // DEX enclosing-class annotations don't distinguish member classes from local classes.
        // This results in Class.isLocalClass always being false and Class.isMemberClass always
        // being true even when the converse is the case when running on the JVM.
        .put("enclosingmethod.Main", TestCondition.any())
        // Early art versions incorrectly print Float.MIN_VALUE.
        .put("filledarray.FilledArray",
            TestCondition.match(TestCondition.runtimesUpTo(Version.V6_0_1)))
        // Early art versions incorrectly print doubles.
        .put("regress_70736958.Test",
            TestCondition.match(TestCondition.runtimesUpTo(Version.V6_0_1)))
        // Early art versions incorrectly print doubles.
        .put("regress_72361252.Test",
            TestCondition.match(TestCondition.runtimesUpTo(Version.V6_0_1)))
        // TODO(120402200): Triage.
        .put("regress_62300145.Regress",
            TestCondition.match(TestCondition.runtimesUpTo(Version.V9_0_0)))
        .build();
  }


  @Override
  protected Map<String, TestCondition> getSkip() {
    return new ImmutableMap.Builder<String, TestCondition>()
        // Test uses runtime methods which are not available on older Art versions.
        .put("regress_70703087.Test",
            TestCondition.match(TestCondition.runtimesUpTo(Version.V6_0_1)))
        // Test uses runtime methods which are not available on older Art versions.
        .put("loop.UdpServer",
            TestCondition.match(TestCondition.runtimesUpTo(Version.V4_0_4)))
        .build();
  }
}
