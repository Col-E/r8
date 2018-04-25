// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package regress_77944861;

import regress_77944861.inner.TopLevelPolicy;
import regress_77944861.inner.TopLevelPolicy.MobileIconState;

public class SomeView {

  public static String get(MobileIconState state) {
    // Field read context. TopLevelPolicy$IconState is not accessible in this context.
    return state.description;
  }

  public static void main(String[] args) {
    MobileIconState state = new MobileIconState();
    TopLevelPolicy.set(state, "foo");
    System.out.println(get(state));
  }
}

