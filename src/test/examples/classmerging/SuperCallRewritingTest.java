// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class SuperCallRewritingTest {
  public static void main(String[] args) {
    System.out.println("Calling referencedMethod on SubClassThatReferencesSuperMethod");
    System.out.println(new SubClassThatReferencesSuperMethod().referencedMethod());
  }
}
