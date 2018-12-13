// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package mockito_interface;

import java.util.logging.Logger;

public class Implementer implements Interface {
  private static final String TAG = Implementer.class.getSimpleName();
  private Logger logger;

  public Implementer() {
    this.logger = Logger.getLogger(TAG);
  }

  @Override
  public void onEnterForeground() {
    logger.info("onEnterForeground called");
  }

}
