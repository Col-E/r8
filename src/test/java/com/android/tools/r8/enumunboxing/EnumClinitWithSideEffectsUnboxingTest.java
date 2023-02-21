package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumClinitWithSideEffectsUnboxingTest extends EnumUnboxingTestBase {

  private final EnumKeepRules enumEnumKeepRules;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enum keep rule: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        getAllEnumKeepRules(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public EnumClinitWithSideEffectsUnboxingTest(
      EnumKeepRules enumEnumKeepRules, TestParameters parameters) {
    this.enumEnumKeepRules = enumEnumKeepRules;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumClinitWithSideEffectsUnboxingTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(enumEnumKeepRules.getKeepRules())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    static MyEnum f = MyEnum.A;

    public static void main(String[] args) {}
  }

  enum MyEnum {
    A;

    static {
      System.out.println("Hello world!");
    }
  }
}
