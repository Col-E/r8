// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramClass;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DeterminismChecker {

  /**
   * Create a new checker with a directory of log files as backing
   *
   * <p>The checker must be created as a new instance for each compilation. Withing the directory
   * the respective check points will be emitted/checked via successive log files.
   */
  public static DeterminismChecker createWithFileBacking(Path directory) {
    return new DeterminismChecker(
        new LineCallbackSupplier() {
          // Index of the "checkpoint", there can be several if checked at multiple points during
          // compiler execution.
          private int index = 0;

          @Override
          public LineCallback createCallback() throws IOException {
            // This is called on each "checkpoint" and the index is bumped.
            Path log = directory.resolve("" + index++ + ".log");
            if (Files.exists(log)) {
              System.out.println("Checking against determinism log: " + log);
              return new LineCallbackChecker(Files.newBufferedReader(log, StandardCharsets.UTF_8));
            } else {
              System.out.println("Writing determinism log: " + log);
              // Note that Files.newBufferedWriter will cause issues in presence of malformed input
              // and unmappable character errors, since Files.newBufferedWriter uses the
              // java.nio.charset.CharsetDecoder default action, which is to report such errors,
              // instead of dealing with them in java.nio.charset.CharsetDecoder#onMalformedInput.
              BufferedWriter bufferedWriter =
                  new BufferedWriter(
                      new OutputStreamWriter(
                          new FileOutputStream(log.toFile()), StandardCharsets.UTF_8));
              return new LineCallbackWriter(bufferedWriter);
            }
          }
        });
  }

  /** Supplier/factory to support multiple checkpoints. */
  public interface LineCallbackSupplier {
    LineCallback createCallback() throws IOException;
  }

  /** Interface for the write-or-check of each line. */
  public interface LineCallback extends Closeable {
    boolean onLine(String line) throws IOException;
  }

  private final LineCallbackSupplier callbackFactory;

  private DeterminismChecker(LineCallbackSupplier callbackFactory) {
    this.callbackFactory = callbackFactory;
  }

  private static String fmtClass(DexProgramClass clazz) {
    return clazz.getType().toSourceString()
        + " "
        + clazz.getMethodCollection().getBackingDescriptionString();
  }

  private static String fmtMethod(DexEncodedMethod method) {
    return method.getReference().toSourceString();
  }

  public <E extends Exception> void accept(ThrowingConsumer<LineCallback, E> consumer)
      throws E, IOException {
    try (LineCallback callback = callbackFactory.createCallback()) {
      consumer.accept(callback);
    }
  }

  public void check(AppView<?> appView) {
    try (LineCallback callback = callbackFactory.createCallback()) {
      List<DexProgramClass> classes = new ArrayList<>(appView.appInfo().classes());
      classes.sort(Comparator.comparing(ProgramClass::getType));
      for (int i = 0; i < classes.size(); i++) {
        DexProgramClass clazz = classes.get(i);
        checkClass(callback, clazz);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkClass(LineCallback callback, DexProgramClass clazz) throws IOException {
    String line = fmtClass(clazz);
    if (!callback.onLine(line)) {
      return;
    }
    for (DexEncodedMethod method : clazz.methods()) {
      checkMethod(callback, method);
    }
  }

  private void checkMethod(LineCallback callback, DexEncodedMethod method) throws IOException {
    String header = fmtMethod(method);
    if (!callback.onLine(header)) {
      return;
    }
    if (method.hasCode()) {
      List<String> lines = StringUtils.splitLines(method.getCode().toString());
      for (String line : lines) {
        if (!callback.onLine(line)) {
          return;
        }
      }
    } else {
      if (!callback.onLine("<nocode>")) {
        return;
      }
    }
  }

  /** Shared escape function. */
  private static String escape(String line) {
    // Replace \r so the line splitting is always consistently on \n.
    return line.replace("\r", "<CR>");
  }

  /** Implementation of the checker (must be consistent with the writer). */
  private static class LineCallbackChecker implements LineCallback {

    private final BufferedReader reader;

    public LineCallbackChecker(BufferedReader reader) {
      this.reader = reader;
    }

    @Override
    public boolean onLine(String unescapedLine) throws IOException {
      String line = escape(unescapedLine);
      String dumpLine = reader.readLine();
      if (!dumpLine.equals(line)) {
        // The line might contain a non unicode points. If so, the dump line will have mapped those
        // to ? when writing. Decode the string and retry equals.
        String decoded = new String(line.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        if (!decoded.equals(dumpLine)) {
          throw new AssertionError(
              "\nMismatch for line: " + decoded + "\n" + "    and dump-line: " + dumpLine);
        }
      }
      return true;
    }

    @Override
    public void close() throws IOException {
      reader.close();
    }
  }

  /** Implementation of the writer (must be consistent with the checker). */
  private static class LineCallbackWriter implements LineCallback {

    private final Writer writer;

    public LineCallbackWriter(Writer writer) {
      this.writer = writer;
    }

    @Override
    public boolean onLine(String line) throws IOException {
      writer.write(escape(line));
      writer.write('\n');
      return true;
    }

    @Override
    public void close() throws IOException {
      writer.close();
    }
  }
}
