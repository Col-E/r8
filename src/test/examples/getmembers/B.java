// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package getmembers;

import java.lang.reflect.Method;

public class B {

  private String toBeInlined() throws Exception {
    Method baz = A.class.getMethod("baz", (Class[]) null);
    assert baz != null;
    String bazResult = (String) baz.invoke(null, null);
    assert bazResult.startsWith("foo");
    return bazResult;
  }

  synchronized static String inliner() throws Exception {
    B self = new B();
    return self.toBeInlined();
  }

}
