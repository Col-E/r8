// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IfRuleWithFieldBasedOnClassExtends extends TestBase {

  static final String EXPECTED = "foobar";
  public static final String CONDITIONAL_KEEP_RULE =
      "-if class * extends "
          + Base.class.getTypeName()
          + "\n"
          + " -keepclassmembers class * {\n"
          + "    <1> *;\n"
          + "}";
  public static final String CONDITIONAL_KEEP_RULE_FOR_SAME_CLASS =
      "-if class * extends "
          + Extending.class.getTypeName()
          + "\n"
          + " -keepclassmembers class * {\n"
          + "    <1> *;\n"
          + "}";
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfRuleWithFieldBasedOnClassExtends(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    // Validate that we keep the field if the conditional rule is used
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Extending.class)
        .addKeepRules(CONDITIONAL_KEEP_RULE)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            codeInspector -> {
              assertThat(
                  codeInspector
                      .clazz(UsingExtendingAsField.class)
                      .uniqueFieldWithOriginalName("shouldBeKept"),
                  isPresent());
              // The rest of the fields are gone
              assertEquals(
                  codeInspector.clazz(UsingExtendingAsField.class).allFields().stream().count(), 1);
            })
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8NotKept() throws Exception {
    // Validate that we don't keep the field if the conditional rule is not used
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Extending.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NoSuchFieldException.class);
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addDontWarn(getClass())
        .addKeepClassAndMembersRules(Extending.class)
        .addKeepRules(CONDITIONAL_KEEP_RULE)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  // It would often be easier to write keep rules if a given class was considered to extend itself,
  // but that is not the case (see also test below for proguard)
  public void testR8SameClassInCondition() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Extending.class)
        .addKeepRules(CONDITIONAL_KEEP_RULE_FOR_SAME_CLASS)
        .allowUnusedProguardConfigurationRules()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NoSuchFieldException.class);
  }

  @Test
  public void testProguardSameClassInCondition() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addDontWarn(getClass())
        .addKeepClassAndMembersRules(Extending.class)
        .addKeepRules(CONDITIONAL_KEEP_RULE_FOR_SAME_CLASS)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NoSuchFieldException.class);
  }

  public static class Base {}

  public static class Extending extends Base {
    String foo = "foo";
    String bar = "bar";
  }

  public static class UsingExtendingAsField {
    Extending shouldBeKept = new Extending();
    Base baseField = null;
    String otherField = "never_used";
  }

  public static class TestClass {

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
      UsingExtendingAsField using = new UsingExtendingAsField();
      Object shouldBeKept = using.getClass().getDeclaredField("shouldBeKept").get(using);
      System.out.print(shouldBeKept.getClass().getDeclaredField("foo").get(shouldBeKept));
      System.out.println(shouldBeKept.getClass().getDeclaredField("bar").get(shouldBeKept));
    }
  }
}
