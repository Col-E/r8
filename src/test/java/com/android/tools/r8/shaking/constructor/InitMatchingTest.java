// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.constructor;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProguardTestCompileResult;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class InitMatchingTestClass {
  // Trivial <clinit>
  static {
  }
  int field;
  public InitMatchingTestClass(int arg) {
    field = arg;
  }
}

@RunWith(Parameterized.class)
public class InitMatchingTest extends TestBase {
  public static final List<String> INIT_NAMES = ImmutableList.of(
      "<random>", "<clinit>", "<init>", "<*init>", "<in*>", "<in*", "*init>", "<1>", "<<1>init>",
      "void <clinit>", "void <init>", "void <*init>", "void <in*>", "void <in*", "void *init>",
      "void <1>", "void <<1>init>", "public void <init>", "public void <*init>",
      "public void <clinit>", "static void <clinit>", "XYZ <clinit>", "private XYZ <init>"
  );

  @Parameters(name = "{0} \"{1}\"")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build(),
        INIT_NAMES);
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public String initName;

  private String createKeepRule() {
    return "-keep class * { " + initName + "(...); }";
  }

  private static final List<String> ALLOWED_INIT_NAMES_PG = ImmutableList.of(
      "<init>", "void <clinit>", "void <init>", "void <1>", "public void <init>",
      "void <*init>", "void <in*>", "void *init>", "public void <*init>",
      "void <<1>init>", "public void <clinit>", "static void <clinit>",
      "XYZ <clinit>", "private XYZ <init>");
  private static final List<String> EFFECTIVE_INIT_NAMES_PG = ImmutableList.of(
      "<init>", "void <init>", "public void <init>",
      "void <*init>", "void <in*>", "void *init>", "public void <*init>");
  private static final List<String> EFFECTIVE_CLINIT_NAMES_PG = ImmutableList.of(
      "void <clinit>", "static void <clinit>", "void <*init>", "void *init>");

  @BeforeClass
  public static void checkPGInitNames() {
    assert INIT_NAMES.containsAll(ALLOWED_INIT_NAMES_PG);
    assert ALLOWED_INIT_NAMES_PG.containsAll(EFFECTIVE_INIT_NAMES_PG);
    assert ALLOWED_INIT_NAMES_PG.containsAll(EFFECTIVE_CLINIT_NAMES_PG);
  }

  @Test
  public void testProguard() throws Exception {
    parameters.assumeProguardTestParameters();
    ProguardTestCompileResult result;
    try {
      result =
          testForProguard(ProguardVersion.V6_0_1)
              .addProgramClasses(InitMatchingTestClass.class)
              .addKeepRules(createKeepRule())
              .compile();
      if (!ALLOWED_INIT_NAMES_PG.contains(initName)) {
        fail("Expect to fail");
      }
    } catch (CompilationFailedException e) {
      assertFalse(ALLOWED_INIT_NAMES_PG.contains(initName));
      if (initName.equals("void <in*")) {
        assertThat(e.getMessage(), containsString("Missing closing angular bracket"));
      } else {
        assertThat(e.getMessage(),
            containsString("Expecting type and name instead of just '" + initName + "'"));
      }
      return;
    }
    result.inspect(this::inspectProguard);
  }

  private void inspectProguard(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(InitMatchingTestClass.class);
    assertThat(classSubject, isPresent());
    MethodSubject init = classSubject.init(ImmutableList.of("int"));
    if (EFFECTIVE_INIT_NAMES_PG.contains(initName)) {
      assertThat(init, isPresent());
    } else {
      assertThat(init, not(isPresent()));
    }
    MethodSubject clinit = classSubject.clinit();
    if (EFFECTIVE_CLINIT_NAMES_PG.contains(initName)) {
      assertThat(clinit, isPresent());
    } else {
      assertThat(clinit, not(isPresent()));
    }
  }

  // "[[access-flag]* void] <[cl]init>" is the only valid format. Plus legitimate back-references.
  public static final List<String> ALLOWED_INIT_NAMES = ImmutableList.of(
      "<clinit>", "<init>", "void <clinit>", "void <init>", "void <1>",
      "public void <init>", "public void <clinit>", "static void <clinit>");
  private static final List<String> EFFECTIVE_INIT_NAMES = ImmutableList.of(
      "<init>", "void <init>", "public void <init>");
  private static final List<String> EFFECTIVE_CLINIT_NAMES = ImmutableList.of(
      "<clinit>", "void <clinit>", "static void <clinit>");

  @BeforeClass
  public static void checkR8InitNames() {
    assert INIT_NAMES.containsAll(ALLOWED_INIT_NAMES);
    assert ALLOWED_INIT_NAMES.containsAll(EFFECTIVE_INIT_NAMES);
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult result;
    try {
      result =
          testForR8(parameters.getBackend())
              .addProgramClasses(InitMatchingTestClass.class)
              .addKeepRules(createKeepRule())
              .setMinApi(parameters)
              .compile();
      if (!ALLOWED_INIT_NAMES.contains(initName)) {
        fail("Expect to fail");
      }
    } catch (CompilationFailedException e) {
      assertFalse(ALLOWED_INIT_NAMES.contains(initName));
      if (initName.contains("XYZ")) {
        assertThat(e.getCause().getMessage(),
            containsString("Expected [access-flag]* void "));
        return;
      }
      assertThat(e.getCause().getMessage(),
          containsString("Unexpected character '" + (initName.contains("<") ? "<" : ">") + "'"));
      assertThat(e.getCause().getMessage(),
          containsString("only allowed in the method name '<init>'"));
      return;
    }
    result
        .assertNoMessages()
        .inspect(this::inspectR8);
  }

  private void inspectR8(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(InitMatchingTestClass.class);
    assertThat(classSubject, isPresent());
    MethodSubject init = classSubject.init(ImmutableList.of("int"));
    if (EFFECTIVE_INIT_NAMES.contains(initName)) {
      assertThat(init, isPresent());
    } else {
      assertThat(init, not(isPresent()));
    }
    // We only keep class initializers in debug mode.
    MethodSubject clinit = classSubject.clinit();
    assertThat(clinit, not(isPresent()));
  }
}
