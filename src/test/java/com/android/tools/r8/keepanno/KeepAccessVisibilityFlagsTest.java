// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.MethodAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepAccessVisibilityFlagsTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines(
          // Field targets.
          "packagePrivateField",
          "protectedField",
          "publicField",
          // Method targets.
          "privateMethod",
          "protectedMethod",
          "publicMethod",
          // Member targets.
          "packagePrivateField",
          "privateField",
          "privateMethod",
          "packagePrivateMethod");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepAccessVisibilityFlagsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(
        TestClass.class,
        A.class,
        FieldRuleTarget.class,
        MethodRuleTarget.class,
        MemberRuleTarget.class);
  }

  private static List<String> allMembers =
      ImmutableList.of(
          "publicField",
          "protectedField",
          "packagePrivateField",
          "privateField",
          "publicMethod",
          "protectedMethod",
          "packagePrivateMethod",
          "privateMethod");

  private void checkOutput(CodeInspector inspector) {
    assertPresent(
        inspector, FieldRuleTarget.class, "publicField", "protectedField", "packagePrivateField");
    assertPresent(
        inspector, MethodRuleTarget.class, "publicMethod", "protectedMethod", "privateMethod");
    assertPresent(
        inspector,
        MemberRuleTarget.class,
        "packagePrivateField",
        "privateField",
        "packagePrivateMethod",
        "privateMethod");
  }

  private void assertPresent(CodeInspector inspector, Class<?> clazz, String... members) {
    ClassSubject subject = inspector.clazz(clazz);
    assertThat(subject, isPresent());
    Set<String> expectedPresent = ImmutableSet.copyOf(members);
    for (String member : allMembers) {
      if (member.endsWith("Field")) {
        assertThat(
            subject.uniqueFieldWithOriginalName(member),
            expectedPresent.contains(member) ? isPresent() : isAbsent());
      } else {
        assertThat(
            subject.uniqueMethodWithOriginalName(member),
            expectedPresent.contains(member) ? isPresent() : isAbsent());
      }
    }
  }

  abstract static class FieldRuleTarget {
    // Fields are ordered by name and all are in the DEX instance pool.
    String packagePrivateField = "package-private";
    private String privateField = "private";
    protected String protectedField = "protected";
    public String publicField = "public";
    // Private methods are in direct pool and printed before virtuals.
    private void privateMethod() {}
    // The virtual methods are ordered by name.
    void packagePrivateMethod() {}

    protected void protectedMethod() {}

    public void publicMethod() {}
  }

  abstract static class MethodRuleTarget {
    // Fields are ordered by name and all are in the DEX instance pool.
    String packagePrivateField = "package-private";
    private String privateField = "private";
    protected String protectedField = "protected";
    public String publicField = "public";
    // Private methods are in direct pool and printed before virtuals.
    private void privateMethod() {}
    // The virtual methods are ordered by name.
    void packagePrivateMethod() {}

    protected void protectedMethod() {}

    public void publicMethod() {}
  }

  abstract static class MemberRuleTarget {
    // Fields are ordered by name and all are in the DEX instance pool.
    String packagePrivateField = "package-private";
    private String privateField = "private";
    protected String protectedField = "protected";
    public String publicField = "public";
    // Private methods are in direct pool and printed before virtuals.
    private void privateMethod() {}
    // The virtual methods are ordered by name.
    void packagePrivateMethod() {}

    protected void protectedMethod() {}

    public void publicMethod() {}
  }

  static class A {

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          classConstant = FieldRuleTarget.class,
          fieldAccess = {FieldAccessFlags.NON_PRIVATE}),
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          classConstant = MethodRuleTarget.class,
          methodAccess = {MethodAccessFlags.NON_PACKAGE_PRIVATE}),
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          classConstant = MemberRuleTarget.class,
          memberAccess = {MemberAccessFlags.PACKAGE_PRIVATE, MemberAccessFlags.PRIVATE}),
    })
    void foo() throws Exception {
      // Print all non-private fields.
      for (Field field : FieldRuleTarget.class.getDeclaredFields()) {
        int mod = field.getModifiers();
        if (!Modifier.isPrivate(mod)) {
          System.out.println(field.getName());
        }
      }
      // Print all non-package-private fields.
      for (Method method : MethodRuleTarget.class.getDeclaredMethods()) {
        int mod = method.getModifiers();
        if (Modifier.isPublic(mod) || Modifier.isProtected(mod) || Modifier.isPrivate(mod)) {
          System.out.println(method.getName());
        }
      }
      // Print all private and package-private members.
      for (Field field : MemberRuleTarget.class.getDeclaredFields()) {
        int mod = field.getModifiers();
        if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) {
          System.out.println(field.getName());
        }
      }
      for (Method method : MemberRuleTarget.class.getDeclaredMethods()) {
        int mod = method.getModifiers();
        if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) {
          System.out.println(method.getName());
        }
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
