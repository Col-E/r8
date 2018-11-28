// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.builders;

import com.android.tools.r8.NeverInline;

public class BuildersTestClass {
  private static int ID = 0;

  private static int nextInt() {
    return ID++;
  }

  private static String next() {
    return Integer.toString(nextInt());
  }

  public static void main(String[] args) {
    BuildersTestClass test = new BuildersTestClass();
    test.testSimpleBuilder1();
    test.testSimpleBuilderWithMultipleBuilds();
    test.testBuilderConstructors();
    test.testWithControlFlow();
    test.testWithMoreControlFlow();
  }

  @NeverInline
  private void testSimpleBuilder1() {
    System.out.println(
        new PairBuilder<String, String>().setFirst("f-" + next()).build());
    testSimpleBuilder2();
    testSimpleBuilder3();
  }

  @NeverInline
  private void testSimpleBuilder2() {
    System.out.println(
        new PairBuilder<String, String>().setSecond("s-" + next()).build());
  }

  @NeverInline
  private void testSimpleBuilder3() {
    System.out.println(new PairBuilder<String, String>()
        .setFirst("f-" + next()).setSecond("s-" + next()).build());
  }

  @NeverInline
  private void testSimpleBuilderWithMultipleBuilds() {
    PairBuilder<String, String> builder = new PairBuilder<>();
    Pair p1 = builder.build();
    System.out.println(p1.toString());
    builder.setFirst("f-" + next());
    Pair p2 = builder.build();
    System.out.println(p2.toString());
    builder.setSecond("s-" + next());
    Pair p3 = builder.build();
    System.out.println(p3.toString());
  }

  @NeverInline
  private void testBuilderConstructors() {
    System.out.println(new Tuple().toString());
    System.out.println(new Tuple(true, (byte) 77, (short) 9977, '#', 42,
        987654321123456789L, -12.34f, 43210.98765, "s-" + next() + "-s").toString());
  }

  @NeverInline
  private void testWithControlFlow() {
    ControlFlow flow = new ControlFlow(-1, 2, 7);
    for (int k = 0; k < 25; k++) {
      if (k % 3 == 0) {
        flow.foo(k);
      } else if (k % 3 == 1) {
        flow.bar(nextInt(), nextInt(), nextInt(), nextInt());
      }
    }
    System.out.println("flow = " + flow.toString());
  }

  @NeverInline
  private void testWithMoreControlFlow() {
    String str = "1234567890";
    Pos pos = new Pos();
    while (pos.y < str.length()) {
      pos.x = pos.y;
      pos.y = pos.x;

      if (str.charAt(pos.x) != '*') {
        if ('0' <= str.charAt(pos.y) && str.charAt(pos.y) <= '9') {
          while (pos.y < str.length() && '0' <= str.charAt(pos.y) && str.charAt(pos.y) <= '9') {
            pos.y++;
          }
        }
      }
    }
  }

  public static class Pos {
    public int x = 0;
    public int y = 0;
  }
}
