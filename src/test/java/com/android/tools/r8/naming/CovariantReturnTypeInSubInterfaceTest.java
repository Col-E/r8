// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

interface SuperInterface {
  Super foo();
}

interface SubInterface extends SuperInterface {
  @Override
  Sub foo();
}

class Super {
  protected int bar() {
    return 0;
  }
}

class Sub extends Super {
  @Override
  protected int bar() {
    return 1;
  }
}

class SuperImplementer implements SuperInterface {
  @Override
  public Super foo() {
    return new Sub();
  }
}

class SubImplementer extends SuperImplementer implements SubInterface {
  @Override
  public Sub foo() {
    return (Sub) super.foo();
  }
}

class TestMain {
  public static void main(String[] args) {
    SubImplementer subImplementer = new SubImplementer();
    Super sup = subImplementer.foo();
    System.out.print(sup.bar());
  }
}

@RunWith(VmTestRunner.class)
public class CovariantReturnTypeInSubInterfaceTest extends TestBase {

  private void test(boolean overloadAggressively) throws Exception {
    String mainName = TestMain.class.getCanonicalName();
    String aggressive =
        overloadAggressively ? "-overloadaggressively" : "# Not overload aggressively";
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-useuniqueclassmembernames",
        aggressive,
        "-keep class " + mainName + " {",
        "  public void main(...);",
        "}",
        "-keep,allowobfuscation class **.Super* {",
        "  <methods>;",
        "}",
        "-keep,allowobfuscation class **.Sub* {",
        "  <methods>;",
        "}"
    );
    AndroidApp app = readClasses(
        SuperInterface.class,
        SubInterface.class,
        Super.class,
        Sub.class,
        SuperImplementer.class,
        SubImplementer.class,
        TestMain.class
    );
    AndroidApp processedApp =
        compileWithR8(app, String.join(System.lineSeparator(), config), options -> {
          options.enableInlining = false;
        });
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject superInterface = inspector.clazz(SuperInterface.class);
    assertThat(superInterface, isRenamed());
    MethodSubject foo1 = superInterface.method(
        Super.class.getCanonicalName(), "foo", ImmutableList.of());
    assertThat(foo1, isRenamed());
    ClassSubject subInterface = inspector.clazz(SubInterface.class);
    assertThat(subInterface, isRenamed());
    MethodSubject foo2 = subInterface.method(
        Sub.class.getCanonicalName(), "foo", ImmutableList.of());
    assertThat(foo2, isRenamed());
    assertEquals(foo1.getFinalName(), foo2.getFinalName());

    ProcessResult javaResult = ToolHelper.runJava(ToolHelper.getClassPathForTests(), mainName);
    assertEquals(0, javaResult.exitCode);
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    processedApp.writeToZip(outDex, OutputMode.DexIndexed);
    ProcessResult artResult = ToolHelper.runArtNoVerificationErrorsRaw(outDex.toString(), mainName);
    assertEquals(0, artResult.exitCode);
    assertEquals(javaResult.stdout, artResult.stdout);
  }

  @Test
  public void test_notAggressively() throws Exception {
    test(false);
  }

  @Test
  public void test_aggressively() throws Exception {
    test(true);
  }

}
