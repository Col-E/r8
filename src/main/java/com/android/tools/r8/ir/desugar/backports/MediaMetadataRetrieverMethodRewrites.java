// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;
import org.objectweb.asm.Opcodes;

public final class MediaMetadataRetrieverMethodRewrites {

  private MediaMetadataRetrieverMethodRewrites() {}

  public static MethodInvokeRewriter rewriteClose() {
    // Rewrite android/media/MediaMetadataRetriever#close to
    // android/media/MediaMetadataRetriever#release
    return (invoke, factory) ->
        new CfInvoke(
            Opcodes.INVOKEVIRTUAL, factory.androidMediaMetadataRetrieverMembers.release, false);
  }
}
