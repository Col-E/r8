// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis.globals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.GlobalSyntheticsResourceProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GlobalSyntheticsTestingConsumer implements GlobalSyntheticsConsumer {

  private final Map<ClassReference, GlobalSyntheticsResourceProvider> globals = new HashMap<>();
  private boolean finished = false;

  @Override
  public void accept(ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
    assertFalse(finished);
    assertNotNull(data);
    Origin origin =
        context == null
            ? Origin.unknown()
            : new Origin(Origin.root()) {
              @Override
              public String part() {
                return "globals(" + context.getTypeName() + ")";
              }
            };
    TestingProvider provider = new TestingProvider(origin, data.copyByteData());
    GlobalSyntheticsResourceProvider old = globals.put(context, provider);
    assertNull(old);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    assertFalse(finished);
    finished = true;
  }

  public boolean hasGlobals() {
    return !globals.isEmpty();
  }

  public boolean isSingleGlobal() {
    return globals.size() == 1 && globals.get(null) != null;
  }

  public GlobalSyntheticsResourceProvider getIndexedModeProvider() {
    assertTrue(isSingleGlobal());
    return globals.get(null);
  }

  public GlobalSyntheticsResourceProvider getProvider(ClassReference clazz) {
    assertNotNull("Use getIndexedModeProvider to get single outputs", clazz);
    assertFalse(isSingleGlobal());
    return globals.get(clazz);
  }

  public Collection<GlobalSyntheticsResourceProvider> getProviders() {
    return globals.values();
  }

  private static class TestingProvider implements GlobalSyntheticsResourceProvider {

    private final Origin origin;
    private final byte[] bytes;

    public TestingProvider(Origin origin, byte[] bytes) {
      this.origin = origin;
      this.bytes = bytes;
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }

    @Override
    public InputStream getByteStream() {
      return new ByteArrayInputStream(bytes);
    }
  }
}
