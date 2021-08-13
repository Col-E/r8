// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class CallSiteOptimizationWithInvokeCustomTargetTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("Hello world!");

  private final boolean enableExperimentalArgumentPropagation;
  private final TestParameters parameters;

  @Parameters(name = "{1}, experimental: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withAllRuntimes()
            // Only works when invoke-custom/dynamic are supported and ConstantCallSite defined.
            .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
            .build());
  }

  public CallSiteOptimizationWithInvokeCustomTargetTest(
      boolean enableExperimentalArgumentPropagation, TestParameters parameters) {
    this.enableExperimentalArgumentPropagation = enableExperimentalArgumentPropagation;
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(TestClass.class)
        .addKeepMethodRules(methodFromMethod(TestClass.class.getDeclaredMethod("bar", int.class)))
        .applyIf(
            enableExperimentalArgumentPropagation,
            builder ->
                builder.addOptionsModification(
                    options ->
                        options
                            .callSiteOptimizationOptions()
                            .setEnableExperimentalArgumentPropagation()))
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              assertThat(clazz.uniqueMethodWithName("bootstrap"), isPresent());
              assertThat(clazz.uniqueMethodWithName("bar"), isPresent());
              assertThat(clazz.uniqueMethodWithName("foo"), not(isPresent()));
            });
  }

  private List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(TestClass.class)
            .transformMethodInsnInMethod(
                "main",
                (opcode, owner, name, descriptor, isInterface, visitor) -> {
                  if (opcode == Opcodes.INVOKESTATIC && name.equals("foo")) {
                    visitor.visitInvokeDynamicInsn(
                        "foo",
                        "(I)V",
                        new Handle(
                            Opcodes.H_INVOKESTATIC,
                            binaryName(TestClass.class),
                            "bootstrap",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                            false));
                  } else {
                    visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform());
  }

  static class TestClass {

    @NeverInline
    static CallSite bootstrap(MethodHandles.Lookup lookup, String unused, MethodType type)
        throws NoSuchMethodException, IllegalAccessException {
      return lookup != null
          ? new ConstantCallSite(
              // Reflective access of bar, needs a keep rule.
              lookup.findStatic(TestClass.class, "bar", type))
          : null;
    }

    // Target of the bootstrap method.
    static void bar(int i) {
      if (i == 42) {
        System.out.println("Hello world!");
      }
    }

    // Placeholder. Never called.
    static void foo(int i) {
      throw null;
    }

    public static void main(String[] args) throws Exception {
      // Direct call to the bootstrap method with constant arguments, triggering call-site opt.
      bootstrap(null, null, null);
      // Rewritten to invoke-dynamic foo(I)V, bsm:TestClass::bootstrap
      TestClass.foo(args.length + 42);
    }
  }
}
