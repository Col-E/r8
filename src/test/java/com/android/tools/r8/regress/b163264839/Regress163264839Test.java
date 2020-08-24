// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b163264839;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Handle;

@RunWith(Parameterized.class)
public class Regress163264839Test extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;
  private final boolean isInterface;

  @Parameterized.Parameters(name = "{0}, itf:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withApiLevel(AndroidApiLevel.L).build(),
        BooleanUtils.values());
  }

  public Regress163264839Test(TestParameters parameters, boolean isInterface) {
    this.parameters = parameters;
    this.isInterface = isInterface;
  }

  @Test
  public void test() throws Exception {
    TestRunResult<?> result =
        testForRuntime(parameters)
            .addProgramClassFileData(getFunctionClass())
            .addProgramClasses(TestClass.class)
            .run(parameters.getRuntime(), TestClass.class);

    if (isInterface
        || (parameters.isCfRuntime() && parameters.getRuntime().asCf().getVm().equals(CfVm.JDK8))) {
      // JDK 8 allows mismatched method references in this case.
      result.assertSuccessWithOutput(EXPECTED);
    } else {
      result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    }
  }

  private byte[] getFunctionClass() throws Exception {
    // HACK: Use a new name for the lambda implementation method as otherwise ASM will silently
    // change isInterface to the "right" value and we can't test the error case.
    String oldLambdaName = "lambda$identity$0";
    String newLambdaName = "lambda$identity$foo";
    return transformer(Function.class)
        .renameMethod(oldLambdaName, newLambdaName)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitInvokeDynamicInsn(
                  String name,
                  String descriptor,
                  Handle bootstrapMethodHandle,
                  Object... bootstrapMethodArguments) {
                assertEquals(3, bootstrapMethodArguments.length);
                Handle handle = (Handle) bootstrapMethodArguments[1];
                assertTrue(handle.isInterface());
                assertEquals(oldLambdaName, handle.getName());
                Handle newHandle =
                    new Handle(
                        handle.getTag(),
                        handle.getOwner(),
                        newLambdaName,
                        handle.getDesc(),
                        isInterface);
                super.visitInvokeDynamicInsn(
                    name,
                    descriptor,
                    bootstrapMethodHandle,
                    bootstrapMethodArguments[0],
                    newHandle,
                    bootstrapMethodArguments[2]);
              }
            })
        .transform();
  }

  interface Function<R, T> {
    R apply(T t);

    static <T> Function<T, T> identity() {
      return t -> t;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Function.identity().apply("Hello, world"));
    }
  }
}
