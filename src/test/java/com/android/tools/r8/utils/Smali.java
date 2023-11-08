// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.smali.LexerErrorInterface;
import com.android.tools.smali.smali.smaliFlexLexer;
import com.android.tools.smali.smali.smaliParser;
import com.android.tools.smali.smali.smaliTreeWalker;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenSource;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;

// Adapted from org.jf.smali.SmaliTestUtils.
public class Smali {

  public static byte[] compile(String smaliText)
      throws RecognitionException, IOException, ExecutionException {
    return compile(smaliText, 15);
  }

  public static byte[] compile(String... smaliText)
      throws RecognitionException, IOException, ExecutionException {
    return compile(Arrays.asList(smaliText), 15);
  }

  public static byte[] compile(String smaliText, int apiLevel)
      throws IOException, RecognitionException, ExecutionException {
    return compile(ImmutableList.of(smaliText), apiLevel);
  }

  public static byte[] compile(List<String> smaliTexts)
      throws RecognitionException, IOException, ExecutionException {
    return compile(smaliTexts, 15);
  }

  public static byte[] compile(List<String> smaliTexts, int apiLevel)
      throws RecognitionException, IOException, ExecutionException {
    DexBuilder dexBuilder = new DexBuilder(Opcodes.forApi(apiLevel));

    for (String smaliText : smaliTexts) {
      Reader reader = new StringReader(smaliText);

      LexerErrorInterface lexer = new smaliFlexLexer(reader, apiLevel);
      CommonTokenStream tokens = new CommonTokenStream((TokenSource) lexer);

      smaliParser parser = new smaliParser(tokens);
      parser.setVerboseErrors(true);
      parser.setAllowOdex(false);
      parser.setApiLevel(apiLevel);

      smaliParser.smali_file_return result = parser.smali_file();

      if (parser.getNumberOfSyntaxErrors() > 0 || lexer.getNumberOfSyntaxErrors() > 0) {
        throw new RuntimeException(
            "Error occured while compiling text:\n" + StringUtils.join("\n", smaliTexts));
      }

      CommonTree t = result.getTree();

      CommonTreeNodeStream treeStream = new CommonTreeNodeStream(t);
      treeStream.setTokenStream(tokens);

      smaliTreeWalker dexGen = new smaliTreeWalker(treeStream);
      dexGen.setApiLevel(apiLevel);
      dexGen.setVerboseErrors(true);
      dexGen.setDexBuilder(dexBuilder);
      dexGen.smali_file();

      if (dexGen.getNumberOfSyntaxErrors() > 0) {
        throw new RuntimeException("Error occured while compiling text");
      }
    }

    MemoryDataStore dataStore = new MemoryDataStore();

    dexBuilder.writeTo(dataStore);

    // This returns the full backingstore from MemoryDataStore, which by default is 1024k bytes.
    // We process it via our reader and writer to trim it to the exact size and update its checksum.
    byte[] data = dataStore.getData();
    SingleFileConsumer consumer = new SingleFileConsumer();
    AndroidApp app = AndroidApp.builder().addDexProgramData(data, Origin.unknown()).build();
    InternalOptions options = new InternalOptions();
    options.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(apiLevel));
    options.programConsumer = consumer;
    ExecutorService executor = options.getThreadingModule().createThreadedExecutorService(1);
    try {
      DexApplication dexApp = new ApplicationReader(app, options, Timing.empty()).read(executor);
      ApplicationWriter writer =
          ApplicationWriter.create(
              AppView.createForD8(
                  AppInfo.createInitialAppInfo(
                      dexApp, GlobalSyntheticsStrategy.forNonSynthesizing())),
              null);
      writer.write(executor);
      return consumer.contents;
    } finally {
      executor.shutdown();
    }
  }

  private static class SingleFileConsumer implements DexIndexedConsumer {

    byte[] contents;

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      contents = data.copyByteData();
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}
  }
}
