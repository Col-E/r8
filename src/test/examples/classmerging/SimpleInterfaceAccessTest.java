// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

import classmerging.pkg.SimpleInterfaceImplRetriever;

public class SimpleInterfaceAccessTest {
  public static void main(String[] args) {
    // Without access modifications, it is not possible to merge the interface SimpleInterface into
    // SimpleInterfaceImpl, since this would lead to an illegal class access here.
    SimpleInterface x = SimpleInterfaceImplRetriever.getSimpleInterfaceImpl();
    x.foo();

    // Without access modifications, it is not possible to merge the interface OtherSimpleInterface
    // into OtherSimpleInterfaceImpl, since this could lead to an illegal class access if another
    // package references OtherSimpleInterface.
    OtherSimpleInterface y = new OtherSimpleInterfaceImpl();
    y.bar();
  }

  // Should only be merged into OtherSimpleInterfaceImpl if access modifications are allowed.
  public interface SimpleInterface {

    void foo();
  }

  // Should only be merged into OtherSimpleInterfaceImpl if access modifications are allowed.
  public interface OtherSimpleInterface {

    void bar();
  }

  private static class OtherSimpleInterfaceImpl implements OtherSimpleInterface {

    @Override
    public void bar() {
      System.out.println("In bar on OtherSimpleInterfaceImpl");
    }
  }
}
