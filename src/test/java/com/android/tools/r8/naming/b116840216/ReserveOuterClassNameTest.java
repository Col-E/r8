// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b116840216;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoStaticClassMerging;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@NoStaticClassMerging
class Outer {

  @NoStaticClassMerging
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
  public static Backend[] data() {
    return ToolHelper.getBackends();
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
            keepOuterName ? "-keepnames class " + Outer.class.getCanonicalName() : "",
            noStaticClassMergingRule()),
        Origin.unknown());

    ToolHelper.allowTestProguardOptions(builder);
    AndroidApp processedApp = ToolHelper.runR8(builder.build());

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresentAndNotRenamed());
    MethodSubject mainMethod = mainSubject.mainMethod();
    assertThat(mainMethod, isPresentAndNotRenamed());

    ClassSubject outer = inspector.clazz(Outer.class);
    assertThat(outer, isPresentAndNotRenamed());
    MethodSubject bar = outer.method("void", "bar", ImmutableList.of());
    assertThat(bar, isPresentAndRenamed());

    ClassSubject inner = inspector.clazz(Outer.Inner.class);
    assertThat(inner, isPresentAndNotRenamed());
    MethodSubject foo = inner.method("void", "foo", ImmutableList.of());
    assertThat(foo, isPresentAndRenamed());
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
