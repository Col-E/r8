// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package enumStatic;

public class EnumStaticMain {

  enum EnumStatic {
    A,
    B {
      static int i = 17;

      static void print() {
        System.out.println("-" + i);
      }

      public void virtualPrint() {
        print();
      }
    };

    public void virtualPrint() {}
  }

  public static void main(String[] args) throws Throwable {
    EnumStatic.B.virtualPrint();
  }
}
