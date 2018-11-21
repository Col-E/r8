// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b114554345;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B114554345 extends TestBase {

  private final Backend backend;

  @Parameters(name = "backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public B114554345(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    AndroidApp input =
        AndroidApp.builder()
            .addProgramFiles(
                ToolHelper.getClassFilesForTestDirectory(
                    ToolHelper.getPackageDirectoryForTestPackage(this.getClass().getPackage()),
                    path -> !path.toString().contains("B114554345")))
            .build();
    AndroidApp output =
        compileWithR8(
            input,
            keepMainProguardConfiguration(TestDriver.class),
            options -> options.enableInlining = false,
            backend);
    String mainClass = TestDriver.class.getName();
    assertEquals(runOnJava(TestDriver.class), runOnVM(output, mainClass, backend));
  }
}

// Interface and two implementations.

interface Interface {
  Interface method();
}

class InterfaceImpl implements Interface {

  @Override
  public InterfaceImpl method() {
    System.out.println("In InterfaceImpl.method()");
    return this;
  }
}

class OtherInterfaceImpl extends InterfaceImpl {

  @Override
  public OtherInterfaceImpl method() {
    System.out.println("In OtherInterfaceImpl.method()");
    return this;
  }
}

// Sub-interface and three implementations.

interface SubInterface extends Interface {
  SubInterface method();
}

class SubInterfaceImpl implements SubInterface {

  @Override
  public SubInterfaceImpl method() {
    System.out.println("In SubInterfaceImpl.method()");
    return this;
  }
}

class OtherSubInterfaceImpl implements SubInterface {

  @Override
  public OtherSubInterfaceImpl method() {
    System.out.println("In OtherSubInterfaceImpl.method()");
    return this;
  }
}

class YetAnotherSubInterfaceImpl extends InterfaceImpl implements SubInterface {

  @Override
  public YetAnotherSubInterfaceImpl method() {
    System.out.println("In YetAnotherSubInterfaceImpl.method()");
    return this;
  }
}

class TestDriver {

  public static void main(String[] args) {
    foo(new InterfaceImpl());
    foo(new OtherInterfaceImpl());
    foo(new SubInterfaceImpl());
    foo(new YetAnotherSubInterfaceImpl());
    bar(new SubInterfaceImpl());
    bar(new OtherSubInterfaceImpl());
    bar(new YetAnotherSubInterfaceImpl());
  }

  private static void foo(Interface obj) {
    obj.method();
  }

  private static void bar(SubInterface obj) {
    obj.method();
  }
}
