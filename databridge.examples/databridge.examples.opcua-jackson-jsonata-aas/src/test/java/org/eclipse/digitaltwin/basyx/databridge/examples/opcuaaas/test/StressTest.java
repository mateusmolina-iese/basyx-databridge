/*******************************************************************************
 * Copyright (C) 2021 the Eclipse BaSyx Authors
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 * SPDX-License-Identifier: MIT
 ******************************************************************************/
package org.eclipse.digitaltwin.basyx.databridge.examples.opcuaaas.test;

import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.digitaltwin.basyx.databridge.aas.configuration.factory.AASProducerDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.core.component.DataBridgeComponent;
import org.eclipse.digitaltwin.basyx.databridge.core.configuration.factory.RoutesConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.core.configuration.route.core.RoutesConfiguration;
import org.eclipse.digitaltwin.basyx.databridge.jsonata.configuration.factory.JsonataDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.jsonjackson.configuration.factory.JsonJacksonDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.opcua.configuration.factory.OpcuaDefaultConfigurationFactory;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Based on TestAASUpdater
 * 
 * Requires running MongoDB and BaSyxV2 instances
 * 
 * @see docker-compose.yaml @ src/test/resources
 */
public class StressTest {
	private static DataBridgeComponent updater;

	private final static long TOTAL_TEST_TIME = 60 * 1000 * 6; // ms
	private final static long PROPERTY_CHECK_INTERVAL = 2000; // ms

	private static final String SUBMODEL_ENDPOINT = "http://localhost:8081/submodels/aHR0cHM6Ly9leGFtcGxlLmNvbS9pZHMvc20vMDAwMl8wMTIyXzIwNDJfNjI1Mw";
	private static final String[] ID_SHORTS = { "DynamicFloat" };

	private final static int NUMBER_OF_CHECKS = (int) (TOTAL_TEST_TIME / PROPERTY_CHECK_INTERVAL);
	private Object[] lastValues = new Object[ID_SHORTS.length];

	private static CloseableHttpClient httpClient = HttpClients.createDefault();

	@AfterClass
	public static void tearDown() throws Exception {
		System.out.println("Tearing down env...");
		updater.stopComponent();
	}

	@Test
	public void test() throws Exception {
		setupDatabridge();

		Thread.sleep(3000);

		List<String> propEndpoints = Arrays.asList(ID_SHORTS).stream().map(StressTest::buildGetPropValueEndpoint).collect(Collectors.toList());

		for (int i = 0; i < NUMBER_OF_CHECKS; i++) {
			Thread.sleep(PROPERTY_CHECK_INTERVAL);
			long elapsedTime = i * PROPERTY_CHECK_INTERVAL / 1000; // s
			System.out.println("Try=#" + i + " / ElapsedTime=" + elapsedTime + "s");
			checkProperties(propEndpoints);
		}
	}

	private void checkProperties(List<String> endpoints) throws InterruptedException, IOException {
		int i = 0;
		for (String propEndpoint : endpoints) {
			String result = executeHttpGetRequest(propEndpoint);
			System.out.println(result);

			assertNotEquals(lastValues[i], result); // Shouldn't be the case if the connection fails

			lastValues[i] = result;
			i++;
		}
	}

	private static String buildGetPropValueEndpoint(String idShort) {
		return SUBMODEL_ENDPOINT + "/submodel-elements/" + idShort + "/$value";
	}

	private void setupDatabridge() {
		ClassLoader loader = this.getClass().getClassLoader();
		RoutesConfiguration configuration = new RoutesConfiguration();

		RoutesConfigurationFactory routesFactory = new RoutesConfigurationFactory(loader);
		configuration.addRoutes(routesFactory.create());

		OpcuaDefaultConfigurationFactory opcuaConfigFactory = new OpcuaDefaultConfigurationFactory(loader);
		configuration.addDatasources(opcuaConfigFactory.create());

		AASProducerDefaultConfigurationFactory aasConfigFactory = new AASProducerDefaultConfigurationFactory(loader);
		configuration.addDatasinks(aasConfigFactory.create());

		JsonJacksonDefaultConfigurationFactory jsonJacksonConfigFactory = new JsonJacksonDefaultConfigurationFactory(loader);
		configuration.addTransformers(jsonJacksonConfigFactory.create());

		JsonataDefaultConfigurationFactory jsonataConfigFactory = new JsonataDefaultConfigurationFactory(loader);
		configuration.addTransformers(jsonataConfigFactory.create());

		updater = new DataBridgeComponent(configuration);
		updater.startComponent();
	}

	private static String executeHttpGetRequest(String endpoint) throws IOException {
		HttpGet request = new HttpGet(endpoint);
		CloseableHttpResponse response = null;
		try {
			response = httpClient.execute(request);
			return EntityUtils.toString(response.getEntity(), "UTF-8");
		} finally {
			response.close();
		}
	}

}
