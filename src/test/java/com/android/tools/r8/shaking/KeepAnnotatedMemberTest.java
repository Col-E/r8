// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepAnnotatedMemberTest extends TestBase {

  private static final Path R8_JAR = Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8", "r8.jar");
  private static final String ABSENT_ANNOTATION = "com.android.tools.r8.MissingAnnotation";
  private static final String PRESENT_ANNOTATION =
      "com.android.tools.r8.com.google.common.annotations.VisibleForTesting";

  private static final String CLASS_WITH_ANNOTATED_METHOD =
      "com.android.tools.r8.com.google.common.math.IntMath";

  private static final String ANNOTATED_METHOD = "lessThanBranchFree";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepAnnotatedMemberTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testPresence() throws Exception {
    CodeInspector inspector = new CodeInspector(R8_JAR);
    assertThat(inspector.clazz(ABSENT_ANNOTATION), not(isPresent()));
    assertThat(inspector.clazz(PRESENT_ANNOTATION), isPresent());
    ClassSubject clazz = inspector.clazz(CLASS_WITH_ANNOTATED_METHOD);
    MethodSubject method = clazz.uniqueMethodWithName(ANNOTATED_METHOD);
    assertThat(method, isPresent());
  }

  // TODO(b/159966986): A general keep rule should not cause compiler assertion errors.
  @Test(expected = CompilationFailedException.class)
  public void testPresentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .addKeepRules("-keep class * { @" + PRESENT_ANNOTATION + " *; }")
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class)));
  }

  @Test
  public void testPresentAnnotationSplit() throws Exception {
    // These rules should be equivalent to the above.
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .addKeepRules(
            "-keep class *", "-keepclassmembers class * { @" + PRESENT_ANNOTATION + " *; }")
        .compile();
  }

  @Test
  public void testWithMembersAbsentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .allowUnusedProguardConfigurationRules()
        .addKeepRules("-keepclasseswithmembers class * { @" + ABSENT_ANNOTATION + " *; }")
        .compile()
        .inspect(inspector -> assertEquals(0, inspector.allClasses().size()));
  }

  @Test
  public void testWithMembersPresentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .addKeepRules("-keepclasseswithmembers class * { @" + PRESENT_ANNOTATION + " *** *(...); }")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(CLASS_WITH_ANNOTATED_METHOD);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithName(ANNOTATED_METHOD), isPresent());
            });
  }

  @Test
  public void testUnsatisfiedClassMembersPresentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        // TODO(b/159971974): Technically this rule does not hit anything and should fail due to
        //  missing allowUnusedProguardConfigurationRules()
        .addKeepRules("-keepclassmembers class * { @" + PRESENT_ANNOTATION + " *** *(...); }")
        .compile()
        .inspect(inspector -> assertEquals(0, inspector.allClasses().size()));
  }

  @Test
  public void testSatisfiedClassMembersPresentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .addKeepClassRules(CLASS_WITH_ANNOTATED_METHOD)
        .addKeepRules("-keepclassmembers class * { @" + PRESENT_ANNOTATION + " *** *(...); }")
        .compile()
        .inspect(
            inspector -> {
              assertEquals(1, inspector.allClasses().size());
              List<FoundMethodSubject> methods =
                  inspector.clazz(CLASS_WITH_ANNOTATED_METHOD).allMethods();
              assertEquals(
                  1, methods.stream().filter(m -> m.getOriginalName().equals("<init>")).count());
              assertEquals(
                  1,
                  methods.stream()
                      .filter(m -> m.getOriginalName().equals(ANNOTATED_METHOD))
                      .count());
              assertEquals(2, methods.size());
            });
  }

  @Test
  public void testUnsatisfiedConditionalPresentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .allowUnusedProguardConfigurationRules()
        .addKeepRules("-if class * -keep class <1> { @" + PRESENT_ANNOTATION + " *** *(...); }")
        .compile()
        .inspect(inspector -> assertEquals(0, inspector.allClasses().size()));
  }

  @Test
  public void testSatisfiedConditionalPresentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .addKeepClassRules(CLASS_WITH_ANNOTATED_METHOD)
        .addKeepRules("-if class * -keep class <1> { @" + PRESENT_ANNOTATION + " *** *(...); }")
        .compile()
        .inspect(
            inspector -> {
              assertEquals(1, inspector.allClasses().size());
              List<FoundMethodSubject> methods =
                  inspector.clazz(CLASS_WITH_ANNOTATED_METHOD).allMethods();
              assertEquals(
                  1, methods.stream().filter(m -> m.getOriginalName().equals("<init>")).count());
              // TODO(b/132318609): This should have the annotated method, but does not due to the
              //  annotation being removed.
              assertEquals(
                  0,
                  methods.stream()
                      .filter(m -> m.getOriginalName().equals(ANNOTATED_METHOD))
                      .count());
              assertEquals(1, methods.size());
            });
  }

  @Test
  public void testConditionalEqualsKeepClassMembers() throws Exception {
    GraphInspector referenceInspector =
        testForR8(Backend.CF)
            .enableGraphInspector()
            .addProgramFiles(R8_JAR)
            .addKeepMainRule(R8.class)
            .addKeepClassRules(CLASS_WITH_ANNOTATED_METHOD)
            // TODO(b/132318609): Remove keep annotation once fixed.
            .addKeepClassRules(PRESENT_ANNOTATION)
            .addKeepRules("-keepclassmembers class * { @" + PRESENT_ANNOTATION + " *** *(...); }")
            .compile()
            .graphInspector();

    GraphInspector ifThenKeepClassMembersInspector =
        testForR8(Backend.CF)
            .enableGraphInspector()
            .addProgramFiles(R8_JAR)
            .addKeepMainRule(R8.class)
            .addKeepClassRules(CLASS_WITH_ANNOTATED_METHOD)
            // TODO(b/132318609): Remove keep annotation once fixed.
            .addKeepClassRules(PRESENT_ANNOTATION)
            .addKeepRules(
                "-if class * "
                    + "-keepclassmembers class <1> { @"
                    + PRESENT_ANNOTATION
                    + " *** *(...); }")
            .compile()
            .graphInspector();
    assertRetainedClassesEqual(referenceInspector, ifThenKeepClassMembersInspector, false, false);

    GraphInspector ifThenKeepClassesWithMembersInspector =
        testForR8(Backend.CF)
            .enableGraphInspector()
            .addProgramFiles(R8_JAR)
            .addKeepMainRule(R8.class)
            .addKeepClassRules(CLASS_WITH_ANNOTATED_METHOD)
            // TODO(b/132318609): Remove keep annotation once fixed.
            .addKeepClassRules(PRESENT_ANNOTATION)
            .addKeepRules(
                "-if class * "
                    + "-keepclasseswithmembers class <1> { @"
                    + PRESENT_ANNOTATION
                    + " *** *(...); }")
            .compile()
            .graphInspector();
    assertRetainedClassesEqual(
        referenceInspector, ifThenKeepClassesWithMembersInspector, false, false);

    GraphInspector ifHasMemberThenKeepClassInspector =
        testForR8(Backend.CF)
            .enableGraphInspector()
            .addProgramFiles(R8_JAR)
            .addKeepMainRule(R8.class)
            .addKeepClassRules(CLASS_WITH_ANNOTATED_METHOD)
            // TODO(b/132318609): Remove keep annotation once fixed.
            .addKeepClassRules(PRESENT_ANNOTATION)
            .addKeepRules(
                "-if class * { @"
                    + PRESENT_ANNOTATION
                    + " *** *(...); } "
                    + "-keep class <1> { @"
                    + PRESENT_ANNOTATION
                    + " *** <2>(...); }")
            .compile()
            .graphInspector();
    // TODO(b/159418523): Should the reference be equal to the result with the conditional rule?
    assertRetainedClassesEqual(referenceInspector, ifHasMemberThenKeepClassInspector, true, false);
  }

  private void assertRetainedClassesEqual(
      GraphInspector referenceResult,
      GraphInspector conditionalResult,
      boolean expectReferenceIsLarger,
      boolean expectConditionalIsLarger) {
    Set<String> referenceClasses =
        new TreeSet<>(
            referenceResult.codeInspector().allClasses().stream()
                .map(c -> c.getOriginalName())
                .collect(Collectors.toSet()));

    Set<String> conditionalClasses =
        conditionalResult.codeInspector().allClasses().stream()
            .map(c -> c.getOriginalName())
            .collect(Collectors.toSet());
    {
      SetView<String> notInReference = Sets.difference(conditionalClasses, referenceClasses);
      if (expectConditionalIsLarger) {
        assertFalse("Expected classes in -if rule to retain more.", notInReference.isEmpty());
      } else {
        assertEquals(
            "Classes in -if rule that are not in -keepclassmembers rule",
            Collections.emptySet(),
            notInReference);
      }
    }
    {
      SetView<String> notInConditional = Sets.difference(referenceClasses, conditionalClasses);
      if (expectReferenceIsLarger) {
        assertFalse(
            "Expected classes in -keepclassmembers rule to retain more.",
            notInConditional.isEmpty());
      } else {
        assertEquals(
            "Classes in -keepclassmembers rule that are not in -if rule",
            Collections.emptySet(),
            new TreeSet<>(notInConditional));
      }
    }
  }
}
