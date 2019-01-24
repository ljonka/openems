package io.openems.edge.meter.eastron.sdm220mbus;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.meter.api.MeterType;

@ObjectClassDefinition( 
		name = "Meter Eastron SDM220 M-Bus", //
		description = "Implements the meter.")
@interface Config {
	String service_pid(); 

	String id() default "meter0"; 

	boolean enabled() default true; 

	@AttributeDefinition(name = "Meter-Type", description = "Consumption") 
	MeterType type() default MeterType.PRODUCTION_AND_CONSUMPTION; 

	@AttributeDefinition(name = "Mbus-ID", description = "ID of Mbus bridge.")
	String mbus_id(); 

	@AttributeDefinition(name = "Mbus Unit-ID", description = "The Unit-ID of the Mbus device.")
	int mbusUnitId(); 

	String webconsole_configurationFactory_nameHint() default "Meter [{id}]"; 
}
