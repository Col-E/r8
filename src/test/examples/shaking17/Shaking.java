// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package shaking17;

public class Shaking {

  public static void main(String[] args) {
    Subclass subclass = new Subclass();
    callTheMethod(subclass);
  }

  private static void callTheMethod(AbstractProgramClass abstractProgramClass) {
    System.out.print(abstractProgramClass.abstractMethod());
  }
}
