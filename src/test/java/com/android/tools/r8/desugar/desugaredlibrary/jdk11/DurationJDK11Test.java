// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DurationJDK11Test extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("7");

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public DurationJDK11Test(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    Assume.assumeFalse(
        "TODO(b/206068300)",
        parameters.getApiLevel().getLevel() >= AndroidApiLevel.O.getLevel()
            && parameters.getApiLevel().getLevel() < AndroidApiLevel.S.getLevel());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    Assume.assumeFalse(
        "TODO(b/206068300)",
        parameters.getApiLevel().getLevel() >= AndroidApiLevel.O.getLevel()
            && parameters.getApiLevel().getLevel() < AndroidApiLevel.S.getLevel());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private Collection<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(TestClass.class)
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String name,
                      String descriptor,
                      boolean isInterface) {
                    if (opcode == Opcodes.INVOKESTATIC && name.equals("toHoursPart")) {
                      super.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/time/Duration",
                          name,
                          withoutFirstObjectArg(descriptor),
                          isInterface);
                      return;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform());
  }

  private String withoutFirstObjectArg(String descriptor) {
    int i = descriptor.indexOf(";");
    return "(" + descriptor.substring(i + 1);
  }

  public static class TestClass {

    public static void main(String[] args) {
      Duration duration = Duration.ofHours(31);
      System.out.println(toHoursPart(duration));
    }

    // Replaced in the transformer by JDK 11 Duration#toHoursPart.
    private static int toHoursPart(Duration duration) {
      return -1;
    }
  }
}
