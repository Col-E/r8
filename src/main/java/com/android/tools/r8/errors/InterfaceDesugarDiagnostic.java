// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;

/** Common interface type for all diagnostics related to interface-method desugaring. */
@KeepForApi(kind = KeepItemKind.ONLY_CLASS)
public interface InterfaceDesugarDiagnostic extends DesugarDiagnostic {}
