/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.serialization;

import org.openmrs.ImplementationId;
import org.openmrs.Patient;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.extended.DynamicProxyConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.impl.AdministrationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * This serializer uses the xstream library to serialize and deserialize objects.
 * <br>
 * All classes are automatically aliased.  So a serialization of the {@link Patient} class
 * will not be:
 * <code>
 * &lt;org.openmrs.Patient ...&gt;
 *   &lt;element
 *   ...
 * &lt;/org.openmrs.Patient&gt;
 * </code>
 * but instead will be:
 * <code>
 * &lt;patient ...&gt;
 *   &lt;element
 *   ...
 * &lt;/patient&gt;
 * </code>
 *
 */
@Component("simpleXStreamSerializer")
public class SimpleXStreamSerializer implements OpenmrsSerializer {

	protected static final Logger log = LoggerFactory.getLogger(SimpleXStreamSerializer.class);
	
	private volatile XStream xstream;
	
	private final XStream customXStream;
	
	private AdministrationService adminService;
	
	/**
	 * Default Constructor
	 *
	 * @throws SerializationException
	 */
	public SimpleXStreamSerializer() throws SerializationException {
		this(null, null);
	}

	/**
	 * Constructor that takes a custom XStream object. 
	 * 
	 * Please note that it is deprecated since it now requires AdministrationService to fully configure
	 * whitelists defined via GPs.
	 * 
	 * @deprecated since 2.7.0, 2.6.2, 2.5.13 use SimpleXStreamSerializer(XStream, AdministrationService)
	 * @param customXStream
	 * @throws SerializationException
	 */
	public SimpleXStreamSerializer(XStream customXStream) throws SerializationException {
		this(customXStream, null);
	}
	
	public SimpleXStreamSerializer(XStream customXStream, AdministrationService adminService) throws SerializationException {
		this.customXStream = customXStream;
		this.adminService = adminService;
	}
	
	@Autowired
	public SimpleXStreamSerializer(AdministrationService adminService) {
		this.customXStream = null;
		this.adminService = adminService;
	}

	/**
	 * Setups XStream security using AdministrationService.getSerializerWhitelistTypes()
	 *
	 * @since 2.7.0, 2.6.2, 2.5.13
	 * @param newXStream
	 * @param adminService
	 */
	public static void setupXStreamSecurity(XStream newXStream, AdministrationService adminService) {
		if (adminService != null) {
			List<String> serializerWhitelistTypes = adminService.getSerializerWhitelistTypes();
			int prefixLength = AdministrationService.GP_SERIALIZER_WHITELIST_HIERARCHY_TYPES_PREFIX.length();
			for (String type: serializerWhitelistTypes) {
				if (type.startsWith(AdministrationService.GP_SERIALIZER_WHITELIST_HIERARCHY_TYPES_PREFIX)) {
					try {
						String className = type.substring(prefixLength);
						if (!isValidClassName(className)) {
							log.warn("XStream serializer rejected invalid class name: " + className);
							continue;
						}
						Class<?> aClass = Class.forName(className);
						newXStream.allowTypeHierarchy(aClass);
					} catch (ClassNotFoundException e) {
						log.warn("XStream serializer not configured to whitelist hierarchy of " + type, e);
					}
				} else if (type.contains("*")) {
					newXStream.allowTypesByWildcard(new String[] {type});
				} else {
					newXStream.allowTypes(new String[] {type});
				}
			}
		} else {
			log.warn("XStream serializer not configured with whitelists defined in GPs suffixed " +
				"with '.serializer.whitelist.types' due to adminService not being set.");
			List<Class<?>> types = AdministrationServiceImpl.getSerializerDefaultWhitelistHierarchyTypes();
			for (Class<?> type: types) {
				newXStream.allowTypeHierarchy(type);
			}
		}
	}

	/**
	 * Validates that a class name is safe to load via reflection.
	 * Only allows standard Java packages and OpenMRS packages.
	 *
	 * @param className the fully qualified class name to validate
	 * @return true if the class name is valid and safe to load
	 */
	private static boolean isValidClassName(String className) {
		if (className == null || className.trim().isEmpty()) {
			return false;
		}

		// Reject class names with suspicious characters
		if (className.contains("..") || className.contains("/") || className.contains("\\")) {
			return false;
		}

		// Must match Java class name pattern
		if (!className.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*$")) {
			return false;
		}

		// Allowlist of permitted package prefixes
		String[] allowedPrefixes = {
			"org.openmrs.",
			"java.",
			"javax.",
			"org.springframework.",
			"org.hibernate.",
			"org.apache.commons."
		};

		for (String prefix : allowedPrefixes) {
			if (className.startsWith(prefix)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Setups permissions and default attributes.
	 *
	 * @since 2.7.0, 2.6.2, 2.5.13 
	 * @param newXStream
	 */
	public void initXStream(XStream newXStream) {
		setupXStreamSecurity(newXStream, adminService);
		
		newXStream.registerConverter(new OpenmrsDynamicProxyConverter(), XStream.PRIORITY_VERY_HIGH);

		//This is added to read the prior simpleframework-serialized values.
		//TODO: find a better way to do this.
		newXStream.useAttributeFor(ImplementationId.class, "implementationId");
	}
	
	/**
	 * Expose the xstream object, so that module can config with xstream as need
	 *
	 * @return xstream can be configured by module
	 */
	public XStream getXstream() {
		if (xstream == null) {
			//Lazy-init so that GPs are completely populated for initXStream
			XStream newXStream = customXStream;
			if (newXStream == null) {	
				newXStream = new XStream();
			}
			initXStream(newXStream);
			xstream = newXStream;
		}
		return xstream;
	}
	
	/**
	 * @see OpenmrsSerializer#serialize(java.lang.Object)
	 * <strong>Should</strong> not serialize proxies
	 */
	@Override
	public String serialize(Object o) throws SerializationException {
		return getXstream().toXML(o);
	}
	
	/**
	 * @see OpenmrsSerializer#deserialize(String, Class)
	 * <strong>Should</strong> not deserialize proxies
	 * <strong>Should</strong> ignore entities
	 * <strong>Should</strong> not deserialize classes that are not whitelisted
	 * <strong>Should</strong> deserialize whitelisted packages
	 * <strong>Should</strong> deserialize whitelisted classes and packages
	 * <strong>Should</strong> deserialize whitelisted hierarchies
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> T deserialize(String serializedObject, Class<? extends T> clazz) throws SerializationException {
		try {
			return (T) getXstream().fromXML(serializedObject);
		}
		catch (XStreamException e) {
			throw new SerializationException("Unable to deserialize class: " + clazz.getName(), e);
		}
	}
	
	/**
	 * An instance of this converter needs to be registered with a higher priority than the rest so
	 * that it's called early in the converter chain. This way, we can make sure we never get to
	 * xstream's DynamicProxyConverter that can deserialize proxies.
	 *
	 * @see <a href="http://tinyurl.com/ord2rry">this blog</a>
	 */
	private class OpenmrsDynamicProxyConverter extends DynamicProxyConverter {
		
		OpenmrsDynamicProxyConverter() {
			super(null);
		}
		
		@Override
		public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
			throw new XStreamException("Can't serialize proxies");
		}
		
		@Override
		public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
			throw new XStreamException("Can't deserialize proxies");
		}
		
	}
}
