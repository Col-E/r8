// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import org.junit.Test;

public class ExtraMethodNullTest extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClassesAndInnerClasses(One.class)
        .addKeepMainRule(One.class)
        .run(One.class)
        .assertFailureWithErrorThatMatches(containsString("java.lang.NullPointerException"));
  }

  public static class One {

    public static void main(String[] args) {
      One one = new One();
      Other other = args.length == 0 ? null : new Other();
      other.print(one);
    }

    static class Other {
      @NeverInline
      Object print(Object one) {
        return one;
      }
    }
  }
}
