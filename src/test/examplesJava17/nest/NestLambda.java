// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nest;

import java.util.function.Consumer;

public class NestLambda {

  private void print(Object o) {
    System.out.println("printed: " + o);
  }

  Inner getInner() {
    return new Inner();
  }

  class Inner {

    void exec(Consumer<Object> consumer) {
      consumer.accept("inner");
    }

    void execLambda() {
      exec(NestLambda.this::print);
    }
  }

  public static void main(String[] args) {
    new NestLambda().getInner().execLambda();
  }
}
