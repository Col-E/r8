// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.fields.pblc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.shaking.fields.FieldsTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;

@NeverMerge
class Super {
  public int f1;
}

@NeverMerge
class Sub extends Super {
  public int f2;
}

@NeverMerge
class SubSub extends Sub {
  public int f3;
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
    printField(Sub.class, "f2");
    printField(SubSub.class, "f3");
  }
}

public class PublicFieldsTest extends FieldsTestBase {

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableSet.of(Super.class, Sub.class, SubSub.class, getMainClass());
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  private void checkFields(CodeInspector inspector, Set<String> expected) {
    ClassSubject superSubject = inspector.clazz(Super.class);
    assertThat(superSubject, isPresent());
    assertEquals(superSubject.field("int", "f1").isPresent(), expected.contains("f1"));
    ClassSubject subSubject = inspector.clazz(Sub.class);
    assertThat(subSubject, isPresent());
    assertEquals(subSubject.field("int", "f2").isPresent(), expected.contains("f2"));
    ClassSubject subSubSubject = inspector.clazz(SubSub.class);
    assertThat(subSubSubject, isPresent());
    assertEquals(subSubSubject.field("int", "f3").isPresent(), expected.contains("f3"));
  }

  private void checkAllFields(CodeInspector inspector) {
    checkFields(inspector, ImmutableSet.of("f1", "f2", "f3"));
  }

  private void checkOnlyF1(CodeInspector inspector) {
    checkFields(inspector, ImmutableSet.of("f1"));
  }

  private void checkOnlyF2(CodeInspector inspector) {
    checkFields(inspector, ImmutableSet.of("f2"));
  }

  private void checkOnlyF3(CodeInspector inspector) {
    checkFields(inspector, ImmutableSet.of("f3"));
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
    runTest("-keep class **.SubSub { int f1; }", this::checkOnlyF1, onlyF1Output());
  }

  @Test
  public void testKeepF2() throws Throwable {
    runTest("-keep class **.SubSub { int f2; }", this::checkOnlyF2, onlyF2Output());
  }

  @Test
  public void testKeepF3() throws Throwable {
    runTest("-keep class **.SubSub { int f3; }", this::checkOnlyF3, onlyF3Output());
  }

  @Test
  public void testKeepF1WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { int f1; }", this::checkOnlyF1, onlyF1Output());
  }

  @Test
  public void testKeepF2WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { int f2; }", this::checkOnlyF2, onlyF2Output());
  }

  @Test
  public void testKeepF3WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { int f3; }", this::checkOnlyF3, onlyF3Output());
  }
}
