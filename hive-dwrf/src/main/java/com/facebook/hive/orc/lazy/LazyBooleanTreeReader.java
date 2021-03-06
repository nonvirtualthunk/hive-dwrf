//  Copyright (c) 2013, Facebook, Inc.  All rights reserved.

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.hive.orc.lazy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.io.BooleanWritable;

import com.facebook.hive.orc.BitFieldReader;
import com.facebook.hive.orc.InStream;
import com.facebook.hive.orc.OrcProto;
import com.facebook.hive.orc.StreamName;
import com.facebook.hive.orc.OrcProto.RowIndex;
import com.facebook.hive.orc.OrcProto.RowIndexEntry;
import com.facebook.hive.orc.lazy.OrcLazyObject.ValueNotPresentException;

public class LazyBooleanTreeReader extends LazyTreeReader {

  private BitFieldReader reader = null;
  private boolean latestRead = true; //< Latest value from stream.

  public LazyBooleanTreeReader(int columnId, long rowIndexStride) {
    super(columnId, rowIndexStride);
  }

  @Override
  public void startStripe(Map<StreamName, InStream> streams, List<OrcProto.ColumnEncoding> encodings,
      RowIndex[] indexes, long rowBaseInStripe) throws IOException {
    super.startStripe(streams, encodings, indexes, rowBaseInStripe);
    reader = new BitFieldReader(streams.get(new StreamName(columnId,
        OrcProto.Stream.Kind.DATA)));
    if (indexes[columnId] != null) {
      loadIndeces(indexes[columnId].getEntryList(), 0);
    }
  }

  @Override
  public void seek(int index) throws IOException {
    reader.seek(index);
  }

  @Override
  public int loadIndeces(List<RowIndexEntry> rowIndexEntries, int startIndex) {
    int updatedStartIndex = super.loadIndeces(rowIndexEntries, startIndex);
    return reader.loadIndeces(rowIndexEntries, updatedStartIndex);
  }

  @Override
  public void skipRows(long numNonNullValues) throws IOException {
    reader.skip(numNonNullValues);
  }

  boolean readBoolean() throws IOException {
    latestRead = (reader.next() == 1);
    return latestRead;
  }


  BooleanWritable createWritable(Object previous, boolean v) throws IOException {
    BooleanWritable result = null;
    if (previous == null) {
      result = new BooleanWritable();
    } else {
      result = (BooleanWritable) previous;
    }
    result.set(v);
    return result;
  }

  @Override
  public Object createWritableFromLatest(Object previous) throws IOException {
    return createWritable(previous, latestRead);
  }

  @Override
  public boolean nextBoolean(boolean readStream) throws IOException {
    if (!readStream) {
      return latestRead;
    }
    if (!valuePresent) {
      throw new ValueNotPresentException("Cannot materialize boolean.");
    }
    return readBoolean();

  }

  @Override
  public Object next(Object previous) throws IOException {
    BooleanWritable result = null;
    if (valuePresent) {
      result = createWritable(previous, readBoolean());
    }
    return result;
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (reader != null) {
      reader.close();
    }
  }
}
