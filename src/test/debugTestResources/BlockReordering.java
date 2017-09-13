// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class BlockReordering {

  public static int conditionalReturn(boolean test) {
    if (test) return 1;
    else return 2;
  }

  public static int callConditionalReturn(boolean test) {
    return conditionalReturn(test);
  }

  public static int invertConditionalReturn(boolean test) {
    if (!test) return 1;
    else return 2;
  }

  public static int callInvertConditionalReturn(boolean test) {
    return invertConditionalReturn(test);
  }

  public static int fallthroughReturn(int x) {
    if (x <= 5) if (x <= 4) if (x <= 3) if (x <= 2) if (x <= 1) return x + 1;
    else return x + 2;
    else return x + 3;
    else return x + 4;
    else return x + 5;
    return x;
  }

  public static int callFallthroughReturn(int x) {
    return fallthroughReturn(x);
  }

  public static void main(String[] args) {
    System.out.println(callConditionalReturn(true));
    System.out.println(callConditionalReturn(false));
    System.out.println(callInvertConditionalReturn(true));
    System.out.println(callInvertConditionalReturn(false));
    System.out.println(callFallthroughReturn(1));
    System.out.println(callFallthroughReturn(5));
    System.out.println(callFallthroughReturn(6));
  }
}
