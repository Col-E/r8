// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.fields.shadow;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.shaking.fields.FieldsTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import org.junit.Test;

@NeverMerge
class Super {
  public int f1;
}

@NeverMerge
class Sub extends Super {
  public int f1;
}

@NeverMerge
class SubSub extends Sub {
  public int f1;
}

class Main {

  private static void printField(Class<?> clazz, String name) {
    try {
      clazz.getDeclaredField(name);
      System.out.println(clazz.getSimpleName() + "." + name + " found");
    } catch (NoSuchFieldException e) {
    }
    System.out.println(clazz.getSimpleName() + "." + name + " not found");
  }

  public static void main(String[] args) {
    printField(Super.class, "f1");
    printField(Sub.class, "f1");
    printField(SubSub.class, "f1");
  }
}

public class ShadowFieldsTest extends FieldsTestBase {

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableSet.of(Super.class, Sub.class, SubSub.class, getMainClass());
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  private void checkAllFields(CodeInspector inspector) {
    ClassSubject superSubject = inspector.clazz(Super.class);
    assertThat(superSubject, isPresent());
    assertThat(superSubject.field("int", "f1"), isPresent());
    ClassSubject subSubject = inspector.clazz(Sub.class);
    assertThat(subSubject, isPresent());
    assertThat(subSubject.field("int", "f1"), isPresent());
    ClassSubject subSubSubject = inspector.clazz(SubSub.class);
    assertThat(subSubSubject, isPresent());
    assertThat(subSubSubject.field("int", "f1"), isPresent());
  }

  @Override
  public String allFieldsOutput() {
    return StringUtils.lines("Super.f1 found", "Sub.f1 found", "SubSub.f1 found");
  }

  @Test
  public void testKeepAllFieldsWithWildcard() throws Throwable {
    runTest("-keep class **.SubSub { *; }", this::checkAllFields, allFieldsOutput());
  }

  @Test
  public void testKeepAllFieldsWithNameWildcard() throws Throwable {
    runTest("-keep class **.SubSub { int f*; }", this::checkAllFields, allFieldsOutput());
  }

  @Test
  public void testKeepAllFieldsWithFields() throws Throwable {
    runTest("-keep class **.SubSub { <fields>; }", this::checkAllFields, allFieldsOutput());
  }

  @Test
  public void testKeepF1() throws Throwable {
    runTest("-keep class **.SubSub { int f1; }", this::checkAllFields, allFieldsOutput());
  }

  @Test
  public void testKeepF1WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { int f1; }", this::checkAllFields, allFieldsOutput());
  }
}
