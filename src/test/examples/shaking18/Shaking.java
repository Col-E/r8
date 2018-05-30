// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package shaking18;

public class Shaking {

  public static void main(String[] args) {
    int i = args.length;
    run(i);
  }

  private static void run(int i) {
    // Three invocations of each method to avoid inlining.
    Options o =
        i % 2 == 0 ? getOptions(i % 3) : i % 5 == 0 ? getOptions(i % 7) : getOptions(i % 11);
    print(make(o, i % 13));
    print(make(o, i % 17));
    print(make(o, i % 19));
  }

  private static Base make(Options o, int i) {
    if (o.alwaysFalse) {
      return new DerivedUnused();
    }
    return o.dummy ? new Derived1() : new Derived2();
  }

  private static Options getOptions(int i) {
    Options o = new Options();
    o.dummy = i % 101 < 23;
    return o;
  }

  private static void print(Base b) {
    System.out.println(b.getMessage());
  }
}
