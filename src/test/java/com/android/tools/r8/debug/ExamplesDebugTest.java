// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

public class ExamplesDebugTest extends DebugTestBase {

  private String clazzName;
  private Path inputJar;

  private Stream<DebuggeeState> input() throws Exception {
    return streamDebugTest(new CfDebugTestConfig(inputJar), clazzName, ANDROID_FILTER);
  }

  private Stream<DebuggeeState> d8() throws Exception {
    D8DebugTestConfig config = new D8DebugTestConfig().compileAndAdd(temp, inputJar);
    return streamDebugTest(config, clazzName, ANDROID_FILTER);
  }

  private Stream<DebuggeeState> r8jar() throws Exception {
    return streamDebugTest(getCfConfig("r8jar.jar", o -> {}), clazzName, ANDROID_FILTER);
  }

  private Stream<DebuggeeState> r8cf() throws Exception {
    return streamDebugTest(
        getCfConfig("r8cf.jar", options -> options.enableCfFrontend = true),
        clazzName,
        ANDROID_FILTER);
  }

  private DebugTestConfig getCfConfig(String outputName, Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    Path input = inputJar;
    Path output = temp.newFolder().toPath().resolve(outputName);
    ToolHelper.runR8(
        R8Command.builder()
            .addProgramFiles(input)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setMode(CompilationMode.DEBUG)
            .setOutput(output, OutputMode.ClassFile)
            .build(),
        optionsConsumer);
    return new CfDebugTestConfig(output);
  }

  @Test
  public void testArithmetic() throws Throwable {
    testDebugging("arithmetic", "Arithmetic");
  }

  @Test
  public void testArrayAccess() throws Exception {
    testDebugging("arrayaccess", "ArrayAccess");
  }

  @Test
  public void testBArray() throws Exception {
    testDebugging("barray", "BArray");
  }

  @Test
  public void testBridgeMethod() throws Exception {
    // TODO(b/79671093): D8 has local variables with empty names.
    testDebuggingJvmOnly("bridge", "BridgeMethod");
  }

  @Test
  public void testCommonSubexpressionElimination() throws Exception {
    testDebugging("cse", "CommonSubexpressionElimination");
  }

  @Test
  public void testConstants() throws Exception {
    testDebugging("constants", "Constants");
  }

  @Test
  public void testControlFlow() throws Exception {
    testDebugging("controlflow", "ControlFlow");
  }

  @Test
  public void testConversions() throws Exception {
    testDebugging("conversions", "Conversions");
  }

  @Test
  public void testFloatingPointValuedAnnotation() throws Exception {
    // D8 has no source file.
    testDebuggingJvmOnly("floating_point_annotations", "FloatingPointValuedAnnotationTest");
  }

  @Test
  public void testFilledArray() throws Exception {
    testDebugging("filledarray", "FilledArray");
  }

  @Test
  public void testHello() throws Exception {
    testDebugging("hello", "Hello");
  }

  @Test
  public void testIfStatements() throws Exception {
    testDebugging("ifstatements", "IfStatements");
  }

  @Test
  public void testInstanceVariable() throws Exception {
    testDebugging("instancevariable", "InstanceVariable");
  }

  @Test
  public void testInstanceofString() throws Exception {
    testDebugging("instanceofstring", "InstanceofString");
  }

  @Test
  public void testInvoke() throws Exception {
    testDebugging("invoke", "Invoke");
  }

  @Test
  public void testJumboString() throws Exception {
    testDebugging("jumbostring", "JumboString");
  }

  @Test
  public void testLoadConst() throws Exception {
    testDebugging("loadconst", "LoadConst");
  }

  @Test
  public void testUdpServer() throws Exception {
    // TODO(b/79671093): We don't match JVM's behavior on this example.
    testDebuggingJvmOutputOnly("loop", "UdpServer");
  }

  @Test
  public void testNewArray() throws Exception {
    testDebugging("newarray", "NewArray");
  }

  @Test
  public void testRegAlloc() throws Exception {
    testDebugging("regalloc", "RegAlloc");
  }

  @Test
  public void testReturns() throws Exception {
    testDebugging("returns", "Returns");
  }

  @Test
  public void testStaticField() throws Exception {
    // TODO(b/79671093): D8 has different line number info during stepping.
    testDebuggingJvmOnly("staticfield", "StaticField");
  }

  @Test
  public void testStringBuilding() throws Exception {
    testDebugging("stringbuilding", "StringBuilding");
  }

  @Test
  public void testSwitches() throws Exception {
    testDebugging("switches", "Switches");
  }

  @Test
  public void testSync() throws Exception {
    // D8 has two local variables with empty names.
    testDebuggingJvmOnly("sync", "Sync");
  }

  @Test
  public void testThrowing() throws Exception {
    // TODO(b/79671093): We don't match JVM's behavior on this example.
    testDebuggingJvmOutputOnly("throwing", "Throwing");
  }

  @Test
  public void testTrivial() throws Exception {
    testDebugging("trivial", "Trivial");
  }

  @Ignore("TODO(mathiasr): InvalidDebugInfoException in CfSourceCode")
  @Test
  public void testTryCatch() throws Exception {
    // TODO(b/79671093): We don't match JVM's behavior on this example.
    testDebuggingJvmOutputOnly("trycatch", "TryCatch");
  }

  @Test
  public void testNestedTryCatches() throws Exception {
    testDebugging("nestedtrycatches", "NestedTryCatches");
  }

  @Test
  public void testTryCatchMany() throws Exception {
    testDebugging("trycatchmany", "TryCatchMany");
  }

  @Test
  public void testInvokeEmpty() throws Exception {
    testDebugging("invokeempty", "InvokeEmpty");
  }

  @Test
  public void testRegress() throws Exception {
    testDebugging("regress", "Regress");
  }

  @Test
  public void testRegress2() throws Exception {
    testDebugging("regress2", "Regress2");
  }

  @Test
  public void testRegress37726195() throws Exception {
    testDebugging("regress_37726195", "Regress");
  }

  @Test
  public void testRegress37658666() throws Exception {
    testDebugging("regress_37658666", "Regress");
  }

  @Test
  public void testRegress37875803() throws Exception {
    testDebugging("regress_37875803", "Regress");
  }

  @Test
  public void testRegress37955340() throws Exception {
    testDebugging("regress_37955340", "Regress");
  }

  @Test
  public void testRegress62300145() throws Exception {
    // D8 has no source file.
    testDebuggingJvmOnly("regress_62300145", "Regress");
  }

  @Test
  public void testRegress64881691() throws Exception {
    testDebugging("regress_64881691", "Regress");
  }

  @Test
  public void testRegress65104300() throws Exception {
    testDebugging("regress_65104300", "Regress");
  }

  @Ignore("TODO(b/79671093): This test seems to take forever")
  @Test
  public void testRegress70703087() throws Exception {
    testDebugging("regress_70703087", "Test");
  }

  @Test
  public void testRegress70736958() throws Exception {
    // D8 has a local variable with empty name.
    testDebuggingJvmOnly("regress_70736958", "Test");
  }

  @Ignore("TODO(mathiasr): Different behavior CfSourceCode vs JarSourceCode")
  @Test
  public void testRegress70737019() throws Exception {
    // TODO(b/79671093): We don't match JVM's behavior on this example.
    testDebuggingJvmOutputOnly("regress_70737019", "Test");
  }

  @Test
  public void testRegress72361252() throws Exception {
    // D8 output has variable with empty name.
    testDebuggingJvmOnly("regress_72361252", "Test");
  }

  @Test
  public void testMemberrebinding2() throws Exception {
    testDebugging("memberrebinding2", "Memberrebinding");
  }

  @Test
  public void testMemberrebinding3() throws Exception {
    testDebugging("memberrebinding3", "Memberrebinding");
  }

  @Test
  public void testMinification() throws Exception {
    testDebugging("minification", "Minification");
  }

  @Test
  public void testEnclosingmethod() throws Exception {
    testDebugging("enclosingmethod", "Main");
  }

  @Test
  public void testEnclosingmethod_proguarded() throws Exception {
    // TODO(b/79671093): We don't match JVM's behavior on this example.
    testDebuggingJvmOutputOnly("enclosingmethod_proguarded", "Main");
  }

  @Test
  public void testInterfaceinlining() throws Exception {
    testDebugging("interfaceinlining", "Main");
  }

  @Test
  public void testSwitchmaps() throws Exception {
    // TODO(b/79671093): D8 has different line number info during stepping.
    testDebuggingJvmOnly("switchmaps", "Switches");
  }

  private void testDebugging(String pkg, String clazz) throws Exception {
    init(pkg, clazz)
        .add("Input", input())
        .add("R8/CfSourceCode", r8cf())
        .add("R8/JarSourceCode", r8jar())
        .add("D8", d8())
        .compare();
  }

  private void testDebuggingJvmOnly(String pkg, String clazz) throws Exception {
    init(pkg, clazz)
        .add("Input", input())
        .add("R8/CfSourceCode", r8cf())
        .add("R8/JarSourceCode", r8jar())
        .compare();
  }

  private void testDebuggingJvmOutputOnly(String pkg, String clazz) throws Exception {
    init(pkg, clazz)
        .add("R8/CfSourceCode", r8cf())
        .add("R8/JarSourceCode", r8jar())
        .compare();
  }

  private DebugStreamComparator init(String pkg, String clazz) throws Exception {
    // See verifyStateLocation in DebugTestBase.
    Assume.assumeTrue(
        "Streaming on Dalvik DEX runtimes has some unknown interference issue",
        ToolHelper.getDexVm().getVersion().isAtLeast(Version.V6_0_1));
    Assume.assumeTrue(
        "Skipping test "
            + testName.getMethodName()
            + " because debug tests are not yet supported on Windows",
        !ToolHelper.isWindows());
    clazzName = pkg + "." + clazz;
    inputJar =
        Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, pkg + "_debuginfo_all" + FileUtils.JAR_EXTENSION);
    return new DebugStreamComparator();
  }
}
