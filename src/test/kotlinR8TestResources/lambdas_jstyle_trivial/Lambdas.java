// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class Lambdas {
  public interface IntConsumer {
    void put(int value);
  }

  public interface Consumer<T> {
    void put(T t);
  }

  public interface Supplier<T> {
    T get();
  }

  public interface IntSupplier {
    int get();
  }

  public interface MultiFunction<R, P1, P2, P3> {
    R get(P1 a, P2 b, P3 c);
  }

  public synchronized static void acceptStringSupplier(Supplier<String> s) {
    System.out.println(s.get());
  }

  public synchronized static <T> void acceptGenericSupplier(Supplier<T> s) {
    System.out.println(s.get());
  }

  public synchronized static void acceptIntSupplier(IntSupplier s) {
    System.out.println(s.get());
  }

  public synchronized static void acceptStringConsumer(Consumer<String> s, String arg) {
    s.put(arg);
  }

  public synchronized static <T> void acceptGenericConsumer(Consumer<T> s, T arg) {
    s.put(arg);
  }

  public synchronized static void acceptIntConsumer(IntConsumer s, int arg) {
    s.put(arg);
  }

  public synchronized static <R, P1, P2, P3>
  void acceptMultiFunction(MultiFunction<R, P1, P2, P3> s, P1 a, P2 b, P3 c) {
    System.out.println(s.get(a, b, c));
  }
}
