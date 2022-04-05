// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.internal.ProguardMappingProviderBuilderImpl;

@Keep
public abstract class ProguardMappingProvider extends MappingProvider {

  public static Builder builder() {
    return new ProguardMappingProviderBuilderImpl();
  }

  @Keep
  public abstract static class Builder
      extends MappingProviderBuilder<ProguardMappingProvider, Builder> {

    public abstract Builder registerUse(ClassReference classReference);

    public abstract Builder allowLookupAllClasses();

    public abstract Builder setProguardMapProducer(ProguardMapProducer proguardMapProducer);
  }
}
