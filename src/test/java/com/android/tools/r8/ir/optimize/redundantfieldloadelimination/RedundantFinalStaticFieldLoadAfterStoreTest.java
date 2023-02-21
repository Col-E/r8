package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantFinalStaticFieldLoadAfterStoreTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public RedundantFinalStaticFieldLoadAfterStoreTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RedundantFinalStaticFieldLoadAfterStoreTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42", "42", "42", "42");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    FieldSubject fFieldSubject = aClassSubject.uniqueFieldWithOriginalName("f");
    assertThat(fFieldSubject, isPresent());

    MethodSubject initMethodSubject = aClassSubject.clinit();
    assertThat(initMethodSubject, isPresent());
    assertEquals(
        0,
        countStaticGetInstructions(
            initMethodSubject.asFoundMethodSubject(), fFieldSubject.asFoundFieldSubject()));

    MethodSubject mMethodSubject = aClassSubject.uniqueMethodWithOriginalName("m");
    assertThat(mMethodSubject, isPresent());
    assertEquals(
        1,
        countStaticGetInstructions(
            mMethodSubject.asFoundMethodSubject(), fFieldSubject.asFoundFieldSubject()));
  }

  private long countStaticGetInstructions(
      FoundMethodSubject methodSubject, FoundFieldSubject fieldSubject) {
    return methodSubject
        .streamInstructions()
        .filter(InstructionSubject::isStaticGet)
        .map(InstructionSubject::getField)
        .filter(fieldSubject.getField().getReference()::equals)
        .count();
  }

  static class TestClass {

    public static void main(String[] args) {
      A.m();
    }
  }

  static class A {

    @NeverPropagateValue static final long f;

    static {
      f = System.currentTimeMillis() > 0 ? 42 : 0;
      killNonFinalActiveFields();
      System.out.println(f); // Redundant, since `f` is final and guaranteed to be initialized.
      killNonFinalActiveFields();
      System.out.println(f); // Redundant, since `f` is final and guaranteed to be initialized.
    }

    @NeverInline
    static void m() {
      System.out.println(A.f);
      killNonFinalActiveFields();
      System.out.println(A.f); // Redundant, since `f` is guaranteed to be initialized.
    }

    @NeverInline
    static void killNonFinalActiveFields() {
      if (System.currentTimeMillis() < 0) {
        System.out.println(A.class);
      }
    }
  }
}
