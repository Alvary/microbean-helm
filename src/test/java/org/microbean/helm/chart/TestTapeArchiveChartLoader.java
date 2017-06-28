/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017 MicroBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.helm.chart;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import java.util.List;

import java.util.zip.GZIPInputStream;

import hapi.chart.ChartOuterClass.Chart;

import hapi.chart.MetadataOuterClass.Metadata;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import org.kamranzafar.jtar.TarInputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestTapeArchiveChartLoader {

  private TarInputStream stream;
  
  @Before
  public void setUp() throws IOException {
    // Chart is arbitrary, but it does have subcharts in it, which exercise some tricky logic
    final URI uri = URI.create("https://kubernetes-charts.storage.googleapis.com/wordpress-0.6.6.tgz");
    assertNotNull(uri);
    final URL url = uri.toURL();
    assertNotNull(url);
    final URLConnection connection = url.openConnection();
    assertNotNull(connection);
    connection.addRequestProperty("Accept-Encoding", "gzip");
    connection.connect();
    assertEquals("application/x-tar", connection.getContentType());
    this.stream = new TarInputStream(new BufferedInputStream(new GZIPInputStream(connection.getInputStream())));
  }

  @After
  public void tearDown() throws IOException {
    if (this.stream != null) {
      this.stream.close();
    }
  }
  
  @Test
  public void testLoad() throws IOException {
    final Chart chart = new TapeArchiveChartLoader().load(this.stream);
    assertNotNull(chart);
    final Metadata metadata = chart.getMetadata();
    assertNotNull(metadata);
    assertEquals("wordpress", metadata.getName());
    assertEquals("0.6.6", metadata.getVersion());
    final List<Chart> dependencies = chart.getDependenciesList();
    assertNotNull(dependencies);
    assertEquals(1, dependencies.size());
  }

}
