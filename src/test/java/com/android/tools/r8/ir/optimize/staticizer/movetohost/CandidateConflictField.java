// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.movetohost;

public class CandidateConflictField {
  public static String field;

  public String foo() {
    synchronized ("") {
      return bar("CandidateConflictMethod::foo()");
    }
  }

  public String bar(String other) {
    synchronized ("") {
      return "CandidateConflictMethod::bar(" + other + ")";
    }
  }
}
