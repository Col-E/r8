// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.classinliner.nonpublicsubtype.subpkg;

import com.android.tools.r8.ir.optimize.classinliner.nonpublicsubtype.ClassInlineNonPublicSubtypeTest.I;

public class Utils {

  // Non-public class can't be accessed by the context calling 'method'.
  // TODO(b/120061431): Class inlining should be able to inline this as the instance itself will be
  //  eliminated, but until then, it must consistently refuse to inline in case of invalid access.
  public static final I INSTANCE =
      new I() {
        @Override
        public void method() {
          System.out.println("Hello world");
        }
      };
}
