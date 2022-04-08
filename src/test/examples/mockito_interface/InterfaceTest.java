// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package mockito_interface;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InterfaceTest {

  @Mock
  private Interface fld;

  private InterfaceUser user;

  private boolean flag;

  public static void main(String[] args) {
    for (boolean flag : new boolean[] {true, false}) {
      InterfaceTest test = new InterfaceTest(flag);
      test.setUp();
      test.test();
    }
  }

  public InterfaceTest(boolean flag) {
    this.flag = flag;
  }

  public void setUp() {
    MockitoAnnotations.initMocks(this);
    user = new InterfaceUser(fld);
  }

  public void test() {
    if (flag) {
      user.consume();
    }
    verify(fld, times(flag ? 1 : 0)).onEnterForeground();
  }
}
