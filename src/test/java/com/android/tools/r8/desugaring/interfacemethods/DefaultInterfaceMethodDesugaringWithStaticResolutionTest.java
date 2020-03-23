package com.android.tools.r8.desugaring.interfacemethods;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodDesugaringWithStaticResolutionTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultInterfaceMethodDesugaringWithStaticResolutionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVM() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("I.m()");
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    D8TestRunResult result =
        testForD8()
            .addInnerClasses(DefaultInterfaceMethodDesugaringWithStaticResolutionTest.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .run(parameters.getRuntime(), TestClass.class);
    // TODO(b/152163087): Should always succeed with "I.m()".
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      result.assertSuccessWithOutputLines("I.m()");
    } else {
      result.assertFailureWithErrorThatMatches(
          containsString(AbstractMethodError.class.getTypeName()));
    }
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(DefaultInterfaceMethodDesugaringWithStaticResolutionTest.class)
            .addKeepAllClassesRule()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .run(parameters.getRuntime(), TestClass.class);
    // TODO(b/152163087): Should always succeed with "I.m()".
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      result.assertSuccessWithOutputLines("I.m()");
    } else {
      result.assertFailureWithErrorThatMatches(
          containsString(AbstractMethodError.class.getTypeName()));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      I b = new B();
      b.m();
    }
  }

  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  static class A {

    private static void m() {
      System.out.println("A.m()");
    }
  }

  static class B extends A implements I {}
}
