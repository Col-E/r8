// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

import com.android.tools.r8.NeverInline;

public class BasicNestHostTreePruning {

  private String field = System.currentTimeMillis() >= 0 ? "NotPruned" : "Dead";

  public static class NotPruned extends BasicNestHostTreePruning {

    @NeverInline
    public String getFields() {
      return ((BasicNestHostTreePruning) this).field;
    }
  }

  public static class Pruned {

    public static void main(String[] args) {
      System.out.println("NotPruned");
    }
  }

  public static void main(String[] args) {
    System.out.println(new NotPruned().getFields());
  }
}
