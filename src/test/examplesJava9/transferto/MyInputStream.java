// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package transferto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MyInputStream extends ByteArrayInputStream {

  public MyInputStream(byte[] buf) {
    super(buf);
  }

  @Override
  public long transferTo(OutputStream out) throws IOException {
    out.write((int) '$');
    return super.transferTo(out);
  }
}
