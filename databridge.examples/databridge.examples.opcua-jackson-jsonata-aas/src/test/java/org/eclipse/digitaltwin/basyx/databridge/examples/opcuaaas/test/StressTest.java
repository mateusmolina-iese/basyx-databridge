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

import org.eclipse.basyx.aas.manager.ConnectedAssetAdministrationShellManager;
import org.eclipse.basyx.aas.metamodel.connected.ConnectedAssetAdministrationShell;
import org.eclipse.basyx.aas.metamodel.map.descriptor.CustomId;
import org.eclipse.basyx.aas.registration.memory.InMemoryRegistry;
import org.eclipse.basyx.components.aas.AASServerComponent;
import org.eclipse.basyx.components.aas.configuration.AASServerBackend;
import org.eclipse.basyx.components.aas.configuration.BaSyxAASServerConfiguration;
import org.eclipse.basyx.components.configuration.BaSyxContextConfiguration;
import org.eclipse.basyx.submodel.metamodel.api.ISubmodel;
import org.eclipse.basyx.submodel.metamodel.api.identifier.IIdentifier;
import org.eclipse.basyx.submodel.metamodel.api.submodelelement.ISubmodelElement;
import org.eclipse.digitaltwin.basyx.databridge.aas.configuration.factory.AASProducerDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.core.component.DataBridgeComponent;
import org.eclipse.digitaltwin.basyx.databridge.core.configuration.factory.RoutesConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.core.configuration.route.core.RoutesConfiguration;
import org.eclipse.digitaltwin.basyx.databridge.jsonata.configuration.factory.JsonataDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.jsonjackson.configuration.factory.JsonJacksonDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.opcua.configuration.factory.OpcuaDefaultConfigurationFactory;
import org.eclipse.milo.examples.server.ExampleServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Based on TestAASUpdater
 */
public class StressTest {
	private static AASServerComponent aasServer;
	private static DataBridgeComponent updater;
	private static InMemoryRegistry registry;
	protected static ExampleServer opcUaServer;

	protected static IIdentifier deviceAAS = new CustomId("TestUpdatedDeviceAAS");
	private static BaSyxContextConfiguration aasContextConfig;

	private final static long TOTAL_TEST_TIME = 60 * 1000 * 5; // ms
	private final static long PROPERTY_CHECK_INTERVAL = 500; // ms

	private final static int NUMBER_OF_CHECKS = (int) (TOTAL_TEST_TIME / PROPERTY_CHECK_INTERVAL);

	private Object propALastValue = "";

	@BeforeClass
	public static void setUp() throws Exception {
		System.out.println("Setting up env...");
		registry = new InMemoryRegistry();

		aasContextConfig = new BaSyxContextConfiguration(4001, "");
		BaSyxAASServerConfiguration aasConfig = new BaSyxAASServerConfiguration(AASServerBackend.INMEMORY,
				"aasx/updatertest.aasx");
		aasServer = new AASServerComponent(aasContextConfig, aasConfig);
		aasServer.setRegistry(registry);
	}

	@AfterClass
	public static void tearDown() throws Exception {
		System.out.println("Tearing down env...");
		updater.stopComponent();
		aasServer.stopComponent();
	}

	@Test
	public void test() throws Exception {
		aasServer.startComponent();
		System.out.println("AAS STARTED");
		System.out.println("START UPDATER");
		ClassLoader loader = this.getClass().getClassLoader();
		RoutesConfiguration configuration = new RoutesConfiguration();

		// Extend configutation for connections
		RoutesConfigurationFactory routesFactory = new RoutesConfigurationFactory(loader);
		configuration.addRoutes(routesFactory.create());

		// Extend configutation for Opcua Source
		OpcuaDefaultConfigurationFactory opcuaConfigFactory = new OpcuaDefaultConfigurationFactory(loader);
		configuration.addDatasources(opcuaConfigFactory.create());

		// Extend configuration for AAS
		AASProducerDefaultConfigurationFactory aasConfigFactory = new AASProducerDefaultConfigurationFactory(loader);
		configuration.addDatasinks(aasConfigFactory.create());

		JsonJacksonDefaultConfigurationFactory jsonJacksonConfigFactory = new JsonJacksonDefaultConfigurationFactory(
				loader);
		configuration.addTransformers(jsonJacksonConfigFactory.create());

		// Extend configuration for Jsonata
		JsonataDefaultConfigurationFactory jsonataConfigFactory = new JsonataDefaultConfigurationFactory(loader);
		configuration.addTransformers(jsonataConfigFactory.create());

		updater = new DataBridgeComponent(configuration);
		updater.startComponent();

		Thread.sleep(3000);

		for (int i = 0; i < NUMBER_OF_CHECKS; i++) {
			Thread.sleep(PROPERTY_CHECK_INTERVAL);
			long elapsedTime = i * PROPERTY_CHECK_INTERVAL / 1000; // s
			System.out.println("Try=#" + i + " / ElapsedTime=" + elapsedTime + "s");
			checkProperty();
		}
	}


	private void checkProperty() throws InterruptedException {
		ConnectedAssetAdministrationShellManager manager = new ConnectedAssetAdministrationShellManager(registry);
		ConnectedAssetAdministrationShell aas = manager.retrieveAAS(deviceAAS);
		ISubmodel sm = aas.getSubmodels().get("ConnectedSubmodel");
		ISubmodelElement propertyA = sm.getSubmodelElement("ConnectedPropertyA");
		Object propAValue = propertyA.getValue();

		// Should not be the case when the connection times out
		assertNotEquals(propALastValue, propAValue);
		propALastValue = propAValue;
	}
}
