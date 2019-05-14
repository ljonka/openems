package io.openems.edge.controller.api.websocket;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller Api Websocket", //
		description = "This controller provides an HTTP Websocket/JSON api. It is required for OpenEMS UI.")
@interface Config {
	String id() default "ctrlApiWebsocket0";

	boolean enabled() default true;

	@AttributeDefinition(name = "Port", description = "Port on which the Websocket server should listen.")
	int port() default 8085;

	@AttributeDefinition(name = "Api-Timeout", description = "Sets the timeout in seconds for updates on Channels set by this Api.")
	int apiTimeout() default 60;

	@AttributeDefinition(name = "Edge identifier", description = "This identifier gets used when the edge has to authenticate on the AccessControl")
	String edgeIdentfier();

	String webconsole_configurationFactory_nameHint() default "Controller Api Websocket [{id}]";
}