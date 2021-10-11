/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dytanic.cloudnet.service.defaults.config;

import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.service.ICloudService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;

public class NukkitConfigurationPreparer extends AbstractServiceConfigurationPreparer {

  @Override
  public void configure(@NotNull CloudNet nodeInstance, @NotNull ICloudService cloudService) {
    // check if we should run now
    if (this.shouldRewriteIp(nodeInstance, cloudService)) {
      // copy the default file
      Path configFile = cloudService.getDirectory().resolve("server.properties");
      this.copyCompiledFile("files/nukkit/server.properties", configFile);
      // load the configuration
      Properties properties = new Properties();
      try (InputStream stream = Files.newInputStream(configFile)) {
        properties.load(stream);
        // update the configuration
        properties.setProperty("server-ip", nodeInstance.getConfig().getHostAddress());
        properties.setProperty("server-port", String.valueOf(cloudService.getServiceConfiguration().getPort()));
        // store the properties
        try (OutputStream out = Files.newOutputStream(configFile)) {
          properties.store(out, "Properties Config file - edited by CloudNet");
        }
      } catch (IOException exception) {
        LOGGER.severe("Unable to edit server.properties in %s", exception, cloudService.getDirectory());
      }
    }
  }
}