// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.prvt;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.shaking.methods.MethodsTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;

@NeverMerge
class Super {
  private void m1() {}
}

@NeverMerge
class Sub extends Super {
  private void m2() {}
}

@NeverMerge
class SubSub extends Sub {
  private void m3() {}
}

class Main {

  private static void printMethod(Class<?> clazz, String name) {
    try {
      clazz.getDeclaredMethod(name);
      System.out.println(clazz.getSimpleName() + "." + name + " found");
    } catch (NoSuchMethodException e) {
      System.out.println(clazz.getSimpleName() + "." + name + " not found");
    }
  }

  public static void main(String[] args) {
    printMethod(Super.class, "m1");
    printMethod(Sub.class, "m2");
    printMethod(SubSub.class, "m3");
  }
}

public class PrivateMethodsTest extends MethodsTestBase {

  public Collection<Class<?>> getClasses() {
    return ImmutableSet.of(Super.class, Sub.class, SubSub.class, getMainClass());
  }

  public Class<?> getMainClass() {
    return Main.class;
  }

  private void checkMethods(CodeInspector inspector, Shrinker shrinker, Set<String> expected) {
    ClassSubject superSubject = inspector.clazz(Super.class);
    assertThat(superSubject, isPresent());
    assertEquals(
        shrinker != Shrinker.R8Full && expected.contains("m1"),
        superSubject.uniqueMethodWithName("m1").isPresent());
    ClassSubject subSubject = inspector.clazz(Sub.class);
    assertThat(subSubject, isPresent());
    assertEquals(
        shrinker != Shrinker.R8Full && expected.contains("m2"),
        subSubject.uniqueMethodWithName("m2").isPresent());
    ClassSubject subSubSubject = inspector.clazz(SubSub.class);
    assertThat(subSubSubject, isPresent());
    assertEquals(expected.contains("m3"), subSubSubject.uniqueMethodWithName("m3").isPresent());
  }

  public String allMethodsOutput(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines("Super.m1 found", "Sub.m2 found", "SubSub.m3 found");
    } else {
      return StringUtils.lines("Super.m1 not found", "Sub.m2 not found", "SubSub.m3 found");
    }
  }

  public String onlyM1Output(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines("Super.m1 found", "Sub.m2 not found", "SubSub.m3 not found");
    } else {
      return StringUtils.lines("Super.m1 not found", "Sub.m2 not found", "SubSub.m3 not found");
    }
  }

  public String onlyM2Output(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines("Super.m1 not found", "Sub.m2 found", "SubSub.m3 not found");
    } else {
      return StringUtils.lines("Super.m1 not found", "Sub.m2 not found", "SubSub.m3 not found");
    }
  }

  private void checkAllMethods(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, shrinker, ImmutableSet.of("m1", "m2", "m3"));
  }

  private void checkOnlyM1(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, shrinker, ImmutableSet.of("m1"));
  }

  private void checkOnlyM2(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, shrinker, ImmutableSet.of("m2"));
  }

  private void checkOnlyM3(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, shrinker, ImmutableSet.of("m3"));
  }

  @Test
  public void testKeepAllMethodsWithWildcard() throws Throwable {
    runTest("-keep class **.SubSub { *; }", this::checkAllMethods, this::allMethodsOutput);
  }

  @Test
  public void testKeepAllMethodsWithNameWildcard() throws Throwable {
    runTest("-keep class **.SubSub { void m*(); }", this::checkAllMethods, this::allMethodsOutput);
  }

  @Test
  public void testKeepAllMethodsWithMethods() throws Throwable {
    runTest("-keep class **.SubSub { <methods>; }", this::checkAllMethods, this::allMethodsOutput);
  }

  @Test
  public void testKeepM1() throws Throwable {
    runTest("-keep class **.SubSub { void m1(); }", this::checkOnlyM1, this::onlyM1Output);
  }

  @Test
  public void testKeepM2() throws Throwable {
    runTest("-keep class **.SubSub { void m2(); }", this::checkOnlyM2, this::onlyM2Output);
  }

  @Test
  public void testKeepM3() throws Throwable {
    runTest("-keep class **.SubSub { void m3(); }", this::checkOnlyM3, onlyM3Output());
  }

  @Test
  public void testKeepM1WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m1(); }", this::checkOnlyM1, this::onlyM1Output);
  }

  @Test
  public void testKeepM2WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m2(); }", this::checkOnlyM2, this::onlyM2Output);
  }

  @Test
  public void testKeepM3WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m3(); }", this::checkOnlyM3, onlyM3Output());
  }
}
