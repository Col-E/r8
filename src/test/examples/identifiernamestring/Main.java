// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package identifiernamestring;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Main {
  public static void main(String[] args) throws Exception {
    A ax = new A();
    assert ax.boo.equals(A.TYPE_B);
    try {
      // Should be renamed
      ax.bar("identifiernamestring.B");
    } catch (NullPointerException e) {
      System.err.println(e.getMessage());
    }

    Class a = Class.forName(A.TYPE_A);
    Class bByA = Class.forName(B.TYPO_A);
    // A's name is kept.
    assert a.equals(bByA);

    Class b = Class.forName(ax.boo);
    Class bByB = Class.forName(B.TYPO_B);
    // As TYPO_B is not renamed, they will be different.
    assert !b.equals(bByB);

    Field foo = R.findField(B.class, "foo");
    System.out.println(foo.getName());
    Method boo = R.findMethod(B.class, "boo", new Class[] { A.class });
    System.out.println(boo.getName());
  }
}