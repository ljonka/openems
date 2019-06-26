package io.openems.edge.meter.consolinno.d0;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Meter Consolinno D0", //
		description = "Implements generic D0 meter.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";
	
	@AttributeDefinition(name = "Meter serial number", description = "Serial number of meter.")
	String serialNumber() default "";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;
	
	@AttributeDefinition(name = "Use OBIS 1.8.0?", description = "Should we use the OBIS value 1.8.0?")
	boolean use180() default true;
	
	@AttributeDefinition(name = "Use OBIS 2.8.0?", description = "Should we use the OBIS value 2.8.0?")
	boolean use280() default true;

	@AttributeDefinition(name = "LMN-ID", description = "ID of LMNWired brige.")
	String lmnwired_id() default "lmnwired0";

	String webconsole_configurationFactory_nameHint() default "Meter Consolinno D0 [{id}]";
	String service_pid();
}