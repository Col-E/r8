// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.ProguardPathFilter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.io.ByteStreams;
import java.io.InputStream;
import java.nio.charset.Charset;

public class ResourceAdapter {

  private final DexItemFactory dexItemFactory;
  private final GraphLense graphLense;
  private final NamingLens namingLense;
  private final InternalOptions options;

  public ResourceAdapter(
      DexItemFactory dexItemFactory,
      GraphLense graphLense,
      NamingLens namingLense,
      InternalOptions options) {
    this.dexItemFactory = dexItemFactory;
    this.graphLense = graphLense;
    this.namingLense = namingLense;
    this.options = options;
  }

  public DataEntryResource adaptFileContentsIfNeeded(DataEntryResource file) {
    ProguardPathFilter filter = options.proguardConfiguration.getAdaptResourceFileContents();
    return filter.isEnabled()
            && !file.getName().toLowerCase().endsWith(FileUtils.CLASS_EXTENSION)
            && filter.matches(file.getName())
        ? adaptFileContents(file)
        : file;
  }

  // According to the Proguard documentation, the resource files should be parsed and written using
  // the platform's default character set.
  private DataEntryResource adaptFileContents(DataEntryResource file) {
    try (InputStream in = file.getByteStream()) {
      byte[] bytes = ByteStreams.toByteArray(in);
      String contents = new String(bytes, Charset.defaultCharset());

      FileContentsAdapter adapter = new FileContentsAdapter(contents);
      if (adapter.run()) {
        return DataEntryResource.fromBytes(
            adapter.getResult().getBytes(Charset.defaultCharset()),
            file.getName(),
            file.getOrigin());
      }
    } catch (ResourceException e) {
      options.reporter.error(
          new StringDiagnostic("Failed to open input: " + e.getMessage(), file.getOrigin()));
    } catch (Exception e) {
      options.reporter.error(new ExceptionDiagnostic(e, file.getOrigin()));
    }
    return file;
  }

  private class FileContentsAdapter {

    private final String contents;
    private final StringBuilder result = new StringBuilder();

    // If any type names in `contents` have been updated. If this flag is still true in the end,
    // then we can simply use the resource as it was.
    private boolean changed = false;
    private int outputFrom = 0;
    private int position = 0;

    public FileContentsAdapter(String contents) {
      this.contents = contents;
    }

    public boolean run() {
      do {
        handleMisc();
        handleJavaType();
      } while (!eof());
      if (changed) {
        // At least one type was renamed. We need to flush all characters in `contents` that follow
        // the last type that was renamed.
        outputRangeFromInput(outputFrom, contents.length());
      } else {
        // No types were renamed. In this case the adapter should simply have scanned through
        // `contents`, without outputting anything to `result`.
        assert outputFrom == 0;
        assert result.toString().isEmpty();
      }
      return changed;
    }

    public String getResult() {
      assert changed;
      return result.toString();
    }

    // Forwards the cursor until the current character is a Java identifier part.
    private void handleMisc() {
      while (!eof() && !Character.isJavaIdentifierPart(contents.charAt(position))) {
        position++;
      }
    }

    // Reads a Java type from the current position in `contents`, and then checks if the given
    // type has been renamed.
    private void handleJavaType() {
      if (eof()) {
        return;
      }

      assert Character.isJavaIdentifierPart(contents.charAt(position));
      int start = position++;
      while (!eof()) {
        char currentChar = contents.charAt(position);
        if (Character.isJavaIdentifierPart(currentChar)) {
          position++;
          continue;
        }
        if (currentChar == '.'
            && !eof(position + 1)
            && Character.isJavaIdentifierPart(contents.charAt(position + 1))) {
          // Consume the dot and the Java identifier part that follows the dot.
          position += 2;
          continue;
        }

        // Not a valid extension of the type name.
        break;
      }

      if ((start > 0 && isDashOrDot(contents.charAt(start - 1)))
          || (!eof(position) && isDashOrDot(contents.charAt(position)))) {
        // The Java type starts with '-' or '.', and should not be renamed.
        return;
      }

      String javaType = contents.substring(start, position);
      DexString descriptor =
          dexItemFactory.lookupString(
              DescriptorUtils.javaTypeToDescriptorIgnorePrimitives(javaType));
      DexType dexType = descriptor != null ? dexItemFactory.lookupType(descriptor) : null;
      if (dexType != null) {
        DexString renamedDescriptor = namingLense.lookupDescriptor(graphLense.lookupType(dexType));
        if (!descriptor.equals(renamedDescriptor)) {
          // Need to flush all changes up to and excluding 'start', and then output the renamed
          // type.
          outputRangeFromInput(outputFrom, start);
          outputString(DescriptorUtils.descriptorToJavaType(renamedDescriptor.toSourceString()));
          outputFrom = position;
          changed = true;
        }
      }
    }

    private boolean isDashOrDot(char c) {
      return c == '.' || c == '-';
    }

    private void outputRangeFromInput(int from, int toExclusive) {
      if (from < toExclusive) {
        result.append(contents.substring(from, toExclusive));
      }
    }

    private void outputString(String s) {
      result.append(s);
    }

    private boolean eof() {
      return eof(position);
    }

    private boolean eof(int position) {
      return position == contents.length();
    }
  }
}
