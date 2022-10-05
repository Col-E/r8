// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This is a reproduction of b/250671873. */
@RunWith(Parameterized.class)
public class RepackageProtectedInSamePackageTest extends RepackageTestBase {

  public RepackageProtectedInSamePackageTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .apply(this::configureRepackaging)
        .addKeepRules(
            "-keepclassmembers,allowobfuscation class * extends "
                + typeName(Base.class)
                + " { <fields>; }")
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines("Hello World!");
  }

  private void inspect(CodeInspector inspector) {
    // TODO(b/250671873): We should be able to repackage the Sub class since the only reference
    //  to Sub.class is in the same package and we have allowobfuscation.
    ClassSubject clazz = inspector.clazz(Sub.class);
    assertThat(clazz, isPresent());
    assertThat(Sub.class, isNotRepackaged(inspector));
    assertThat(clazz.uniqueFieldWithOriginalName("hashCodeCache"), isPresentAndRenamed());
    assertThat(clazz.uniqueMethodWithOriginalName("calculateHashCode"), isPresentAndRenamed());
  }

  public static class Base {}

  @NeverClassInline
  public static class Sub extends Base {

    protected int hashCodeCache;

    @NeverInline
    protected int calculateHashCode() {
      if (hashCodeCache != -1) {
        return hashCodeCache;
      }
      hashCodeCache = Objects.hash(Sub.class);
      return hashCodeCache;
    }

    @Override
    public int hashCode() {
      return calculateHashCode();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(new Sub().hashCode());
      }
      System.out.println("Hello World!");
    }
  }
}
