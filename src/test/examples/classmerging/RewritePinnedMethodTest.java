// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class RewritePinnedMethodTest {

  public static void main(String[] args) {
    C obj = new C();
    obj.m();
  }

  // A.m is pinned by a keep rule, to prevent it from being merged into B.
  public static class A {
    public void m() {
      System.out.println("In A.m");
    }
  }

  // B is merged into C.
  public static class B extends A {
    @Override
    public void m() {
      System.out.println("In B.m");
      super.m();
    }
  }

  public static class C extends B {
    @Override
    public void m() {
      System.out.println("In C.m");
      // This invocation is changed from invoke-super to invoke-direct. It would be valid for this
      // instruction to be on the form "invoke-super A.m". Therefore, the graph lense contains a
      // mapping for "A.m" from (context: "C.m", type: SUPER) to C.m$B, where the method C.m$B is
      // the direct method that gets created for B.m during vertical class merging.
      super.m();
    }
  }
}
