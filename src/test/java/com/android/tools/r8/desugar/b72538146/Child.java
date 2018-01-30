// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.b72538146;

import com.android.tools.r8.desugar.b72538146.Parent.Inner1;
import com.android.tools.r8.desugar.b72538146.Parent.Inner2;
import com.android.tools.r8.desugar.b72538146.Parent.Inner4;
import java.util.function.Supplier;

public class Child {
  /**
   * See what happens when app/runtime code both use and call a method reference.
   */
  public void calling_duplicate_method_reference() {
    Supplier<Inner1> supplier = Inner1::new;
    supplier.get();
  }

  /**
   * See what happens when app/runtime code both use a method reference but neither calls it.
   */
  public void using_duplicate_method_reference() {
    Supplier<Inner2> supplier = Inner2::new;
  }

  /**
   * See what happens when app code uses and calls a method reference to runtime code.
   */
  public void calling_method_reference() {
    Supplier<Inner4> supplier = Inner4::new;
    supplier.get();
  }

  public static void main(String[] args) {
    Child instance = new Child();
    instance.calling_duplicate_method_reference();
    instance.using_duplicate_method_reference();
    instance.calling_method_reference();
    System.out.print("SUCCESS");
  }
}
