package io.openems.edge.bridge.mbus;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Bridge M-Bus Serial", //
		description = "Provides a service for connecting to, querying and writing to a M-Bus device.")
@interface ConfigSerial {
	String service_pid();

	String id() default "mbus0";

	@AttributeDefinition(name = "Port-Name", description = "The name of the serial port - e.g. '/dev/ttyUSB0' or 'COM3'")
	String portName() default "/dev/ttyUSB0";

	@AttributeDefinition(name = "Baudrate", description = "The baudrate - e.g. 9600, 19200, 38400, 57600 or 115200")
	int baudRate() default 9600;

	boolean enabled() default true;

	String webconsole_configurationFactory_nameHint() default "Bridge M-Bus Serial [{id}]";
}
