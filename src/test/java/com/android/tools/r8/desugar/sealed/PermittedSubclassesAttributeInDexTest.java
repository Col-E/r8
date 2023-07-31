// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.sealed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class PermittedSubclassesAttributeInDexTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("true", "true");

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(
        parameters.isCfRuntime()
            && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)
            && parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(Sub1.class, Sub2.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    assertEquals(
        ImmutableList.of(
            inspector.clazz(Sub1.class).asTypeSubject(),
            inspector.clazz(Sub2.class).asTypeSubject()),
        inspector.clazz(C.class).getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(Sub1.class, Sub2.class)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitPermittedSubclassesAnnotationsInDex = true)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            // TODO(b/270941147): Partial DEX support in Android U DP1 (reflective APIs).
            parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V14_0_0),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class));
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    ClassFileTransformer transformer =
        transformer(TestClass.class)
            .setMinVersion(CfVm.JDK17)
            .transformMethodInsnInMethod(
                "main",
                ((opcode, owner, name, descriptor, isInterface, continuation) -> {
                  if (owner.equals(DescriptorUtils.getClassBinaryName(AdditionalClassAPIs.class))) {
                    if (name.equals("getPermittedSubclasses")) {
                      continuation.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/lang/Class",
                          "getPermittedSubclasses",
                          "()[Ljava/lang/Class;",
                          false);
                    } else if (name.equals("isSealed")) {
                      continuation.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL, "java/lang/Class", "isSealed", "()Z", false);
                    } else {
                      fail("Unsupported rewriting of API " + owner + "." + name + descriptor);
                    }
                  } else {
                    continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                }));

    return ImmutableList.of(
        transformer.transform(),
        transformer(C.class).setPermittedSubclasses(C.class, Sub1.class, Sub2.class).transform());
  }

  static class AdditionalClassAPIs {
    public static Class<?>[] getPermittedSubclasses(Class<?> clazz) {
      throw new RuntimeException();
    }

    public static boolean isSealed(Class<?> clazz) {
      throw new RuntimeException();
    }
  }

  static class TestClass {

    public static boolean sameArrayContent(Class<?>[] array1, Class<?>[] array2) {
      Set<Class<?>> expected = new HashSet<>(Arrays.asList(array1));
      for (Class<?> clazz : array2) {
        if (!expected.remove(clazz)) {
          return false;
        }
      }
      return expected.isEmpty();
    }

    public static void main(String[] args) {
      System.out.println(AdditionalClassAPIs.isSealed(C.class));
      System.out.println(
          sameArrayContent(
              new Class<?>[] {Sub1.class, Sub2.class},
              AdditionalClassAPIs.getPermittedSubclasses(C.class)));
    }
  }

  static class C {}

  static class Sub1 extends C {}

  static class Sub2 extends C {}
}
