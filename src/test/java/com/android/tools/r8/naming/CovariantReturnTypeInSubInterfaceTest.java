// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

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
    return new Super();
  }
}

class SubImplementer implements SubInterface {
  @Override
  public Sub foo() {
    return new Sub();
  }
}

class TestMain {
  public static void main(String[] args) {
    SubImplementer subImplementer = new SubImplementer();
    Super sup = subImplementer.foo();
    System.out.println(sup.bar());
  }
}

public class CovariantReturnTypeInSubInterfaceTest extends TestBase {

  @Ignore("b/112185748")
  @Test
  public void test() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-useuniqueclassmembernames",
        "-keep class " + TestMain.class.getCanonicalName() + " {",
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
    AndroidApp processedApp = compileWithR8(app, String.join(System.lineSeparator(), config));
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
  }

}
