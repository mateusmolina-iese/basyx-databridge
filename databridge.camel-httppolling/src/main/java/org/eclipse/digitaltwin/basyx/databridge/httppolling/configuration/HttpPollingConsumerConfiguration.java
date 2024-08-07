/*******************************************************************************
 * Copyright (C) 2022 the Eclipse BaSyx Authors
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
package org.eclipse.digitaltwin.basyx.databridge.httppolling.configuration;

import org.eclipse.digitaltwin.basyx.databridge.core.configuration.entity.DataSourceConfiguration;

/**
 * An implementation of httppolling consumer configuration
 * @author n14s - Niklas Mertens
 *
 */
public class HttpPollingConsumerConfiguration extends DataSourceConfiguration {
	public HttpPollingConsumerConfiguration() {}
	private	String authUsername;
	private String authPassword;
	
	public HttpPollingConsumerConfiguration(String uniqueId, String serverUrl, int serverPort, String authUsername, String authPassword) {
		super(uniqueId, serverUrl, serverPort);
		this.authUsername = authUsername;
		this.authPassword = authPassword;
	}

	@Override
	public String getConnectionURI() {

		if(!(authUsername == null || authPassword == null)) {
			String dataSourceServerUrl = getServerUrl();
			return dataSourceServerUrl + (dataSourceServerUrl.contains("?")? "&" : "?") + "authUsername=" + authUsername + "&authPassword=" + authPassword+"&authMethod=Basic&authenticationPreemptive=true";
		}

		return getServerUrl();
	}
}
