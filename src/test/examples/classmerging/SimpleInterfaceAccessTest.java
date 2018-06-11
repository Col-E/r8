// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

import classmerging.pkg.SimpleInterfaceImplRetriever;

public class SimpleInterfaceAccessTest {
  public static void main(String[] args) {
    // It is not possible to merge the interface SimpleInterface into SimpleInterfaceImpl, since
    // this would lead to an illegal class access here.
    SimpleInterface obj = SimpleInterfaceImplRetriever.getSimpleInterfaceImpl();
    obj.foo();
  }
}
