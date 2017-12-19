// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.Resource;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

class OneShotByteResource implements Resource {

  private final Origin origin;
  private byte[] bytes;

  static ProgramResource create(
      Kind kind, Origin origin, byte[] bytes, Set<String> classDescriptors) {
    return new ProgramResource(kind, new OneShotByteResource(origin, bytes), classDescriptors);
  }

  private OneShotByteResource(Origin origin, byte[] bytes) {
    assert bytes != null;
    this.origin = origin;
    this.bytes = bytes;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  @Deprecated
  public Set<String> getClassDescriptors() {
    throw new Unreachable();
  }

  @Override
  public InputStream getStream() throws IOException {
    assert bytes != null;
    InputStream result = new ByteArrayInputStream(bytes);
    bytes = null;
    return result;
  }
}
