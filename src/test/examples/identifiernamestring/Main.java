// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package identifiernamestring;

public class Main {
  public static void main(String[] args) throws Exception {
    A ax = new A();
    assert ax.boo.equals(A.TYPE_B);
    // Should be renamed
    ax.bar("identifiernamestring.B");

    Class a = Class.forName(A.TYPE_A);
    Class b_a = Class.forName(B.TYPO_A);
    // A's name is kept.
    assert a.equals(b_a);

    Class b = Class.forName(ax.boo);
    Class b_b = Class.forName(B.TYPO_B);
    // As TYPO_B is not renamed, they will be different.
    assert !b.equals(b_b);
  }
}