/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.state.kerberos;

import com.google.gson.*;
import junit.framework.Assert;
import org.apache.ambari.server.AmbariException;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KerberosDescriptorTest {
  public static final String JSON_VALUE =
      "{" +
          "  \"properties\": {" +
          "      \"realm\": \"${cluster-env/kerberos_domain}\"," +
          "      \"keytab_dir\": \"/etc/security/keytabs\"" +
          "    }," +
          "  \"services\": [" +
          KerberosServiceDescriptorTest.JSON_VALUE +
          "    ]" +
          "}";

  public static final Map<String, Object> MAP_VALUE =
      new HashMap<String, Object>() {
        {
          put("properties", new HashMap<String, Object>() {{
            put("realm", "EXAMPLE.COM");
            put("some.property", "Hello World");
          }});

          put(KerberosDescriptorType.SERVICE.getDescriptorPluralName(), new ArrayList<Object>() {{
            add(KerberosServiceDescriptorTest.MAP_VALUE);
          }});
          put(KerberosDescriptorType.CONFIGURATION.getDescriptorPluralName(), new ArrayList<Map<String, Object>>() {{
            add(new HashMap<String, Object>() {
              {
                put("cluster-conf", new HashMap<String, String>() {
                  {
                    put("property1", "red");
                  }
                });
              }
            });
          }});
          put(KerberosDescriptorType.IDENTITY.getDescriptorPluralName(), new ArrayList<Object>() {{
            add(new HashMap<String, Object>() {
              {
                put("name", "shared");
                put("principal", new HashMap<String, Object>(KerberosPrincipalDescriptorTest.MAP_VALUE));
                put("keytab", new HashMap<String, Object>() {
                  {
                    put("file", "/etc/security/keytabs/subject.service.keytab");

                    put("owner", new HashMap<String, Object>() {{
                      put("name", "root");
                      put("access", "rw");
                    }});

                    put("group", new HashMap<String, Object>() {{
                      put("name", "hadoop");
                      put("access", "r");
                    }});

                    put("configuration", "service-site/service2.component.keytab.file");
                  }
                });
              }
            });
          }});
        }
      };

  public static void validateFromJSON(KerberosDescriptor descriptor) {
    Assert.assertNotNull(descriptor);
    Assert.assertTrue(descriptor.isContainer());

    Map<String, String> properties = descriptor.getProperties();
    Assert.assertNotNull(properties);
    Assert.assertEquals(2, properties.size());
    Assert.assertEquals("${cluster-env/kerberos_domain}", properties.get("realm"));
    Assert.assertEquals("/etc/security/keytabs", properties.get("keytab_dir"));

    Map<String, KerberosServiceDescriptor> serviceDescriptors = descriptor.getServices();
    Assert.assertNotNull(serviceDescriptors);
    Assert.assertEquals(1, serviceDescriptors.size());

    for (KerberosServiceDescriptor serviceDescriptor : serviceDescriptors.values()) {
      KerberosServiceDescriptorTest.validateFromJSON(serviceDescriptor);
    }

    Map<String, KerberosConfigurationDescriptor> configurations = descriptor.getConfigurations();

    Assert.assertNull(configurations);
  }

  public static void validateFromMap(KerberosDescriptor descriptor) throws AmbariException {
    Assert.assertNotNull(descriptor);
    Assert.assertTrue(descriptor.isContainer());

    Map<String, String> properties = descriptor.getProperties();
    Assert.assertNotNull(properties);
    Assert.assertEquals(2, properties.size());
    Assert.assertEquals("EXAMPLE.COM", properties.get("realm"));
    Assert.assertEquals("Hello World", properties.get("some.property"));

    Map<String, KerberosServiceDescriptor> services = descriptor.getServices();
    Assert.assertNotNull(services);
    Assert.assertEquals(1, services.size());

    for (KerberosServiceDescriptor service : services.values()) {
      KerberosComponentDescriptor component = service.getComponent("A_DIFFERENT_COMPONENT_NAME");
      Assert.assertNotNull(component);

      List<KerberosIdentityDescriptor> resolvedIdentities = component.getIdentities(true);
      KerberosIdentityDescriptor resolvedIdentity = null;
      Assert.assertNotNull(resolvedIdentities);
      Assert.assertEquals(3, resolvedIdentities.size());

      for (KerberosIdentityDescriptor item : resolvedIdentities) {
        if ("/shared".equals(item.getName())) {
          resolvedIdentity = item;
          break;
        }
      }
      Assert.assertNotNull(resolvedIdentity);

      List<KerberosIdentityDescriptor> identities = component.getIdentities(false);
      Assert.assertNotNull(identities);
      Assert.assertEquals(3, identities.size());

      KerberosIdentityDescriptor identityReference = component.getIdentity("/shared");
      Assert.assertNotNull(identityReference);

      KerberosIdentityDescriptor referencedIdentity = descriptor.getIdentity("shared");
      Assert.assertNotNull(referencedIdentity);

      Assert.assertEquals(identityReference.getKeytabDescriptor(), resolvedIdentity.getKeytabDescriptor());
      Assert.assertEquals(referencedIdentity.getPrincipalDescriptor(), resolvedIdentity.getPrincipalDescriptor());

      Map<String, KerberosConfigurationDescriptor> configurations = service.getConfigurations(true);
      Assert.assertNotNull(configurations);
      Assert.assertEquals(2, configurations.size());
      Assert.assertNotNull(configurations.get("service-site"));
      Assert.assertNotNull(configurations.get("cluster-conf"));
    }

    Map<String, KerberosConfigurationDescriptor> configurations = descriptor.getConfigurations();

    Assert.assertNotNull(configurations);
    Assert.assertEquals(1, configurations.size());

    KerberosConfigurationDescriptor configuration = configurations.get("cluster-conf");

    Assert.assertNotNull(configuration);

    Map<String, String> configProperties = configuration.getProperties();

    Assert.assertEquals("cluster-conf", configuration.getType());
    Assert.assertNotNull(configProperties);
    Assert.assertEquals(1, configProperties.size());
    Assert.assertEquals("red", configProperties.get("property1"));
  }

  public void validateUpdatedData(KerberosDescriptor descriptor) {
    Assert.assertNotNull(descriptor);

    Map<String, String> properties = descriptor.getProperties();
    Assert.assertNotNull(properties);
    Assert.assertEquals(3, properties.size());
    Assert.assertEquals("EXAMPLE.COM", properties.get("realm"));
    Assert.assertEquals("/etc/security/keytabs", properties.get("keytab_dir"));
    Assert.assertEquals("Hello World", properties.get("some.property"));

    Map<String, KerberosServiceDescriptor> serviceDescriptors = descriptor.getServices();
    Assert.assertNotNull(serviceDescriptors);
    Assert.assertEquals(2, serviceDescriptors.size());

    KerberosServiceDescriptorTest.validateFromJSON(descriptor.getService("SERVICE_NAME"));
    KerberosServiceDescriptorTest.validateFromMap(descriptor.getService("A_DIFFERENT_SERVICE_NAME"));

    Assert.assertNull(descriptor.getService("invalid service"));

    Map<String, KerberosConfigurationDescriptor> configurations = descriptor.getConfigurations();

    Assert.assertNotNull(configurations);
    Assert.assertEquals(1, configurations.size());

    KerberosConfigurationDescriptor configuration = configurations.get("cluster-conf");

    Assert.assertNotNull(configuration);

    Map<String, String> configProperties = configuration.getProperties();

    Assert.assertEquals("cluster-conf", configuration.getType());
    Assert.assertNotNull(configProperties);
    Assert.assertEquals(1, configProperties.size());
    Assert.assertEquals("red", configProperties.get("property1"));
  }

  private KerberosDescriptor createFromJSON() throws AmbariException {
    return KerberosDescriptor.fromJSON(JSON_VALUE);
  }

  private KerberosDescriptor createFromMap() throws AmbariException {
    return new KerberosDescriptor(MAP_VALUE);
  }

  @Test
  public void testFromMapViaGSON() throws AmbariException {
    Object data = new Gson().fromJson(JSON_VALUE, Object.class);

    Assert.assertNotNull(data);

    KerberosDescriptor descriptor = new KerberosDescriptor((Map<?, ?>) data);

    validateFromJSON(descriptor);
  }

  @Test
  public void testJSONDeserialize() throws AmbariException {
    validateFromJSON(createFromJSON());
  }

  @Test
  public void testMapDeserialize() throws AmbariException {
    validateFromMap(createFromMap());
  }

  @Test
  public void testInvalid() {
    // Invalid JSON syntax
    try {
      KerberosServiceDescriptor.fromJSON(JSON_VALUE + "erroneous text");
      Assert.fail("Should have thrown AmbariException.");
    } catch (AmbariException e) {
      // This is expected
    } catch (Throwable t) {
      Assert.fail("Should have thrown AmbariException.");
    }
  }

  @Test
  public void testEquals() throws AmbariException {
    Assert.assertTrue(createFromJSON().equals(createFromJSON()));
    Assert.assertFalse(createFromJSON().equals(createFromMap()));
  }

  @Test
  public void testToMap() throws AmbariException {
    KerberosDescriptor descriptor = createFromMap();
    Assert.assertNotNull(descriptor);
    Assert.assertEquals(MAP_VALUE, descriptor.toMap());
  }

  @Test
  public void testUpdate() throws AmbariException {
    KerberosDescriptor descriptor = createFromJSON();
    KerberosDescriptor updatedDescriptor = createFromMap();

    Assert.assertNotNull(descriptor);
    Assert.assertNotNull(updatedDescriptor);

    descriptor.update(updatedDescriptor);

    validateUpdatedData(descriptor);
  }

  @Test
  public void testReplaceVariables() throws AmbariException {
    Map<String, Map<String, String>> configurations = new HashMap<String, Map<String, String>>() {
      {
        put("", new HashMap<String, String>() {{
          put("global_variable", "Hello World");
          put("variable-name", "dash");
          put("variable_name", "underscore");
          put("variable.name", "dot");
        }});

        put("config_type", new HashMap<String, String>() {{
          put("variable-name", "config_type_dash");
          put("variable_name", "config_type_underscore");
          put("variable.name", "config_type_dot");
        }});

        put("config.type", new HashMap<String, String>() {{
          put("variable-name", "config.type_dash");
          put("variable_name", "config.type_underscore");
          put("variable.name", "config.type_dot");
        }});

        put("config-type", new HashMap<String, String>() {{
          put("variable.name", "Replacement1");
          put("variable.name1", "${config-type2/variable.name}");
          put("variable.name2", "");
        }});

        put("config-type2", new HashMap<String, String>() {{
          put("variable.name", "Replacement2");
          put("self_reference", "${config-type2/self_reference}");  // This essentially references itself.
          put("${config-type/variable.name}_reference", "Replacement in the key");
        }});
      }
    };

    Assert.assertEquals("concrete",
        KerberosDescriptor.replaceVariables("concrete", configurations));

    Assert.assertEquals("Hello World",
        KerberosDescriptor.replaceVariables("${global_variable}", configurations));

    Assert.assertEquals("Replacement1",
        KerberosDescriptor.replaceVariables("${config-type/variable.name}", configurations));

    Assert.assertEquals("Replacement1|Replacement2",
        KerberosDescriptor.replaceVariables("${config-type/variable.name}|${config-type2/variable.name}", configurations));

    Assert.assertEquals("Replacement1|Replacement2|${config-type3/variable.name}",
        KerberosDescriptor.replaceVariables("${config-type/variable.name}|${config-type2/variable.name}|${config-type3/variable.name}", configurations));

    Assert.assertEquals("Replacement2|Replacement2",
        KerberosDescriptor.replaceVariables("${config-type/variable.name1}|${config-type2/variable.name}", configurations));

    Assert.assertEquals("Replacement1_reference",
        KerberosDescriptor.replaceVariables("${config-type/variable.name}_reference", configurations));

    Assert.assertEquals("dash",
        KerberosDescriptor.replaceVariables("${variable-name}", configurations));

    Assert.assertEquals("underscore",
        KerberosDescriptor.replaceVariables("${variable_name}", configurations));

    Assert.assertEquals("config_type_dot",
        KerberosDescriptor.replaceVariables("${config_type/variable.name}", configurations));

    Assert.assertEquals("config_type_dash",
        KerberosDescriptor.replaceVariables("${config_type/variable-name}", configurations));

    Assert.assertEquals("config_type_underscore",
        KerberosDescriptor.replaceVariables("${config_type/variable_name}", configurations));

    Assert.assertEquals("config.type_dot",
        KerberosDescriptor.replaceVariables("${config.type/variable.name}", configurations));

    Assert.assertEquals("config.type_dash",
        KerberosDescriptor.replaceVariables("${config.type/variable-name}", configurations));

    Assert.assertEquals("config.type_underscore",
        KerberosDescriptor.replaceVariables("${config.type/variable_name}", configurations));

    Assert.assertEquals("dot",
        KerberosDescriptor.replaceVariables("${variable.name}", configurations));

    // Replacement yields an empty string
    Assert.assertEquals("",
        KerberosDescriptor.replaceVariables("${config-type/variable.name2}", configurations));


    // This might cause an infinite loop... we assume protection is in place...
    try {
      Assert.assertEquals("${config-type2/self_reference}",
          KerberosDescriptor.replaceVariables("${config-type2/self_reference}", configurations));
      Assert.fail(String.format("%s expected to be thrown", AmbariException.class.getName()));
    } catch (AmbariException e) {
      // This is expected...
    }
  }

  @Test
  public void testReplaceComplicatedVariables() throws AmbariException {
    Map<String, Map<String, String>> configurations = new HashMap<String, Map<String, String>>() {
      {
        put("", new HashMap<String, String>() {{
          put("host", "c6401.ambari.apache.org");
          put("realm", "EXAMPLE.COM");
        }});
      }
    };

    Assert.assertEquals("hive.metastore.local=false,hive.metastore.uris=thrift://c6401.ambari.apache.org:9083,hive.metastore.sasl.enabled=true,hive.metastore.execute.setugi=true,hive.metastore.warehouse.dir=/apps/hive/warehouse,hive.exec.mode.local.auto=false,hive.metastore.kerberos.principal=hive/_HOST@EXAMPLE.COM",
        KerberosDescriptor.replaceVariables("hive.metastore.local=false,hive.metastore.uris=thrift://${host}:9083,hive.metastore.sasl.enabled=true,hive.metastore.execute.setugi=true,hive.metastore.warehouse.dir=/apps/hive/warehouse,hive.exec.mode.local.auto=false,hive.metastore.kerberos.principal=hive/_HOST@${realm}", configurations));

    Assert.assertEquals("Hello my realm is {EXAMPLE.COM}",
        KerberosDescriptor.replaceVariables("Hello my realm is {${realm}}", configurations));

    Assert.assertEquals("$c6401.ambari.apache.org",
        KerberosDescriptor.replaceVariables("$${host}", configurations));
  }
}