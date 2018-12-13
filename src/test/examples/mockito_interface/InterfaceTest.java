// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package mockito_interface;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(Parameterized.class)
public class InterfaceTest {
  @Mock
  private Interface fld;

  private InterfaceUser user;

  private boolean flag;

  @Parameterized.Parameters(name = "flag: {0}")
  public static Boolean[] data() {
    return new Boolean[] {true, false};
  }

  public InterfaceTest(boolean flag) {
    this.flag = flag;
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    user = new InterfaceUser(fld);
  }

  @Test
  public void test() {
    if (flag) {
      user.consume();
    }
    verify(fld, times(flag ? 1 : 0)).onEnterForeground();
  }
}
