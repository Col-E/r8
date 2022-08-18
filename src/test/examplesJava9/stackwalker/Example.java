// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package stackwalker;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.stream.Collectors;

public class Example {
  public static void main(String[] args) {
    List<String> OneFrameStack =
        StackWalker.getInstance()
            .walk(s -> s.limit(7).map(StackFrame::getMethodName).collect(Collectors.toList()));
    System.out.println(OneFrameStack);
    frame1();
  }

  public static void frame1() {
    frame2();
  }

  public static void frame2() {
    List<String> ThreeFrameStack =
        StackWalker.getInstance()
            .walk(s -> s.limit(7).map(StackFrame::getMethodName).collect(Collectors.toList()));
    System.out.println(ThreeFrameStack);
  }
}
