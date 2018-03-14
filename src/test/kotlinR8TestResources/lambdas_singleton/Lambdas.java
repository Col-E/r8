// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class Lambdas {
  public interface Function<R, P1, P2> {
    R get(P1 a, P2 b);
  }

  public synchronized static <R, P1, P2> void accept(Function<R, P1, P2> s, P1 a, P2 b) {
    System.out.println(s.get(a, b));
  }
}
