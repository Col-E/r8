// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

@NeverClassInline
class ClassForIf {
  ClassForIf() {
  }

  @NeverInline
  synchronized void nonPublicMethod() {
    System.out.println("ClassForIf::nonPublicMethod");
  }

  @NeverInline
  synchronized public void publicMethod() {
    System.out.println("ClassForIf::publicMethod");
  }
}

class ClassForSubsequent {
  ClassForSubsequent() {
  }

  @NeverInline
  synchronized void nonPublicMethod() {
    System.out.println("ClassForSubsequent::nonPublicMethod");
  }

  @NeverInline
  synchronized public void publicMethod() {
    System.out.println("ClassForSubsequent::publicMethod");
  }
}

class MainForAccessModifierTest {
  public static void callIfNonPublic() {
    new ClassForIf().nonPublicMethod();
  }

  public static void callIfPublic() {
    new ClassForIf().publicMethod();
  }
}
