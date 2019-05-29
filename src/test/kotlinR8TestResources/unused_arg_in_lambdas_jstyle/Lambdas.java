// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package unused_arg_in_lambdas_jstyle;

public class Lambdas {

  public interface MultiFunction<R, P1, P2, P3> {
    R get(P1 a, P2 b, P3 c);
  }

  public synchronized static <R, P1, P2, P3>
  void acceptMultiFunction(MultiFunction<R, P1, P2, P3> s, P1 a, P2 b, P3 c) {
    System.out.println(s.get(a, b, c));
  }
}
