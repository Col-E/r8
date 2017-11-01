// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class Inlining1 {
  private static void inlineThisFromSameFile() {
    System.out.println("inlineThisFromSameFile");
  }

  private static void sameFileMultilevelInliningLevel2() {
    System.out.println("sameFileMultilevelInliningLevel2");
  }

  private static void sameFileMultilevelInliningLevel1() {
    sameFileMultilevelInliningLevel2();
  }

  public static void main(String[] args) {
    inlineThisFromSameFile();
    inlineThisFromSameFile();
    Inlining2.inlineThisFromAnotherFile();
    Inlining2.inlineThisFromAnotherFile();
    sameFileMultilevelInliningLevel1();
    sameFileMultilevelInliningLevel1();
    Inlining2.differentFileMultilevelInliningLevel1();
    Inlining2.differentFileMultilevelInliningLevel1();
  }
}
