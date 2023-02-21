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
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This is a reproduction of b/250671873. */
@RunWith(Parameterized.class)
public class RepackageProtectedInSamePackageTest extends RepackageTestBase {

  private final String EXPECTED = "Hello World!";

  public RepackageProtectedInSamePackageTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testPG() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getRepackageForKeepClassMembersWithProtectedAccess())
        .setMinApi(parameters)
        .apply(this::configureRepackaging)
        .addKeepRules(
            "-keepclassmembers class " + typeName(RepackageForKeepClassMembers.class) + " { *; }")
        .addKeepMainRule(Main.class)
        .addDontWarn(RepackageProtectedInSamePackageTest.class, NeverClassInline.class)
        .run(parameters.getRuntime(), Main.class, typeName(RepackageForKeepClassMembers.class))
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getRepackageForKeepClassMembersWithProtectedAccess())
        .setMinApi(parameters)
        .apply(this::configureRepackaging)
        .addKeepRules(
            "-keepclassmembers class " + typeName(RepackageForKeepClassMembers.class) + " { *; }")
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] getRepackageForKeepClassMembersWithProtectedAccess() throws Exception {
    return transformer(RepackageForKeepClassMembers.class)
        .setAccessFlags(
            MethodPredicate.onName("calculateHashCode"),
            methodAccessFlags -> {
              methodAccessFlags.unsetPublic();
              methodAccessFlags.setProtected();
            })
        .setAccessFlags(
            FieldPredicate.onName("hashCodeCache"),
            fieldAccessFlags -> {
              fieldAccessFlags.unsetPublic();
              fieldAccessFlags.setProtected();
            })
        .transform();
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
      System.out.println("Hello World!");
    }
  }
}
