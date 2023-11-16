// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.doctests;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UsesReflectionDocumentationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("on Base", "on Sub");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public UsesReflectionDocumentationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, BaseClass.class, SubClass.class, MyClass.class);
  }

  static class BaseClass {
    void hiddenMethod() {
      System.out.println("on Base");
    }
  }

  static class SubClass extends BaseClass {
    void hiddenMethod() {
      System.out.println("on Sub");
    }
  }

  /// DOC START: UsesReflection on virtual method
  /* DOC TEXT START:
  <p>If for example, your program is reflectively invoking a virtual method on some base class, you
  should annotate the method that is performing the reflection with an annotation describing what
  assumptions the reflective code is making.

  <p>In the following example, the method `foo` is looking up the method with the name
  `hiddenMethod` on objects that are instances of `BaseClass`. It is then invoking the method with
   no other arguments than the receiver.

  <p>The minimal requirement for this code to work is therefore that all methods with the name
  `hiddenMethod` and the empty list of parameters are targeted if they are objects that are
  instances of the class `BaseClass` or subclasses thereof.

  <p>By placing the `UsesReflection` annotation on the method `foo` the annotation is only in
  effect if the method `foo` is determined to be used by the shrinker.
  So, if `foo` turns out to be dead code then the shrinker can remove `foo` and also ignore the
  keep annotation.
  DOC TEXT END */
  static class MyClass {

    @UsesReflection({
      @KeepTarget(
          instanceOfClassConstant = BaseClass.class,
          methodName = "hiddenMethod",
          methodParameters = {})
    })
    public void foo(BaseClass base) throws Exception {
      base.getClass().getDeclaredMethod("hiddenMethod").invoke(base);
    }
  }

  // DOC END

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new MyClass().foo(new BaseClass());
      new MyClass().foo(new SubClass());
    }
  }
}
