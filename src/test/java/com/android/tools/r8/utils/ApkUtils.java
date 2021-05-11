// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApkUtils {

  public static ProcessResult apkMasseur(Path apk, Path dexSources, Path out) throws IOException {
    ImmutableList.Builder<String> command =
        new ImmutableList.Builder<String>()
            .add("tools/apk_masseur.py")
            .add("--dex")
            .add(dexSources.toString())
            .add("--out")
            .add(out.toString())
            .add("--install")
            .add(apk.toString());
    ProcessBuilder builder = new ProcessBuilder(command.build());
    builder.directory(Paths.get(ToolHelper.THIRD_PARTY_DIR).toAbsolutePath().getParent().toFile());
    return ToolHelper.runProcess(builder);
  }
}
