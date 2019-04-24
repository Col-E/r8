// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.constructor;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProguardTestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
      "<clinit>", "<init>", "<*init>", "<in*>", "<in*", "*init>",
      "void <clinit>", "void <init>", "void <*init>", "void <in*>", "void <in*", "void *init>",
      "public void <init>", "public void <*init>"
  );

  @Parameterized.Parameters(name = "{0} \"{1}\"")
  public static Collection<Object[]> data() {
    return buildParameters(ToolHelper.getBackends(), INIT_NAMES);
  }

  private final Backend backend;
  private final String initName;

  public InitMatchingTest(Backend backend, String initName) {
    this.backend = backend;
    this.initName = initName;
  }

  private String createKeepRule() {
    return "-keep class * { " + initName + "(...); }";
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(backend == Backend.CF);
    ProguardTestCompileResult result;
    try {
      result =
        testForProguard()
            .addProgramClasses(InitMatchingTestClass.class)
            .addKeepRules(createKeepRule())
            .compile();
    } catch (CompilationFailedException e) {
      assertNotEquals("<init>", initName);
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
    if (initName.equals("void <clinit>")) {
      assertThat(init, not(isPresent()));
    } else {
      assertThat(init, isPresent());
    }
    MethodSubject clinit = classSubject.clinit();
    if (initName.equals("void <clinit>")
        || initName.equals("void <*init>")
        || initName.equals("void *init>")) {
      assertThat(clinit, isPresent());
    } else {
      assertThat(clinit, not(isPresent()));
    }
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult result;
    try {
      result =
        testForR8(backend)
            .addProgramClasses(InitMatchingTestClass.class)
            .addKeepRules(createKeepRule())
            .compile();
    } catch (CompilationFailedException e) {
      assertNotEquals("<init>", initName);
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
    assertThat(init, isPresent());
    MethodSubject clinit = classSubject.clinit();
    assertThat(clinit, not(isPresent()));
  }

}
