// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

public class OptionalTestClass {
  static class Data {
    final int id;
    final String name;

    Data(int id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Data)) return false;
      Data data = (Data) o;
      return id == data.id && name.equals(data.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, name);
    }

    @Override
    public String toString() {
      return "Data{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
  }

  static class OptionalAdapter<T> extends TypeAdapter<Optional<T>> {
    private final TypeAdapter<T> delegate;

    public OptionalAdapter(TypeAdapter<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(JsonWriter out, Optional<T> value) throws IOException {
      if (!value.isPresent()) {
        out.nullValue();
        return;
      }
      delegate.write(out, value.get());
    }

    @Override
    public Optional<T> read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return Optional.empty();
      }
      return Optional.of(delegate.read(in));
    }

    public static OptionalAdapter<?> getInstance(TypeToken<?> typeToken) {
      TypeAdapter<?> delegate;
      Type type = typeToken.getType();
      assert type instanceof ParameterizedType;
      Type innerType = ((ParameterizedType) type).getActualTypeArguments()[0];
      delegate = new Gson().getAdapter(TypeToken.get(innerType));
      return new OptionalAdapter<>(delegate);
    }
  }

  public static void main(String[] args) {
    GsonBuilder builder = new GsonBuilder();
    builder.registerTypeAdapter(
        Optional.class, OptionalAdapter.getInstance(new TypeToken<Optional<Data>>() {}));
    Gson gson = builder.create();
    Optional<Data> optionalData = Optional.of(new Data(1, "a"));
    String optionalDataSerialized = gson.toJson(optionalData);
    Optional<Data> optionalDataDeserialized =
        gson.<Optional<Data>>fromJson(optionalDataSerialized, Optional.class);
    System.out.println(optionalData.getClass() == optionalDataDeserialized.getClass());
    System.out.println(optionalData.equals(optionalDataDeserialized));
  }
}
