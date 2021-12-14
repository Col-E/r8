// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import java.io.IOException;
import java.nio.file.Path;

public interface SpecificationConverter {

  void convertAllAPILevels(
      StringResource inputSpecification, Path androidLib, StringConsumer output) throws IOException;
}
