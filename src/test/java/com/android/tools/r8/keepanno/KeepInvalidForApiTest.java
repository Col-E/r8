// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepInvalidForApiTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepInvalidForApiTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static List<String> extractRuleForClass(Class<?> clazz) throws IOException {
    Set<KeepDeclaration> keepEdges =
        KeepEdgeReader.readKeepEdges(ToolHelper.getClassAsBytes(clazz));
    List<String> rules = new ArrayList<>();
    KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add);
    keepEdges.forEach(extractor::extract);
    return rules;
  }

  private void assertThrowsWith(ThrowingRunnable fn, Matcher<String> matcher) {
    try {
      fn.run();
    } catch (KeepEdgeException e) {
      assertThat(e.getMessage(), matcher);
      return;
    } catch (Throwable e) {
      fail("Expected run to fail with KeepEdgeException, but failed with: " + e);
    }
    fail("Expected run to fail");
  }

  @Test
  public void testInvalidMemberAccess() {
    assertThrowsWith(
        () -> extractRuleForClass(RefineMemberAccess.class),
        allOf(
            containsString("Unexpected array"),
            containsString("@KeepForApi"),
            containsString("memberAccess")));
  }

  static class RefineMemberAccess {

    @KeepForApi(memberAccess = {MemberAccessFlags.PUBLIC})
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidMethodName() {
    assertThrowsWith(
        () -> extractRuleForClass(RefineMethodName.class),
        allOf(
            containsString("Unexpected value"),
            containsString("@KeepForApi"),
            containsString("methodName")));
  }

  static class RefineMethodName {

    @KeepForApi(methodName = "foo")
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidFieldName() {
    assertThrowsWith(
        () -> extractRuleForClass(RefineFieldName.class),
        allOf(
            containsString("Unexpected value"),
            containsString("@KeepForApi"),
            containsString("fieldName")));
  }

  static class RefineFieldName {

    @KeepForApi(fieldName = "foo")
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
