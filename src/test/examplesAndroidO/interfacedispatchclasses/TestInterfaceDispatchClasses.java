// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package interfacedispatchclasses;

public class TestInterfaceDispatchClasses {
  public static void main(String[] args) {
    System.out.println("TestInterfaceDispatchClasses::main(String[])");
    System.out.println(args[0]);
    boolean doCall = args[0].equals("true");
    Caller1.run(doCall);
    Caller2.run(doCall);
    Caller3.run(doCall);
  }
}
