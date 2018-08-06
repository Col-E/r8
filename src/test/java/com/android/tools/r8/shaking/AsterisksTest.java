// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class B111974287 {
  B111974287 self;
  B111974287[] clones;

  B111974287() {
    self = this;
    clones = new B111974287[1];
    clones[0] = self;
  }

  B111974287 fooX() {
    System.out.println("fooX");
    return self;
  }

  B111974287 fooYY() {
    System.out.println("fooYY");
    return self;
  }

  B111974287 fooZZZ() {
    System.out.println("fooZZZ");
    return self;
  }
}

@RunWith(Parameterized.class)
public class AsterisksTest extends ProguardCompatibilityTestBase {
  private final static List<Class> CLASSES = ImmutableList.of(B111974287.class);
  private final Shrinker shrinker;

  public AsterisksTest(Shrinker shrinker) {
    this.shrinker = shrinker;
  }

  @Parameters(name = "shrinker: {0}")
  public static Collection<Object> data() {
    return ImmutableList.of(Shrinker.PROGUARD6, Shrinker.R8, Shrinker.R8_CF);
  }

  @Test
  public void doubleAsterisksInField() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **." + B111974287.class.getSimpleName() + "{",
        "  ** **;",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(B111974287.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject, not(isRenamed()));
    FieldSubject fieldSubject = classSubject.field(B111974287.class.getTypeName(), "self");
    assertThat(fieldSubject, isPresent());
    assertThat(fieldSubject, not(isRenamed()));
    fieldSubject = classSubject.field(B111974287.class.getTypeName() + "[]", "clones");
    // TODO(b/111974287): Proguard6 kept and renamed the field with array type.
    if (shrinker == Shrinker.PROGUARD6) {
      return;
    }
    assertThat(fieldSubject, not(isPresent()));
  }

  @Test
  public void doubleAsterisksInMethod() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **." + B111974287.class.getSimpleName() + "{",
        "  ** foo**(...);",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(B111974287.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject, not(isRenamed()));
    DexClass clazz = classSubject.getDexClass();
    assertEquals(3, clazz.virtualMethods().length);
    for (DexEncodedMethod encodedMethod : clazz.virtualMethods()) {
      assertTrue(encodedMethod.method.name.toString().startsWith("foo"));
      MethodSubject methodSubject =
          classSubject.method(MethodSignature.fromDexMethod(encodedMethod.method));
      assertThat(methodSubject, isPresent());
      assertThat(methodSubject, not(isRenamed()));
    }
  }

  @Test
  public void tripleAsterisksInField() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **." + B111974287.class.getSimpleName() + "{",
        "  *** ***;",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(B111974287.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject, not(isRenamed()));
    FieldSubject fieldSubject = classSubject.field(B111974287.class.getTypeName(), "self");
    assertThat(fieldSubject, isPresent());
    assertThat(fieldSubject, not(isRenamed()));
    fieldSubject = classSubject.field(B111974287.class.getTypeName() + "[]", "clones");
    assertThat(fieldSubject, isPresent());
    assertThat(fieldSubject, not(isRenamed()));
  }

  @Test
  public void tripleAsterisksInMethod() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **." + B111974287.class.getSimpleName() + "{",
        "  *** foo***(...);",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(B111974287.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject, not(isRenamed()));
    DexClass clazz = classSubject.getDexClass();
    assertEquals(3, clazz.virtualMethods().length);
    for (DexEncodedMethod encodedMethod : clazz.virtualMethods()) {
      assertTrue(encodedMethod.method.name.toString().startsWith("foo"));
      MethodSubject methodSubject =
          classSubject.method(MethodSignature.fromDexMethod(encodedMethod.method));
      assertThat(methodSubject, isPresent());
      assertThat(methodSubject, not(isRenamed()));
    }
  }

  @Test
  public void quadrupleAsterisksInType() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **** {",
        "  **** foo***(...);",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(B111974287.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject, not(isRenamed()));
    DexClass clazz = classSubject.getDexClass();
    assertEquals(3, clazz.virtualMethods().length);
    for (DexEncodedMethod encodedMethod : clazz.virtualMethods()) {
      assertTrue(encodedMethod.method.name.toString().startsWith("foo"));
      MethodSubject methodSubject =
          classSubject.method(MethodSignature.fromDexMethod(encodedMethod.method));
      assertThat(methodSubject, isPresent());
      assertThat(methodSubject, not(isRenamed()));
    }
  }

}
