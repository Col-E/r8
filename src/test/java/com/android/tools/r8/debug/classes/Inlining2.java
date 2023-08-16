// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug.classes;

//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//

public class Inlining2 {
  public static void inlineThisFromAnotherFile() {
    System.out.println("inlineThisFromAnotherFile");
  }

  public static void differentFileMultilevelInliningLevel1() {
    Inlining3.differentFileMultilevelInliningLevel2();
  }
}
