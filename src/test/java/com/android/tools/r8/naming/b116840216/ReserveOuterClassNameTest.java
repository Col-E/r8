// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b116840216;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class Outer {

  static class Inner {
    @NeverInline
    static void foo() {
      System.out.println("Inner.foo");
    }
  }

  @NeverInline
  static void bar() {
    System.out.println("Outer.bar");
  }
}

class TestMain {
  public static void main(String[] args) {
    Outer.Inner.foo();
    Outer.bar();
  }
}

@RunWith(Parameterized.class)
public class ReserveOuterClassNameTest extends TestBase {
  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public ReserveOuterClassNameTest(Backend backend){
    this.backend = backend;
  }

  private void runTest(boolean keepOuterName) throws Exception {
    Class mainClass = TestMain.class;
    R8Command.Builder builder = new CompatProguardCommandBuilder(true);
    builder.addProgramFiles(
        ToolHelper.getClassFileForTestClass(mainClass),
        ToolHelper.getClassFileForTestClass(Outer.class),
        ToolHelper.getClassFileForTestClass(Outer.Inner.class),
        ToolHelper.getClassFileForTestClass(NeverInline.class));
    builder.setProgramConsumer(emptyConsumer(backend));
    builder.addLibraryFiles(runtimeJar(backend));
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-printmapping",
            "-keepattributes EnclosingMethod,InnerClasses,Signature",
            keepMainProguardConfigurationWithInliningAnnotation(mainClass),
            // Note that reproducing b/116840216 relies on the order of following rules that cause
            // the visiting of classes during class minification to be Outer$Inner before Outer.
            "-keepnames class " + Outer.class.getCanonicalName() + "$Inner",
            keepOuterName ? "-keepnames class " + Outer.class.getCanonicalName() : ""
        ),
        Origin.unknown());

    ToolHelper.allowTestProguardOptions(builder);
    AndroidApp processedApp = ToolHelper.runR8(builder.build());

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());
    assertThat(mainSubject, not(isRenamed()));
    MethodSubject mainMethod = mainSubject.mainMethod();
    assertThat(mainMethod, isPresent());
    assertThat(mainMethod, not(isRenamed()));

    ClassSubject outer = inspector.clazz(Outer.class);
    assertThat(outer, isPresent());
    assertThat(outer, not(isRenamed()));
    MethodSubject bar = outer.method("void", "bar", ImmutableList.of());
    assertThat(bar, isPresent());
    assertThat(bar, isRenamed());

    ClassSubject inner = inspector.clazz(Outer.Inner.class);
    assertThat(inner, isPresent());
    assertThat(inner, not(isRenamed()));
    MethodSubject foo = inner.method("void", "foo", ImmutableList.of());
    assertThat(foo, isPresent());
    assertThat(foo, isRenamed());
  }


  @Test
  public void test_keepOuterName() throws Exception {
    runTest(true);
  }

  @Test
  public void test_keepInnerNameOnly() throws Exception {
    runTest(false);
  }
}
