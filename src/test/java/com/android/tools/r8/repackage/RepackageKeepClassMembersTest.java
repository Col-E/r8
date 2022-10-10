// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.repackage.testclasses.RepackageForKeepClassMembers;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageKeepClassMembersTest extends RepackageTestBase {

  private final String EXPECTED = "Could not find " + typeName(RepackageForKeepClassMembers.class);

  public RepackageKeepClassMembersTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testPG() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addProgramClasses(RepackageForKeepClassMembers.class, Main.class)
        .setMinApi(parameters.getApiLevel())
        .apply(this::configureRepackaging)
        .addKeepRules(
            "-keepclassmembers class " + typeName(RepackageForKeepClassMembers.class) + " { *; }")
        .addKeepMainRule(Main.class)
        .addDontWarn(RepackageKeepClassMembersTest.class, NeverClassInline.class)
        .run(parameters.getRuntime(), Main.class, typeName(RepackageForKeepClassMembers.class))
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(RepackageForKeepClassMembers.class, Main.class)
        .setMinApi(parameters.getApiLevel())
        .apply(this::configureRepackaging)
        .addKeepRules(
            "-keepclassmembers class " + typeName(RepackageForKeepClassMembers.class) + " { *; }")
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class, typeName(RepackageForKeepClassMembers.class))
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(RepackageForKeepClassMembers.class);
    assertThat(clazz, isPresent());
    assertThat(RepackageForKeepClassMembers.class, isRepackaged(inspector));
    assertThat(clazz.uniqueFieldWithOriginalName("hashCodeCache"), isPresentAndNotRenamed());
    assertThat(clazz.uniqueMethodWithOriginalName("calculateHashCode"), isPresentAndNotRenamed());
  }

  public static class Main {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(new RepackageForKeepClassMembers().hashCode());
      }
      try {
        Class<?> subClass = Class.forName(args[0]);
        Object subInstance = subClass.getDeclaredConstructor().newInstance();
        subClass.getMethod("calculateHashCode").invoke(subInstance);
        System.out.println("Hello World!");
      } catch (Exception e) {
        System.out.println("Could not find " + args[0]);
      }
    }
  }
}
