// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.modifiers;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AccessFlagsCombinationsTest extends TestBase {

  enum Expect {
    STATICS_ONLY,
    NON_STATICS_ONLY,
    BOTH_STATICS_AND_NON_STATICS
  }

  private final TestParameters parameters;
  private static final int PPP =
      Constants.ACC_PUBLIC | Constants.ACC_PROTECTED | Constants.ACC_PRIVATE;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public AccessFlagsCombinationsTest(TestParameters parameters) {
    assertEquals(7, PPP);
    this.parameters = parameters;
  }

  private void checkKeptMembers(CodeInspector inspector, Integer flags, Expect expect) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);

    boolean includeStatic =
        expect == Expect.STATICS_ONLY || expect == Expect.BOTH_STATICS_AND_NON_STATICS;
    boolean includeNonStatic =
        expect == Expect.NON_STATICS_ONLY || expect == Expect.BOTH_STATICS_AND_NON_STATICS;


    boolean includePublic = (flags & PPP) == 0 || (flags & Constants.ACC_PUBLIC) != 0;
    boolean includeProtected = (flags & PPP) == 0 || (flags & Constants.ACC_PROTECTED) != 0;
    boolean includePrivate = (flags & PPP) == 0 || (flags & Constants.ACC_PRIVATE) != 0;
    boolean includePackagePrivate = (flags & PPP) == 0;

    assertEquals(
        includePublic && includeNonStatic,
        classSubject.uniqueMethodWithName("publicMethod").isPresent());
    assertEquals(
        includeProtected && includeNonStatic,
        classSubject.uniqueMethodWithName("protectedMethod").isPresent());
    assertEquals(
        includePrivate && includeNonStatic,
        classSubject.uniqueMethodWithName("privateMethod").isPresent());
    assertEquals(
        includePackagePrivate && includeNonStatic,
        classSubject.uniqueMethodWithName("packagePrivateMethod").isPresent());
    assertEquals(
        includePublic && includeStatic,
        classSubject.uniqueMethodWithName("publicStaticMethod").isPresent());
    assertEquals(
        includeProtected && includeStatic,
        classSubject.uniqueMethodWithName("protectedStaticMethod").isPresent());
    assertEquals(
        includePrivate && includeStatic,
        classSubject.uniqueMethodWithName("privateStaticMethod").isPresent());
    assertEquals(
        includePackagePrivate && includeStatic,
        classSubject.uniqueMethodWithName("packagePrivateStaticMethod").isPresent());
    assertEquals(
        includePublic && includeNonStatic,
        classSubject.uniqueFieldWithName("publicField").isPresent());
    assertEquals(
        includeProtected && includeNonStatic,
        classSubject.uniqueFieldWithName("protectedField").isPresent());
    assertEquals(
        includePrivate && includeNonStatic,
        classSubject.uniqueFieldWithName("privateField").isPresent());
    assertEquals(
        includePackagePrivate && includeNonStatic,
        classSubject.uniqueFieldWithName("packagePrivateField").isPresent());
    assertEquals(
        includePublic && includeStatic,
        classSubject.uniqueFieldWithName("publicStaticField").isPresent());
    assertEquals(
        includeProtected && includeStatic,
        classSubject.uniqueFieldWithName("protectedStaticField").isPresent());
    assertEquals(
        includePrivate && includeStatic,
        classSubject.uniqueFieldWithName("privateStaticField").isPresent());
    assertEquals(
        includePackagePrivate && includeStatic,
        classSubject.uniqueFieldWithName("packagePrivateStaticField").isPresent());
  }

  public void runTest(
      List<String> keepRules, ThrowingConsumer<CodeInspector, RuntimeException> inspector)
      throws Exception {
    String expectedOutput = StringUtils.lines("Hello, world");
    testForR8(parameters.getBackend())
        .addInnerClasses(AccessFlagsCombinationsTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(keepRules)
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(inspector)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private String flagString(int flags) {
    return new StringBuilder()
        .append((flags & Constants.ACC_PUBLIC) != 0 ? " public" : "")
        .append((flags & Constants.ACC_PROTECTED) != 0 ? " protected" : "")
        .append((flags & Constants.ACC_PRIVATE) != 0 ? " private" : "")
        .toString();
  }

  @Test
  public void testImplicitBothStaticAndNonStaticMembers() throws Exception {
    for (int flags = 0; flags <= PPP; flags++) {
      Integer finalFlags = flags;
      runTest(
          ImmutableList.of("-keep class **.*TestClass {" + flagString(flags) + " *; }"),
          inspector ->
              checkKeptMembers(inspector, finalFlags, Expect.BOTH_STATICS_AND_NON_STATICS));
    }
  }

  @Test
  public void testExplicitNotStaticMembers() throws Exception {
    for (int flags = 0; flags <= PPP; flags++) {
      Integer finalFlags = flags;
      runTest(
          ImmutableList.of("-keep class **.*TestClass { !static" + flagString(flags) + " *; }"),
          inspector -> checkKeptMembers(inspector, finalFlags, Expect.NON_STATICS_ONLY));
    }
  }

  @Test
  public void testStaticMembers() throws Exception {
    for (int flags = 0; flags <= PPP; flags++) {
      Integer finalFlags = flags | Constants.ACC_STATIC;
      runTest(
          ImmutableList.of("-keep class **.*TestClass { static" + flagString(flags) + " *; }"),
          inspector -> checkKeptMembers(inspector, finalFlags, Expect.STATICS_ONLY));
    }
  }

  static class TestClass {
    public static int publicStaticField;
    protected static int protectedStaticField;
    private static int privateStaticField;
    static int packagePrivateStaticField;
    public int publicField;
    protected int protectedField;
    private int privateField;
    int packagePrivateField;

    public static void publicStaticMethod() {

    }

    protected static void protectedStaticMethod() {

    }

    private static void privateStaticMethod() {

    }

    static void packagePrivateStaticMethod() {

    }

    public void publicMethod() {

    }

    protected void protectedMethod() {

    }

    private void privateMethod() {

    }

    void packagePrivateMethod() {

    }

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}