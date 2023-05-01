// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.jumbostring;

class JumboString {
  public static void main(String[] args) {
    // Make sure this string sorts after the field names and string values in the StringPoolX.java
    // files to ensure this is a jumbo string.
    System.out.println("zzzz - jumbo string");
  }
}
