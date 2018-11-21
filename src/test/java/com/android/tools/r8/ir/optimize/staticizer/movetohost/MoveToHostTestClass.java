// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.movetohost;

import com.android.tools.r8.NeverInline;

public class MoveToHostTestClass {
  private static int ID = 0;

  private static String next() {
    return Integer.toString(ID++);
  }

  public static void main(String[] args) {
    MoveToHostTestClass test = new MoveToHostTestClass();
    test.testOk();
    test.testOkSideEffects();
    test.testConflictMethod();
    test.testConflictField();
  }

  @NeverInline
  private void testOk() {
    System.out.println(HostOk.INSTANCE.foo());
    System.out.println(HostOk.INSTANCE.bar(next()));
    HostOk.INSTANCE.blah(next());
  }

  @NeverInline
  private void testOkSideEffects() {
    System.out.println(HostOkSideEffects.INSTANCE.foo());
    System.out.println(HostOkSideEffects.INSTANCE.bar(next()));
  }

  @NeverInline
  private void testConflictMethod() {
    System.out.println(new HostConflictMethod().bar(next()));
    System.out.println(HostConflictMethod.INSTANCE.foo());
    System.out.println(HostConflictMethod.INSTANCE.bar(next()));
  }

  @NeverInline
  private void testConflictField() {
    System.out.println(new HostConflictField().field);
    System.out.println(CandidateConflictField.field);
    System.out.println(HostConflictField.INSTANCE.foo());
    System.out.println(HostConflictField.INSTANCE.bar(next()));
  }
}

