// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package adaptclassstrings;

public class Main {
  public static void main(String[] args) throws Exception {
    int f = 3;
    A a = new A(f);
    AA aa = new AA(f);
    assert a.foo() != aa.foo();

    a.bar();

    Object a_foo =
        Class.forName("adaptclassstrings.A").getMethod("foo").invoke(a);
    Object aa_foo =
        Class.forName("adaptclassstrings.AA").getMethod("foo").invoke(aa);
    assert !a_foo.equals(aa_foo);

    Object c_to_a_foo =
        Class.forName((String)
            Class.forName("adaptclassstrings.C").getField("OTHER").get(null))
            .getMethod("foo").invoke(a);
    assert a_foo.equals(c_to_a_foo);

    String cName = (String) Class.forName(C.ITSELF).getField("ITSELF").get(null);
    assert cName.equals(A.OTHER);
  }
}
