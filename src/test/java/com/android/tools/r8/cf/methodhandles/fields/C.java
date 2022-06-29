// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles.fields;

// This is a top-level class.
// The use of handles will check generics on C and fail if it cannot find the outer class.
public class C {

  public int vi;
  public static int si;
}
