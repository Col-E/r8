package com.android.tools.r8.debug;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.JvmSyntheticTest.A;
import com.android.tools.r8.debug.JvmSyntheticTest.Runner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JvmSyntheticTestRunner extends DebugTestBase {

  public static final Class<?> CLASS = Runner.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private TestParameters parameters;

  public JvmSyntheticTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testStacktrace() throws Throwable {
    testForJvm()
        .addProgramClasses(A.class, CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputThatMatches(
            containsString("at com.android.tools.r8.debug.JvmSyntheticTest$A.access$000"))
        .debugger(this::testDebug)
        .debugger(this::testDebugIntelliJ);
  }

  public void testDebug(DebugTestConfig debugTestConfig) throws Throwable {
    runDebugTest(
        debugTestConfig,
        CLASS,
        breakpoint(methodFromMethod(CLASS.getMethod("main", String[].class))),
        run(),
        checkMethod(CLASS.getTypeName(), "main"),
        stepInto(),
        checkMethod(A.class.getTypeName(), "access$000"),
        run());
  }

  public void testDebugIntelliJ(DebugTestConfig debugTestConfig) throws Throwable {
    runDebugTest(
        debugTestConfig,
        CLASS,
        breakpoint(methodFromMethod(CLASS.getMethod("main", String[].class))),
        run(),
        checkMethod(CLASS.getTypeName(), "main"),
        stepInto(INTELLIJ_FILTER),
        checkMethod(A.class.getTypeName(), "foo"),
        run());
  }
}
