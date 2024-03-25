/*******************************************************************************
 * Copyright (C) 2024 the Eclipse BaSyx Authors
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
import java.util.concurrent.ExecutionException;
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
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Stress Test #4
 * 
 * Tests if the subscription of a non-frequently changing OPC UA node times out
 * after a given time
 * 
 * Requires running MongoDB and BaSyxV2 instances
 * 
 * @see docker-compose.yaml @ src/test/resources
 */
@RunWith(Parameterized.class)
public class StressTest {
	private static OpcUaClient opcUaClient;
	private static CloseableHttpClient httpClient;
	private static DataBridgeComponent databridgeComponent;

	private final static int NUMBER_OF_CHECKS = 2;

	private static final String OPCUA_SERVER_URL = "opc.tcp://127.0.0.1:4840";
	private static final String OPCUA_NODE = "ns=2;i=6";

	private static final String SUBMODEL_ENDPOINT = "http://localhost:8081/submodels/aHR0cHM6Ly9leGFtcGxlLmNvbS9pZHMvc20vMDAwMl8wMTIyXzIwNDJfNjI1Mw";
	private static final String[] ID_SHORTS = { "Temperature" };

	private Object[] lastValues = new Object[ID_SHORTS.length];

	@BeforeClass
	public static void initStaticAttr() throws Exception {
		opcUaClient = buildOpcUaClient();
		httpClient = HttpClients.createDefault();
		databridgeComponent = buildDatabridgeComponent();
	}

	@Before
	public void initTest() throws InterruptedException {
		databridgeComponent.startComponent();
		Thread.sleep(3000);
	}

	@After
	public void tearDown() {
		databridgeComponent.stopComponent();
	}

	@Test
	public void testFor4Min() throws Exception {
		testUpdateFor(60 * 1000 * 4, NUMBER_OF_CHECKS);
	}

	@Test
	public void testFor8Min() throws Exception {
		testUpdateFor(60 * 1000 * 8, NUMBER_OF_CHECKS);
	}

	private void testUpdateFor(long checkInterval, int numChecks) throws Exception {
		List<String> propEndpoints = Arrays.asList(ID_SHORTS).stream().map(StressTest::buildGetPropValueEndpoint).collect(Collectors.toList());

		for (int i = 0; i < numChecks; i++) {
			Thread.sleep(checkInterval);
			long elapsedTime = i * checkInterval / 1000; // s
			System.out.println("Try=#" + i + " / ElapsedTime=" + elapsedTime + "s");

			publishToNode(OPCUA_NODE, elapsedTime);

			assertPropertiesChanged(propEndpoints);
		}
	}

	private void assertPropertiesChanged(List<String> endpoints) throws InterruptedException, IOException {
		int i = 0;
		for (String propEndpoint : endpoints) {
			String result = executeHttpGetRequest(propEndpoint);
			System.out.println(result);

			assertNotEquals(lastValues[i], result); // Shouldn't be the case if the connection fails

			lastValues[i] = result;
			i++;
		}
	}

	private void publishToNode(String nodeId, float value) throws InterruptedException, ExecutionException {
		opcUaClient.connect().get();

		NodeId targetNodeId = NodeId.parse(nodeId);
		Variant varValue = new Variant(value);

		opcUaClient.writeValue(targetNodeId, new DataValue(varValue)).get();

		opcUaClient.disconnect().get();
	}


	private static DataBridgeComponent buildDatabridgeComponent() throws InterruptedException {
		ClassLoader loader = StressTest.class.getClassLoader();
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

		DataBridgeComponent updater = new DataBridgeComponent(configuration);

		return updater;
	}

	private static String buildGetPropValueEndpoint(String idShort) {
		return SUBMODEL_ENDPOINT + "/submodel-elements/" + idShort + "/$value";
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

	private static OpcUaClient buildOpcUaClient() throws UaException {
		return OpcUaClient.create(OPCUA_SERVER_URL, eps -> eps.stream().findFirst(), b -> b.setIdentityProvider(new UsernameProvider("test", "test")).build());
	}
}
