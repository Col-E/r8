// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nest;

public class NestLambda {

  private void print(Object o) {
    System.out.println("printed: " + o);
  }

  Inner getInner() {
    return new Inner();
  }

  // Avoids java.util.Consumer to run below on Apis below 24.
  interface ThisConsumer<T> {
    void accept(T t);
  }

  class Inner implements Itf {

    void exec(ThisConsumer<String> consumer) {
      consumer.accept("inner");
    }

    void execLambda() {
      exec(NestLambda.this::print);
    }
  }

  interface Itf {
    private void printItf(Object o) {
      System.out.println("printed from itf: " + o);
    }
  }

  void exec(ThisConsumer<String> consumer) {
    consumer.accept("here");
  }

  void execItfLambda(Itf itf) {
    exec(itf::printItf);
  }

  public static void main(String[] args) {
    new NestLambda().getInner().execLambda();
    new NestLambda().execItfLambda(new Itf() {});
  }
}
