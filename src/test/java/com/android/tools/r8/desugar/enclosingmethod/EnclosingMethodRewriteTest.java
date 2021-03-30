// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.enclosingmethod;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.DEFAULT_METHOD_PREFIX;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
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

  private final TestParameters parameters;
  private final boolean enableMinification;

  @Parameterized.Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
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
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  @Test
  public void testDesugarAndProguard() throws Exception {
    assumeTrue("Only run on CF runtimes", parameters.isCfRuntime());
    Path desugared = temp.newFile("desugared.jar").toPath().toAbsolutePath();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(A.class)
            .addProgramClasses(C.class, MAIN)
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
    testForProguard()
        .addProgramFiles(desugared)
        .addKeepMainRule(MAIN)
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
        .run(parameters.getRuntime(), MAIN)
        .assertSuccess();
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(A.class)
            .addProgramClasses(C.class, MAIN)
            .addKeepMainRule(MAIN)
            .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
            .setMinApi(parameters.getApiLevel());
    if (enableMinification) {
      builder.addKeepAllClassesRuleWithAllowObfuscation();
    } else {
      builder.noTreeShaking().noMinification();
    }
    builder
        .compile()
        .inspect(
            inspect -> {
              ClassSubject cImplSubject = inspect.clazz(A.class.getTypeName() + "$1");
              assertThat(cImplSubject, isPresent());

              ClassSubject enclosingClassSubject =
                  inspect.clazz(
                      parameters.canUseDefaultAndStaticInterfaceMethods()
                          ? A.class.getTypeName()
                          : A.class.getTypeName() + COMPANION_CLASS_NAME_SUFFIX);
              assertThat(enclosingClassSubject, isPresent());
              assertEquals(
                  enclosingClassSubject.getDexProgramClass().getType(),
                  cImplSubject
                      .getDexProgramClass()
                      .getEnclosingMethodAttribute()
                      .getEnclosingMethod()
                      .getHolderType());
            })
        .run(parameters.getRuntime(), MAIN)
        .apply(
            result -> result.assertSuccessWithOutputLines(getExpectedOutput(result.inspector())));
  }

  private List<String> getExpectedOutput() {
    return ImmutableList.of(
        "interface " + A.class.getTypeName(),
        "public default int " + A.class.getTypeName() + ".def()",
        "42");
  }

  private List<String> getExpectedOutput(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    MethodSubject defMethodSubject = aClassSubject.uniqueMethodWithName("def");
    assertThat(defMethodSubject, isPresent());

    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      String modifiers =
          parameters.isCfRuntime()
                  || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V8_1_0)
              ? "public default"
              : "public";
      return ImmutableList.of(
          "interface " + aClassSubject.getFinalName(),
          modifiers
              + " int "
              + aClassSubject.getFinalName()
              + "."
              + defMethodSubject.getFinalName()
              + "()",
          "42");
    }

    ClassSubject aCompanionClassSubject =
        inspector.clazz(A.class.getTypeName() + COMPANION_CLASS_NAME_SUFFIX);
    assertThat(aCompanionClassSubject, isPresent());

    String methodNamePrefix = enableMinification ? "" : DEFAULT_METHOD_PREFIX;
    return ImmutableList.of(
        "class " + aCompanionClassSubject.getFinalName(),
        "public static int "
            + aCompanionClassSubject.getFinalName()
            + "."
            + methodNamePrefix
            + defMethodSubject.getFinalName()
            + "("
            + aClassSubject.getFinalName()
            + ")",
        "42");
  }
}
