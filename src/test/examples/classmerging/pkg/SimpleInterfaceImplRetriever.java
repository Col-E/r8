// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging.pkg;

import classmerging.SimpleInterface;

public class SimpleInterfaceImplRetriever {

  public static SimpleInterface getSimpleInterfaceImpl() {
    return new SimpleInterfaceImpl();
  }

  // This class is intentionally marked private. It is not possible to merge the interface
  // SimpleInterface into SimpleInterfaceImpl, since this would lead to an illegal class access
  // in SimpleInterfaceAccessTest.
  private static class SimpleInterfaceImpl implements SimpleInterface {

    @Override
    public void foo() {
      System.out.println("In foo on SimpleInterfaceImpl");
    }
  }
}
