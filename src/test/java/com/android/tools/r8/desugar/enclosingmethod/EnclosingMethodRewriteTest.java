// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.enclosingmethod;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

interface A {
  default int def() {
    return new C() {
      @Override
      int number() {
        Class<?> clazz = getClass();
        System.out.println(clazz.getEnclosingClass());
        System.out.println(clazz.getEnclosingMethod());
        return 42;
      }
    }.getNumber();
  }
}

abstract class C {
  abstract int number();

  public int getNumber() {
    return number();
  }
}

class TestClass implements A {
  public static void main(String[] args) throws NoClassDefFoundError, ClassNotFoundException {
    System.out.println(new TestClass().def());
  }
}

@RunWith(Parameterized.class)
public class EnclosingMethodRewriteTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "interface " + A.class.getName(),
      "public default int " + A.class.getName() + ".def()",
      "42"
  );

  private final TestParameters parameters;
  private final boolean enableMinification;

  @Parameterized.Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        BooleanUtils.values());
  }

  public EnclosingMethodRewriteTest(TestParameters parameters, boolean enableMinification) {
    this.parameters = parameters;
    this.enableMinification = enableMinification;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testDesugarAndProguard() throws Exception {
    assumeTrue("Only run on CF runtimes", parameters.isCfRuntime());
    Path desugared = temp.newFile("desugared.jar").toPath().toAbsolutePath();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClasses(A.class, C.class, MAIN)
            .addKeepMainRule(MAIN)
            .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
            .setProgramConsumer(new ArchiveConsumer(desugared))
            .setMinApi(parameters.getApiLevel());
    if (enableMinification) {
      builder.addKeepAllClassesRuleWithAllowObfuscation();
    } else {
      builder.addKeepAllClassesRule();
    }
    builder.compile().assertNoMessages();
    try {
      testForProguard()
          .addProgramFiles(desugared)
          .addKeepMainRule(MAIN)
          .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
          .run(parameters.getRuntime(), MAIN)
          .assertSuccessWithOutput(JAVA_OUTPUT);
    } catch (CompilationFailedException e) {
      // TODO(b/70293332)
      assertThat(e.getMessage(), containsString("unresolved references"));
      assertThat(e.getMessage(), containsString(A.class.getName() + "$1"));
    }
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClasses(A.class, C.class, MAIN)
            .addKeepMainRule(MAIN)
            .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
            .setMinApi(parameters.getApiLevel());
    if (enableMinification) {
      builder.addKeepAllClassesRuleWithAllowObfuscation();
    } else {
      builder.addKeepAllClassesRule();
    }
    String errorType = "ClassNotFoundException";
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      errorType = "NoClassDefFoundError";
    }
    builder
        .run(parameters.getRuntime(), MAIN)
        // TODO(b/70293332)
        .assertFailureWithErrorThatMatches(containsString(errorType));
  }
}
