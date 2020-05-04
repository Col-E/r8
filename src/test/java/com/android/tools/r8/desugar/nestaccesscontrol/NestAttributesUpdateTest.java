// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.PACKAGE_NAME;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.classesMatching;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAttributesUpdateTest extends TestBase {

  public NestAttributesUpdateTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  private final String MERGING_OUTER_CLASS = "BasicNestHostClassMerging";
  private final String PRUNING_OUTER_CLASS = "BasicNestHostTreePruning";
  private final String MERGING_EXPECTED_RESULT = StringUtils.lines("OuterMiddleInner");
  private final String PRUNING_EXPECTED_RESULT = StringUtils.lines("NotPruned");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build();
  }

  @Test
  public void testClassMergingNestMemberRemoval() throws Exception {
    testNestAttributesCorrect(MERGING_OUTER_CLASS, MERGING_OUTER_CLASS, MERGING_EXPECTED_RESULT, 3);
  }

  @Test
  public void testClassMergingNestHostRemoval() throws Exception {
    testNestAttributesCorrect(
        MERGING_OUTER_CLASS + "$MiddleOuter", MERGING_OUTER_CLASS, MERGING_EXPECTED_RESULT, 2);
  }

  @Test
  public void testTreePruningNestMemberRemoval() throws Exception {
    testNestAttributesCorrect(PRUNING_OUTER_CLASS, PRUNING_OUTER_CLASS, PRUNING_EXPECTED_RESULT, 2);
  }

  @Test
  public void testTreePruningNestHostRemoval() throws Exception {
    testNestAttributesCorrect(
        PRUNING_OUTER_CLASS + "$Pruned", PRUNING_OUTER_CLASS, PRUNING_EXPECTED_RESULT, 1);
  }

  public void testNestAttributesCorrect(
      String mainClassName, String outerNestName, String expectedResult, int expectedNumClassesLeft)
      throws Exception {
    testNestAttributesCorrect(
        mainClassName, outerNestName, expectedResult, true, expectedNumClassesLeft);
    testNestAttributesCorrect(
        mainClassName, outerNestName, expectedResult, false, expectedNumClassesLeft);
  }

  public void testNestAttributesCorrect(
      String mainClassName,
      String outerNestName,
      String expectedResult,
      boolean minification,
      int expectedNumClassesLeft)
      throws Exception {
    String actualMainClassName = PACKAGE_NAME + mainClassName;
    testForR8(parameters.getBackend())
        .addKeepMainRule(actualMainClassName)
        .minification(minification)
        .addOptionsModification(
            options -> {
              // Disable optimizations else additional classes are removed since they become unused.
              options.enableValuePropagation = false;
              options.enableClassInlining = false;
            })
        .addProgramFiles(classesMatching(outerNestName))
        .compile()
        .inspect(
            inspector -> {
              assertEquals(expectedNumClassesLeft, inspector.allClasses().size());
              assertNestAttributesCorrect(inspector);
            })
        .run(parameters.getRuntime(), actualMainClassName)
        .assertSuccessWithOutput(expectedResult);
  }

  public static void assertNestAttributesCorrect(CodeInspector inspector) {
    assertTrue(inspector.allClasses().size() > 0);
    for (FoundClassSubject classSubject : inspector.allClasses()) {
      DexClass clazz = classSubject.getDexProgramClass();
      if (clazz.isInANest()) {
        if (clazz.isNestHost()) {
          // All members are present with the clazz as host
          for (NestMemberClassAttribute attr : clazz.getNestMembersClassAttributes()) {
            String memberName = attr.getNestMember().getName();
            ClassSubject inner = inspector.clazz(PACKAGE_NAME + memberName);
            assertNotNull(
                "The nest member " + memberName + " of " + clazz.type.getName() + " is missing",
                inner.getDexProgramClass());
            assertSame(inner.getDexProgramClass().getNestHost(), clazz.type);
          }
        } else {
          // Nest host is present and with the clazz as member
          String hostName = clazz.getNestHost().getName();
          ClassSubject host = inspector.clazz(PACKAGE_NAME + hostName);
          assertNotNull(
              "The nest host " + hostName + " of " + clazz.type.getName() + " is missing",
              host.getDexProgramClass());
          assertTrue(
              host.getDexProgramClass().getNestMembersClassAttributes().stream()
                  .anyMatch(attr -> attr.getNestMember() == clazz.type));
        }
      }
    }
  }
}
