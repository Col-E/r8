// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.lambdanames;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PackageDependentLambdaNamesTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;
  private final boolean samePackage;

  @Parameterized.Parameters(name = "{0}, same-pkg:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  public PackageDependentLambdaNamesTest(TestParameters parameters, boolean samePackage) {
    this.parameters = parameters;
    this.samePackage = samePackage;
  }

  @Test
  public void test() throws Exception {
    TestRunResult<?> result =
        testForRuntime(parameters)
            .addProgramClasses(StringConsumer.class)
            .addProgramClassFileData(getTestClass(), getA(), getB())
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED);
    if (parameters.isDexRuntime()) {
      result.inspect(
          inspector -> {
            // When in the same package we expect the two System.out::print lambdas to be shared.
            assertEquals(
                samePackage ? 2 : 3,
                inspector.allClasses().stream()
                    .filter(c -> c.isSynthesizedJavaLambdaClass())
                    .count());
          });
    }
  }

  private byte[] getA() throws Exception {
    ClassFileTransformer transformer = transformer(A.class);
    if (!samePackage) {
      transformer.setClassDescriptor("La/A;");
    }
    return transformer.transform();
  }

  private byte[] getB() throws Exception {
    ClassFileTransformer transformer = transformer(B.class);
    if (!samePackage) {
      transformer.setClassDescriptor("Lb/B;");
    }
    return transformer.transform();
  }

  private byte[] getTestClass() throws Exception {
    ClassFileTransformer transformer = transformer(TestClass.class);
    if (!samePackage) {
      transformer
          .replaceClassDescriptorInMethodInstructions(
              DescriptorUtils.javaTypeToDescriptor(A.class.getTypeName()), "La/A;")
          .replaceClassDescriptorInMethodInstructions(
              DescriptorUtils.javaTypeToDescriptor(B.class.getTypeName()), "Lb/B;");
    }
    return transformer.transform();
  }

  @FunctionalInterface
  public interface StringConsumer {
    void accept(String arg);
  }

  public static class A {
    public void hello() {
      TestClass.apply(System.out::print, "Hello, ");
    }
  }

  public static class B {
    public void world() {
      TestClass.apply(System.out::print, "world");
    }
  }

  public static class TestClass {

    public static void apply(StringConsumer consumer, String arg) {
      consumer.accept(arg);
    }

    public static void main(String[] args) {
      new A().hello();
      new B().world();
      apply(System.out::println, "");
    }
  }
}
