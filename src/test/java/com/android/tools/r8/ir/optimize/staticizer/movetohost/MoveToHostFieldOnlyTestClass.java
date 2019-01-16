// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.staticizer.movetohost;

import com.android.tools.r8.NeverInline;

public class MoveToHostFieldOnlyTestClass {

  public static void main(String[] args) {
    MoveToHostFieldOnlyTestClass test = new MoveToHostFieldOnlyTestClass();
    test.testOk_fieldOnly();
  }

  @NeverInline
  private void testOk_fieldOnly() {
    // Any instance method call whose target holder is not the candidate will invalidate candidacy,
    // for example, toString() without overriding, getClass(), etc.
    // Note that having instance methods in the candidate class guarantees that method mappings will
    // exist when field mappings do so.
    // Any other uses other than invoke-virtual or invoke-direct (to either <init> or private) are
    // not allowed, e.g., System.out.println(INSTANCE), null check, or static-put to somewhere else.
    // Therefore, it's merely dead code, and thus it has not been harmful to forget to create a
    // staticizer lense when there is no method mapping (for instance methods to staticized ones)
    // while there are field mappings as shown in this example.
    Object x = HostOkFieldOnly.INSTANCE;
  }
}

