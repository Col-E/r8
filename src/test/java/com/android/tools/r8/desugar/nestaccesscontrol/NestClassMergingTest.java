package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.PACKAGE_NAME;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.classesMatching;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestClassMergingTest extends TestBase {

  public NestClassMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  private final String NEST_MAIN_CLASS = PACKAGE_NAME + "NestHostInlining";
  private final String NEST_SUBCLASS_MAIN_CLASS = PACKAGE_NAME + "NestHostInliningSubclasses";
  private final String OUTSIDE_WITH_ACCESS_MAIN_CLASS = PACKAGE_NAME + "OutsideInliningWithAccess";
  private final String OUTSIDE_NO_ACCESS_MAIN_CLASS = PACKAGE_NAME + "OutsideInliningNoAccess";
  private final String NEST_MAIN_EXPECTED_RESULT =
      StringUtils.lines("inlining", "InnerNoPrivAccess");
  private final String NEST_SUBCLASS_MAIN_EXPECTED_RESULT =
      StringUtils.lines("inliningSubclass", "InnerNoPrivAccessSubclass");
  private final String OUTSIDE_WITH_ACCESS_MAIN_EXPECTED_RESULT =
      StringUtils.lines("OutsideInliningNoAccess", "inlining");
  private final String OUTSIDE_NO_ACCESS_MAIN_EXPECTED_RESULT =
      StringUtils.lines("OutsideInliningNoAccess");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .build();
  }

  @Test
  public void testClassMergeAcrossTwoNests() throws Exception {
    // Potentially merge classes from one nest with classes from another nest.
    testClassMergeAcrossNest(
        new String[] {NEST_MAIN_CLASS}, new String[] {NEST_MAIN_EXPECTED_RESULT});
    testClassMergeAcrossNest(
        new String[] {NEST_SUBCLASS_MAIN_CLASS}, new String[] {NEST_SUBCLASS_MAIN_EXPECTED_RESULT});
    testClassMergeAcrossNest(
        new String[] {NEST_MAIN_CLASS, NEST_SUBCLASS_MAIN_CLASS},
        new String[] {NEST_MAIN_EXPECTED_RESULT, NEST_SUBCLASS_MAIN_EXPECTED_RESULT});
  }

  @Test
  public void testClassMergeAcrossNestAndNonNest() throws Exception {
    // Potentially merge classes from a nest with non nest classes.
    testClassMergeAcrossNest(
        new String[] {
          NEST_MAIN_CLASS, OUTSIDE_NO_ACCESS_MAIN_CLASS, OUTSIDE_WITH_ACCESS_MAIN_CLASS
        },
        new String[] {
          NEST_MAIN_EXPECTED_RESULT,
          OUTSIDE_NO_ACCESS_MAIN_EXPECTED_RESULT,
          OUTSIDE_WITH_ACCESS_MAIN_EXPECTED_RESULT
        });
    testClassMergeAcrossNest(
        new String[] {OUTSIDE_NO_ACCESS_MAIN_CLASS},
        new String[] {OUTSIDE_NO_ACCESS_MAIN_EXPECTED_RESULT});
    testClassMergeAcrossNest(
        new String[] {OUTSIDE_WITH_ACCESS_MAIN_CLASS},
        new String[] {OUTSIDE_WITH_ACCESS_MAIN_EXPECTED_RESULT});
  }

  public void testClassMergeAcrossNest(String[] mainClasses, String[] expectedResults)
      throws Exception {
    List<Path> bothNestsAndOutsideClassToCompile = classesMatching("Inlining");
    R8FullTestBuilder r8FullTestBuilder = testForR8(parameters.getBackend());
    for (String clazz : mainClasses) {
      r8FullTestBuilder.addKeepMainRule(clazz);
    }
    R8TestCompileResult compileResult =
        r8FullTestBuilder
            .addOptionsModification(
                options -> {
                  // Disable optimizations else additional classes are removed since they become
                  // unused.
                  options.enableValuePropagation = false;
                  options.enableClassInlining = false;
                  options.enableNestReduction = false;
                })
            .enableInliningAnnotations()
            .addProgramFiles(bothNestsAndOutsideClassToCompile)
            .compile()
            .inspect(NestAttributesUpdateTest::assertNestAttributesCorrect);
    for (int i = 0; i < mainClasses.length; i++) {
      compileResult
          .run(parameters.getRuntime(), mainClasses[i])
          .assertSuccessWithOutput(expectedResults[i]);
    }
  }
}
