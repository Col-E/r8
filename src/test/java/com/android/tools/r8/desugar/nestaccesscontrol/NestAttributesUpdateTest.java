// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.PACKAGE_NAME;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.classesMatching;
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
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withAllApiLevels()
        .build();
  }

  @Test
  public void testClassMergingNestMemberRemoval() throws Exception {
    testNestAttributesCorrect(MERGING_OUTER_CLASS, MERGING_OUTER_CLASS, MERGING_EXPECTED_RESULT);
  }

  @Test
  public void testClassMergingNestHostRemoval() throws Exception {
    testNestAttributesCorrect(
        MERGING_OUTER_CLASS + "$MiddleOuter", MERGING_OUTER_CLASS, MERGING_EXPECTED_RESULT);
  }

  @Test
  public void testTreePruningNestMemberRemoval() throws Exception {
    testNestAttributesCorrect(PRUNING_OUTER_CLASS, PRUNING_OUTER_CLASS, PRUNING_EXPECTED_RESULT);
  }

  @Test
  public void testTreePruningNestHostRemoval() throws Exception {
    testNestAttributesCorrect(
        PRUNING_OUTER_CLASS + "$Pruned", PRUNING_OUTER_CLASS, PRUNING_EXPECTED_RESULT);
  }

  public void testNestAttributesCorrect(
      String mainClassName, String outerNestName, String expectedResult) throws Exception {
    testNestAttributesCorrect(mainClassName, outerNestName, expectedResult, true);
    testNestAttributesCorrect(mainClassName, outerNestName, expectedResult, false);
  }

  public void testNestAttributesCorrect(
      String mainClassName, String outerNestName, String expectedResult, boolean minification)
      throws Exception {
    String actualMainClassName = PACKAGE_NAME + mainClassName;
    testForR8(parameters.getBackend())
        .addKeepMainRule(actualMainClassName)
        .minification(minification)
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(classesMatching(outerNestName))
        .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
        .compile()
        .inspect(this::assertNestAttributesCorrect)
        .run(parameters.getRuntime(), actualMainClassName)
        .assertSuccessWithOutput(expectedResult);
  }

  private void assertNestAttributesCorrect(CodeInspector inspector) {
    assertTrue(inspector.allClasses().size() > 0);
    for (FoundClassSubject classSubject : inspector.allClasses()) {
      DexClass clazz = classSubject.getDexClass();
      if (clazz.isInANest()) {
        if (clazz.isNestHost()) {
          // All members are present with the clazz as host
          for (NestMemberClassAttribute attr : clazz.getNestMembersClassAttributes()) {
            String memberName = attr.getNestMember().getName();
            ClassSubject inner = inspector.clazz(PACKAGE_NAME + memberName);
            assertNotNull(
                "The nest member " + memberName + " of " + clazz.type.getName() + " is missing",
                inner.getDexClass());
            assertSame(inner.getDexClass().getNestHost(), clazz.type);
          }
        } else {
          // Nest host is present and with the clazz as member
          String hostName = clazz.getNestHost().getName();
          ClassSubject host = inspector.clazz(PACKAGE_NAME + hostName);
          assertNotNull(
              "The nest host " + hostName + " of " + clazz.type.getName() + " is missing",
              host.getDexClass());
          assertTrue(
              host.getDexClass().getNestMembersClassAttributes().stream()
                  .anyMatch(attr -> attr.getNestMember() == clazz.type));
        }
      }
    }
  }
}
