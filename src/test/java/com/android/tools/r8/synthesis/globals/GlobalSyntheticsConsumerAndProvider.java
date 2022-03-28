// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis.globals;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.GlobalSyntheticsResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.Origin;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class GlobalSyntheticsConsumerAndProvider
    implements GlobalSyntheticsConsumer, GlobalSyntheticsResourceProvider {

  private byte[] bytes;

  @Override
  public void accept(byte[] bytes) {
    assertNull(this.bytes);
    assertNotNull(bytes);
    this.bytes = bytes;
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public InputStream getByteStream() throws ResourceException {
    return new ByteArrayInputStream(bytes);
  }

  public boolean hasBytes() {
    return true;
  }
}
