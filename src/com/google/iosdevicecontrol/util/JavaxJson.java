// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.iosdevicecontrol.util;

import com.google.common.base.VerifyException;
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities for handling javax.json structures. */
public final class JavaxJson {
  /** Empty JsonArray. */
  public static final JsonArray EMPTY_ARRAY = Json.createArrayBuilder().build();

  /** Empty JsonObject. */
  public static final JsonObject EMPTY_OBJECT = Json.createObjectBuilder().build();

  private static final JsonParser GSON_PARSER = new JsonParser();

  /** Parses a string to a {@link JsonArray}. */
  public static JsonArray parseArray(String jsonString) {
    try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
      return reader.readArray();
    }
  }

  /** Parses a string to a {@link JsonObject}. */
  public static JsonObject parseObject(String jsonString) {
    try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
      return reader.readObject();
    }
  }

  /** Converts a {@link com.google.gson.JsonArray} to a {@link JsonArray}. */
  public static JsonArray fromGson(com.google.gson.JsonArray gson) {
    return parseArray(gson.toString());
  }

  /** Converts a {@link com.google.gson.JsonObject} to a {@link JsonObject}. */
  public static JsonObject fromGson(com.google.gson.JsonObject gson) {
    return parseObject(gson.toString());
  }

  /** Converts a {@link JSONObject} to a {@link JsonObject}. */
  public static JsonObject fromOrgJson(JSONObject json) {
    return parseObject(json.toString());
  }

  /** Converts a {@link JsonArray} to a {@JsonArrayBuilder}. */
  public static JsonArrayBuilder toBuilder(JsonArray array) {
    JsonArrayBuilder builder = Json.createArrayBuilder();
    for (JsonValue value : array) {
      builder.add(value);
    }
    return builder;
  }

  /** Converts a {@link JsonObject} to a {@JsonObjectBuilder}. */
  public static JsonObjectBuilder toBuilder(JsonObject object) {
    JsonObjectBuilder builder = Json.createObjectBuilder();
    for (Entry<String, JsonValue> e : object.entrySet()) {
      builder.add(e.getKey(), e.getValue());
    }
    return builder;
  }

  /** Converts a {@link JsonArray} to a {@link com.google.gson.JsonArray}. */
  public static com.google.gson.JsonArray toGson(JsonArray json) {
    return GSON_PARSER.parse(json.toString()).getAsJsonArray();
  }

  /** Converts a {@link JsonObject} to a {@link com.google.gson.JsonObject}. */
  public static com.google.gson.JsonObject toGson(JsonObject json) {
    return GSON_PARSER.parse(json.toString()).getAsJsonObject();
  }

  /** Converts a {@link JsonObject} to a {@link JSONObject}. */
  public static JSONObject toOrgJson(JsonObject json) {
    try {
      return new JSONObject(json.toString());
    } catch (JSONException e) {
      // Should never happen, because we start from a valid JSON object.
      throw new VerifyException(e);
    }
  }

  /**
   * Converts a {@link JsonValue} to its corresponding Java object. Values of type {@link
   * JsonObject} or {@link JsonArray} are converted as specified by {@link #toJavaMap} and {@link
   * #toJavaList}, respectively.
   */
  @Nullable
  public static Object toJavaObject(JsonValue value) {
    switch (value.getValueType()) {
      case ARRAY:
        return toJavaList((JsonArray) value);
      case FALSE:
        return Boolean.FALSE;
      case NULL:
        return null;
      case NUMBER:
        JsonNumber number = (JsonNumber) value;
        return number.isIntegral() ? number.intValue() : number.doubleValue();
      case OBJECT:
        return toJavaMap((JsonObject) value);
      case STRING:
        return ((JsonString) value).getString();
      case TRUE:
        return Boolean.TRUE;
      default:
        throw new VerifyException("Json value with unknown type: " + value);
    }
  }

  /**
   * Converts a {@link JsonObject} to a map from names to objects returned by {@link #toJavaObject}.
   * The returned map is a mutable copy that will never have null keys but may have null values.
   */
  public static Map<String, Object> toJavaMap(JsonObject object) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (Entry<String, JsonValue> e : object.entrySet()) {
      map.put(e.getKey(), toJavaObject(e.getValue()));
    }
    return map;
  }

  /**
   * Converts a {@link JsonArray} to a list to objects returned by {@link #toJavaObject}. The
   * returned list is a mutable copy that may have null values.
   */
  public static List<Object> toJavaList(JsonArray array) {
    List<Object> list = new ArrayList<>(array.size());
    for (JsonValue v : array) {
      list.add(toJavaObject(v));
    }
    return list;
  }

  private JavaxJson() {}
}
