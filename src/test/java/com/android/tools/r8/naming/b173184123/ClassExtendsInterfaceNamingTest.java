// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b173184123;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/173184123.
@RunWith(Parameterized.class)
public class ClassExtendsInterfaceNamingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassExtendsInterfaceNamingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Path classFiles = temp.newFile("classes.jar").toPath();
    ZipBuilder.builder(classFiles)
        .addFilesRelative(
            ToolHelper.getClassPathForTests(),
            ToolHelper.getClassFileForTestClass(Interface.class),
            ToolHelper.getClassFileForTestClass(Main.class))
        .addBytes(
            DescriptorUtils.getPathFromJavaType(ConcreteClass.class),
            transformer(ConcreteClass.class)
                .setSuper(DescriptorUtils.javaTypeToDescriptor(Interface.class.getTypeName()))
                .transform())
        .build();
    testForExternalR8(parameters.getBackend())
        .addProgramFiles(classFiles)
        .addTestingAnnotationsAsProgramClasses()
        .enableAssertions(false)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Interface.class)
        .addKeepRules("-neverclassinline @com.android.tools.r8.NeverClassInline class *")
        .addKeepRules("-neverinline class * { @**.NeverInline *; }")
        .allowTestProguardOptions(true)
        .compile()
        .assertStderrThatMatches(
            containsString(
                "Class "
                    + ConcreteClass.class.getTypeName()
                    + " extends "
                    + Interface.class.getTypeName()
                    + " which is an interface"))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  public interface Interface {
    void foo();
  }

  @NeverClassInline
  public static class ConcreteClass /* extends InterfaceSub */ implements Interface {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ConcreteClass().foo();
    }
  }
}
