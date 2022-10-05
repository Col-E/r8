// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
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

@RunWith(Parameterized.class)
public class RepackageKeepClassMembersTest extends RepackageTestBase {

  public RepackageKeepClassMembersTest(
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
            "-keepclassmembers class * extends " + typeName(Base.class) + " { <fields>; }")
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class, typeName(Sub.class))
        .inspect(this::inspect)
        // TODOD(b/250671873): Should not repackage class.
        .assertSuccessWithOutputLines("Could not find " + typeName(Sub.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Sub.class);
    assertThat(clazz, isPresent());
    // TODOD(b/250671873): Should not repackage class.
    assertThat(Sub.class, isRepackaged(inspector));
    assertThat(clazz.uniqueFieldWithOriginalName("hashCodeCache"), isPresentAndNotRenamed());
    assertThat(clazz.uniqueMethodWithOriginalName("calculateHashCode"), isPresentAndRenamed());
  }

  public static class Base {}

  @NeverClassInline
  public static class Sub extends Base {

    public int hashCodeCache;

    @NeverInline
    public int calculateHashCode() {
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
