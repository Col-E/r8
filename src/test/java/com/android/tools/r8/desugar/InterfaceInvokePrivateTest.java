// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InterfaceInvokePrivateTest extends TestBase implements Opcodes {

  private final TestParameters parameters;
  private final CfVersion inputCfVersion;

  @Parameterized.Parameters(name = "{0}, Input CfVersion = {1}")
  public static Iterable<?> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevelsAlsoForCf().build(),
        CfVersion.rangeInclusive(CfVersion.V1_8, CfVersion.V15));
  }

  public InterfaceInvokePrivateTest(TestParameters parameters, CfVersion inputCfVersion) {
    this.parameters = parameters;
    this.inputCfVersion = inputCfVersion;
  }

  private boolean isNotDesugaredAndCfRuntimeOlderThanJDK11(DesugarTestConfiguration configuration) {
    return DesugarTestConfiguration.isNotDesugared(configuration)
        && parameters.getRuntime().isCf()
        && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK11);
  }

  private boolean isInputCfVersionSupported() {
    return inputCfVersion.isLessThanOrEqualTo(
        CfVersion.fromRaw(parameters.getRuntime().asCf().getVm().getClassfileVersion()));
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.getRuntime().isCf());
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm()
        .addProgramClassFileData(transformIToPrivate(inputCfVersion))
        .addProgramClasses(TestRunner.class)
        .run(parameters.getRuntime(), TestRunner.class)
        .applyIf(
            // Fails if input CF version is not supported.
            !isInputCfVersionSupported(),
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString(
                        "more recent version of the Java Runtime (class file version "
                            + inputCfVersion.toString())),
            // Fails with ICCE on VMs prior to JDK 11.
            parameters.getRuntime().asCf().isOlderThan(CfVm.JDK11),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            // Succeeds on VMs from JDK 11 regardless of the CF version of the input.
            r -> r.assertSuccessWithOutputLines("Hello, world!"));
  }

  @Test
  public void testDesugar() throws Exception {
    testForDesugaring(parameters)
        .addProgramClassFileData(transformIToPrivate(inputCfVersion))
        .addProgramClasses(TestRunner.class)
        .run(parameters.getRuntime(), TestRunner.class)
        .applyIf(
            // Running un-desugared on a JVM with too high a CF version fails.
            c ->
                parameters.getRuntime().isCf()
                    && !isInputCfVersionSupported()
                    && DesugarTestConfiguration.isNotDesugared(c),
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString(
                        "more recent version of the Java Runtime (class file version "
                            + inputCfVersion.toString())),
            // Running un-desugared on a JVM with a supported CF version fails on pre JVM 11. On
            // JVM 11 and above this succeeds even of the input CF version is below 55.
            this::isNotDesugaredAndCfRuntimeOlderThanJDK11,
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            // All other conditions succeed.
            r -> r.assertSuccessWithOutputLines("Hello, world!"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(transformIToPrivate(inputCfVersion))
        .addProgramClasses(TestRunner.class)
        .addKeepMainRule(TestRunner.class)
        // TODO(b/185463156): Not keeping I and its members will "fix" the ICCE for all runtimes.
        .addKeepClassAndMembersRules(I.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestRunner.class)
        .applyIf(
            // Running un-desugared on a JVM with too high a CF version fails.
            parameters.getRuntime().isCf() && !isInputCfVersionSupported(),
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString(
                        "more recent version of the Java Runtime (class file version "
                            + inputCfVersion.toString())),
            // Running on a JVM with a supported CF version fails on pre JVM 11. On
            // JVM 11 and above this succeeds even of the input CF version is below 55.
            parameters.getRuntime().isCf()
                && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK11),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            // All other conditions succeed.
            r -> r.assertSuccessWithOutputLines("Hello, world!"));
  }

  private byte[] transformIToPrivate(CfVersion version) throws NoSuchMethodException, IOException {
    return transformer(I.class)
        .setVersion(version)
        .setPrivate(I.class.getDeclaredMethod("privateHello"))
        .transformMethodInsnInMethod(
            "hello",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  name.equals("privateHello") ? Opcodes.INVOKEINTERFACE : opcode,
                  owner,
                  name,
                  descriptor,
                  isInterface);
            }))
        .transform();
  }

  interface I {
    /* private */ default String privateHello() {
      return "Hello, world!";
    }

    default String hello() {
      // The private method "privateHello" is called with invokeinterface.
      return privateHello();
    }
  }

  public static class TestRunner implements I {

    public static void main(String[] args) {
      System.out.println(new TestRunner().hello());
    }
  }
}
