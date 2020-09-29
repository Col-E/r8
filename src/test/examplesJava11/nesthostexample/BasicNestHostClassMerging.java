// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

import com.android.tools.r8.NeverInline;

public class BasicNestHostClassMerging {

  private String field = "Outer";

  public static class MiddleOuter extends BasicNestHostClassMerging {

    private String field = "Middle";

    public static void main(String[] args) {
      System.out.println(new InnerMost().getFields());
    }
  }

  public static class MiddleInner extends MiddleOuter {
    private String field = "Inner";
  }

  public static class InnerMost extends MiddleInner {

    @NeverInline
    public String getFields() {
      return ((BasicNestHostClassMerging) this).field
          + ((MiddleOuter) this).field
          + ((MiddleInner) this).field;
    }
  }

  public static void main(String[] args) {
    System.out.println(new InnerMost().getFields());
  }
}
