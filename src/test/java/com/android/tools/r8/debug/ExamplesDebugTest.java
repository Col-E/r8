// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  private Stream<DebuggeeState> r8cf() throws Exception {
    return streamDebugTest(getCfConfig("r8cf.jar"), clazzName, ANDROID_FILTER);
  }

  private DebugTestConfig getCfConfig(String outputName) throws Exception {
    Path input = inputJar;
    Path output = temp.newFolder().toPath().resolve(outputName);
    ToolHelper.runR8(
        R8Command.builder()
            .addProgramFiles(input)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setMode(CompilationMode.DEBUG)
            .setOutput(output, OutputMode.ClassFile)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addProguardConfiguration(ImmutableList.of("-keepattributes *"), Origin.unknown())
            .build());
    return new CfDebugTestConfig(output);
  }

  @Test
  public void testArithmetic() throws Throwable {
    testDebugging("arithmetic", "Arithmetic");
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
    testDebugging("regress_70736958", "Test");
  }

  @Test
  public void testRegress70737019() throws Exception {
    testDebugging("regress_70737019", "Test");
  }

  @Test
  public void testRegress72361252() throws Exception {
    testDebugging("regress_72361252", "Test");
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

  private void testDebugging(String pkg, String clazz) throws Exception {
    init(pkg, clazz)
        .add("Input", input())
        .add("R8/CfSourceCode", r8cf())
        .add("D8", d8())
        // When running on CF and DEX runtimes, filter down to states within the test package.
        .setFilter(state -> state.getClassName().startsWith(pkg))
        .compare();
  }

  private void testDebuggingJvmOnly(String pkg, String clazz) throws Exception {
    init(pkg, clazz)
        .add("Input", input())
        .add("R8/CfSourceCode", r8cf())
        .compare();
  }

  private DebugStreamComparator init(String pkg, String clazz) {
    // TODO(b/199700280): Reenable on 12.0.0 when we have the libjdwp.so file include and the flags
    // fixed.
    Assume.assumeTrue(
        "Skipping test " + testName.getMethodName() + " because debugging not enabled in 12.0.0",
        !ToolHelper.getDexVm().isNewerThanOrEqual(DexVm.ART_12_0_0_HOST));
    // See verifyStateLocation in DebugTestBase.
    Assume.assumeTrue(
        "Streaming on Dalvik DEX runtimes has some unknown interference issue",
        ToolHelper.getDexVm().getVersion().isNewerThanOrEqual(Version.V6_0_1));
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
