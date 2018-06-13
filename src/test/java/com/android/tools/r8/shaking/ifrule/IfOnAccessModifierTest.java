// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatabilityTestBase;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfOnAccessModifierTest extends ProguardCompatabilityTestBase {
    private final static List<Class> CLASSES = ImmutableList.of(
        ClassForIf.class, ClassForSubsequent.class,
        MainForAccessModifierTest.class);

  private final Shrinker shrinker;
  private final MethodSignature nonPublicMethod;
  private final MethodSignature publicMethod;

  public IfOnAccessModifierTest(Shrinker shrinker) {
    this.shrinker = shrinker;
    nonPublicMethod = new MethodSignature("nonPublicMethod", "void", ImmutableList.of());
    publicMethod = new MethodSignature("publicMethod", "void", ImmutableList.of());
  }

  @Parameters(name = "shrinker: {0}")
  public static Collection<Object> data() {
    return ImmutableList.of(Shrinker.PROGUARD6, Shrinker.R8);
  }

  @Test
  public void ifOnNonPublic_keepOnPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfNonPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  !public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  public <methods>;",
        "}"
    );
    DexInspector dexInspector = runShrinker(shrinker, CLASSES, config);
    ClassSubject classSubject = dexInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getMethod().accessFlags.isPublic());

    classSubject = dexInspector.clazz(ClassForSubsequent.class);
    if (isR8(shrinker)) {
      // TODO(b/72109068): ClassForIf#nonPublicMethod becomes public, and -if rule is not applied
      // at the 2nd tree shaking.
      assertThat(classSubject, not(isPresent()));
      return;
    }
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));
  }

  @Test
  public void ifOnNonPublic_keepOnNonPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfNonPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  !public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  !public <methods>;",
        "}"
    );
    DexInspector dexInspector = runShrinker(shrinker, CLASSES, config);
    ClassSubject classSubject = dexInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getMethod().accessFlags.isPublic());

    classSubject = dexInspector.clazz(ClassForSubsequent.class);
    if (isR8(shrinker)) {
      // TODO(b/72109068): ClassForIf#nonPublicMethod becomes public, and -if rule is not applied
      // at the 2nd tree shaking.
      assertThat(classSubject, not(isPresent()));
      return;
    }
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertFalse(methodSubject.getMethod().accessFlags.isPublic());
  }

  @Test
  public void ifOnPublic_keepOnPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  public <methods>;",
        "}"
    );
    DexInspector dexInspector = runShrinker(shrinker, CLASSES, config);
    ClassSubject classSubject = dexInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));

    classSubject = dexInspector.clazz(ClassForSubsequent.class);
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));
  }

  @Test
  public void ifOnPublic_keepOnNonPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  !public <methods>;",
        "}"
    );
    DexInspector dexInspector = runShrinker(shrinker, CLASSES, config);
    ClassSubject classSubject = dexInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));

    classSubject = dexInspector.clazz(ClassForSubsequent.class);
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    if (isR8(shrinker)) {
      // TODO(b/72109068): if kept in the 1st tree shaking, should be kept after publicizing.
      assertThat(methodSubject, not(isPresent()));
      return;
    }
    assertThat(methodSubject, isPresent());
    assertFalse(methodSubject.getMethod().accessFlags.isPublic());
  }

}
