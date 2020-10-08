// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical.testclasses;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.classmerging.vertical.NoIllegalClassAccessWithAccessModificationsTest.SimpleInterface;

public class NoIllegalClassAccessWithAccessModificationsTestClasses {

  public static Class<?> getSimpleInterfaceImplClass() {
    return SimpleInterfaceImpl.class;
  }

  public static class SimpleInterfaceFactory {

    public static SimpleInterface create() {
      return new SimpleInterfaceImpl();
    }
  }

  // This class is intentionally marked private. It is not possible to merge the interface
  // SimpleInterface into SimpleInterfaceImpl, since this would lead to an illegal class access
  // in SimpleInterfaceAccessTest.
  @NoHorizontalClassMerging
  private static class SimpleInterfaceImpl implements SimpleInterface {

    @Override
    public void foo() {
      System.out.println("In foo on SimpleInterfaceImpl");
    }
  }
}
