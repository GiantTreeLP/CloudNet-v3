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

package de.dytanic.cloudnet.command.sub;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.parsers.Parser;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.specifier.Range;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.command.annotation.Description;
import de.dytanic.cloudnet.command.exception.ArgumentNotAvailableException;
import de.dytanic.cloudnet.command.source.CommandSource;
import de.dytanic.cloudnet.command.source.ConsoleCommandSource;
import de.dytanic.cloudnet.common.INameable;
import de.dytanic.cloudnet.common.JavaVersion;
import de.dytanic.cloudnet.common.WildcardUtil;
import de.dytanic.cloudnet.common.collection.Pair;
import de.dytanic.cloudnet.common.language.LanguageManager;
import de.dytanic.cloudnet.driver.network.cluster.NetworkClusterNode;
import de.dytanic.cloudnet.driver.provider.ServiceTaskProvider;
import de.dytanic.cloudnet.driver.service.GroupConfiguration;
import de.dytanic.cloudnet.driver.service.ServiceDeployment;
import de.dytanic.cloudnet.driver.service.ServiceEnvironmentType;
import de.dytanic.cloudnet.driver.service.ServiceRemoteInclusion;
import de.dytanic.cloudnet.driver.service.ServiceTask;
import de.dytanic.cloudnet.driver.service.ServiceTemplate;
import de.dytanic.cloudnet.util.JavaVersionResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@CommandPermission("cloudnet.command.tasks")
@Description("Administers the configurations of all persistent tasks")
public final class CommandTasks {

  @Parser(suggestions = "serviceTask")
  public ServiceTask defaultTaskParser(CommandContext<CommandSource> $, Queue<String> input) {
    String name = input.remove();
    ServiceTask task = CloudNet.getInstance().getServiceTaskProvider().getServiceTask(name);
    if (task == null) {
      throw new ArgumentNotAvailableException(LanguageManager.getMessage("command-tasks-task-not-found"));
    }

    return task;
  }

  @Suggestions("serviceTask")
  public List<String> suggestTask(CommandContext<CommandSource> $, String input) {
    return this.taskProvider().getPermanentServiceTasks().stream().map(INameable::getName).collect(Collectors.toList());
  }

  @Parser(suggestions = "serviceTask")
  public Collection<ServiceTask> wildcardTaskParser(CommandContext<CommandSource> $, Queue<String> input) {
    String name = input.remove();
    Collection<ServiceTask> matchedTasks = WildcardUtil.filterWildcard(
      this.taskProvider().getPermanentServiceTasks(),
      name);
    if (matchedTasks.isEmpty()) {
      throw new ArgumentNotAvailableException(LanguageManager.getMessage("command-tasks-task-not-found"));
    }

    return matchedTasks;
  }

  @Parser(name = "javaCommand")
  public Pair<String, JavaVersion> javaCommandParser(CommandContext<CommandSource> $, Queue<String> input) {
    String command = String.join(" ", input);
    JavaVersion version = JavaVersionResolver.resolveFromJavaExecutable(command);
    if (version == null) {
      throw new ArgumentNotAvailableException(
        LanguageManager.getMessage("command-tasks-setup-question-javacommand-invalid"));
    }

    return new Pair<>(command, version);
  }

  @Parser(name = "nodeId", suggestions = "clusterNode")
  public String defaultClusterNodeParser(CommandContext<CommandSource> $, Queue<String> input) {
    String nodeId = input.remove();
    for (NetworkClusterNode node : CloudNet.getInstance().getConfig().getClusterConfig().getNodes()) {
      if (node.getUniqueId().equals(nodeId)) {
        return nodeId;
      }
    }

    throw new ArgumentNotAvailableException(LanguageManager.getMessage("command-tasks-node-not-found"));
  }

  @Suggestions("clusterNode")
  public List<String> suggestNode(CommandContext<CommandSource> $, String input) {
    return CloudNet.getInstance().getConfig().getClusterConfig().getNodes()
      .stream()
      .map(NetworkClusterNode::getUniqueId)
      .collect(Collectors.toList());
  }

  @CommandMethod(value = "tasks setup", requiredSender = ConsoleCommandSource.class)
  public void taskSetup(CommandSource source) {

  }

  @CommandMethod("tasks reload")
  public void reloadTasks(CommandSource source) {
    this.taskProvider().reload();
    source.sendMessage(LanguageManager.getMessage("command-tasks-reload-success"));
  }

  @CommandMethod("tasks delete <name>")
  public void deleteTask(CommandSource source, @Argument("name") Collection<ServiceTask> serviceTasks) {
    for (ServiceTask serviceTask : serviceTasks) {
      this.taskProvider().removePermanentServiceTask(serviceTask);
      source.sendMessage(LanguageManager.getMessage("command-tasks-delete-task"));
    }
  }

  @CommandMethod("tasks list")
  public void listTasks(CommandSource source) {
    for (ServiceTask task : this.taskProvider().getPermanentServiceTasks()) {
      this.singleTaskInfo(source, task);
    }
  }

  @CommandMethod("tasks create <name> <environment>")
  public void createTask(CommandSource source,
    @Argument("name") String taskName,
    @Argument("environment") ServiceEnvironmentType environmentType
  ) {
    if (this.taskProvider().isServiceTaskPresent(taskName)) {
      source.sendMessage(LanguageManager.getMessage("command-tasks-task-already-existing"));
      return;
    }

    ServiceTask serviceTask = ServiceTask.builder()
      .templates(Collections.singletonList(ServiceTemplate.local(taskName, "default")))
      .name(taskName)
      .autoDeleteOnStop(true)
      .groups(Collections.singletonList(taskName))
      .serviceEnvironmentType(environmentType)
      .maxHeapMemory(environmentType.isMinecraftProxy() ? 256 : 512)
      .startPort(environmentType.getDefaultStartPort())
      .build();
    this.taskProvider().addPermanentServiceTask(serviceTask);
    source.sendMessage(LanguageManager.getMessage("command-tasks-create-task"));
  }

  @CommandMethod("tasks task <name>")
  public void displayTask(CommandSource source, @Argument("name") Collection<ServiceTask> serviceTasks) {
    for (ServiceTask serviceTask : serviceTasks) {
      Collection<String> messages = new ArrayList<>();
      messages.add("Name: " + serviceTask.getName());
      messages.add("Groups: " + Arrays.toString(serviceTask.getGroups().toArray()));
      messages.add("Max heap memory: " + serviceTask.getProcessConfiguration().getMaxHeapMemorySize());
      messages.add("Maintenance: " + serviceTask.isMaintenance());
      messages.add(
        "Nodes:" + (serviceTask.getAssociatedNodes().isEmpty() ? "All"
          : Arrays.toString(serviceTask.getAssociatedNodes().toArray())));
      messages.add("Minimal Services: " + serviceTask.getMinServiceCount());
      messages.add("Java Command: " + serviceTask.getJavaCommand());
      messages.add("Start Port: " + serviceTask.getStartPort());
      messages.add("Static services: " + serviceTask.isStaticServices());
      messages.add("Auto delete on stop: " + serviceTask.isAutoDeleteOnStop());
      messages.add("Deleted files after stop: " + Arrays.toString(serviceTask.getDeletedFilesAfterStop().toArray()));
      messages.add("Environment: " + serviceTask.getProcessConfiguration().getEnvironment());

      CommandServiceConfiguration.applyServiceConfigurationDisplay(messages, serviceTask);
      source.sendMessage(messages);
    }
  }

  @CommandMethod("tasks task <name> set minServiceCount <amount>")
  public void setMinServiceCount(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("amount") @Range(min = "0") Integer amount
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.setMinServiceCount(amount));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "minServiceCount")
          .replace("%name%", task.getName())
          .replace("%value%", amount.toString())
      );
    }
  }

  @CommandMethod("tasks task <name> set maintenance <enabled>")
  public void setMaintenance(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("enabled") boolean enabled
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.setMaintenance(enabled));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "maintenance")
          .replace("%name%", task.getName())
          .replace("%value%", String.valueOf(enabled))
      );
    }
  }

  @CommandMethod("tasks task <name> set maxHeapMemory <amount>")
  public void setMaxHeapMemory(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("amount") @Range(min = "0") Integer amount
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.getProcessConfiguration().setMaxHeapMemorySize(amount));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "maxHeapMemory")
          .replace("%name%", task.getName())
          .replace("%value%", amount.toString())
      );
    }
  }

  @CommandMethod("tasks task <name> set startPort <amount>")
  public void setStartPort(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("amount") @Range(min = "0") Integer amount
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.setStartPort(amount));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "startPort")
          .replace("%name%", task.getName())
          .replace("%value%", amount.toString())
      );
    }
  }

  @CommandMethod("tasks task <name> set autoDeleteOnStop <enabled>")
  public void setAutoDeleteOnStop(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("enabled") boolean enabled
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.setAutoDeleteOnStop(enabled));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "autoDeleteOnStop")
          .replace("%name%", task.getName())
          .replace("%value%", String.valueOf(enabled))
      );
    }
  }

  @CommandMethod("tasks task <name> set staticServices <enabled>")
  public void setStaticServices(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("enabled") boolean enabled
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.setStaticServices(enabled));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "staticServices")
          .replace("%name%", task.getName())
          .replace("%value%", String.valueOf(enabled))
      );
    }
  }

  @CommandMethod("tasks task <name> set environment <environment>")
  public void setEnvironment(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("environment") ServiceEnvironmentType environmentType
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.getProcessConfiguration().setEnvironment(environmentType));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "staticServices")
          .replace("%name%", task.getName())
          .replace("%value%", String.valueOf(environmentType))
      );
    }
  }

  @CommandMethod("tasks task <name> set disableIpRewrite <enabled>")
  public void setDisableIpRewrite(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("enabled") boolean enabled
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.setDisableIpRewrite(enabled));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "disableIpRewrite")
          .replace("%name%", task.getName())
          .replace("%value%", String.valueOf(enabled))
      );
    }
  }

  @CommandMethod("tasks task <name> set javaCommand <executable>")
  public void setJavaCommand(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument(value = "executable", parserName = "javaCommand") Pair<String, JavaVersion> executable
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.setJavaCommand(executable.getFirst()));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "javaCommand")
          .replace("%name%", task.getName())
          .replace("%value%", executable.getFirst())
      );
    }
  }

  @CommandMethod("tasks task <name> add node <uniqueId>")
  public void addNode(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument(value = "uniqueId", parserName = "nodeId") String node
  ) {
    for (ServiceTask task : serviceTasks) {
      if (task.getAssociatedNodes().contains(node)) {
        continue;
      }

      this.updateTask(task, serviceTask -> serviceTask.getAssociatedNodes().add(node));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "node")
          .replace("%name%", task.getName())
          .replace("%value%", node)
      );
    }
  }

  @CommandMethod("tasks task <name> add group <group>")
  public void addGroup(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("group") GroupConfiguration group
  ) {
    for (ServiceTask task : serviceTasks) {
      if (task.getGroups().contains(group.getName())) {
        continue;
      }

      this.updateTask(task, serviceTask -> serviceTask.getGroups().add(group.getName()));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "group")
          .replace("%name%", task.getName())
          .replace("%value%", group.getName())
      );
    }
  }

  @CommandMethod("tasks task <name> remove node <uniqueId>")
  public void removeNode(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("uniqueId") String node
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.getAssociatedNodes().remove(node));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "node")
          .replace("%name%", task.getName())
          .replace("%value%", node)
      );
    }
  }

  @CommandMethod("tasks task <name> remove group <group>")
  public void removeGroup(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("group") String group
  ) {
    for (ServiceTask task : serviceTasks) {
      this.updateTask(task, serviceTask -> serviceTask.getGroups().remove(group));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "group")
          .replace("%name%", task.getName())
          .replace("%value%", group)
      );
    }
  }

  @CommandMethod("tasks task <name> add deployment <deployment>")
  public void addDeployment(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("deployment") ServiceTemplate template
  ) {
    ServiceDeployment deployment = new ServiceDeployment(template, new ArrayList<>());
    for (ServiceTask serviceTask : serviceTasks) {
      this.updateTask(serviceTask, task -> task.getDeployments().add(deployment));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "deployment")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", template.getFullName())
      );
    }
  }

  @CommandMethod("tasks task <name> add template <template>")
  public void addTemplate(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("template") ServiceTemplate template
  ) {

    for (ServiceTask serviceTask : serviceTasks) {
      this.updateTask(serviceTask, task -> task.getTemplates().add(template));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "template")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", template.getFullName())
      );
    }
  }

  @CommandMethod("tasks task <name> add inclusion <url> <path>")
  public void addInclusion(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("url") String url,
    @Argument("path") String path
  ) {
    ServiceRemoteInclusion inclusion = new ServiceRemoteInclusion(url, path);

    for (ServiceTask serviceTask : serviceTasks) {
      this.updateTask(serviceTask, task -> task.getIncludes().add(inclusion));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "inclusion")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", inclusion.getUrl())
      );
    }
  }

  @CommandMethod("tasks task <name> add jvmOption <options>")
  public void addJvmOption(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Greedy @Argument("options") String jvmOptions
  ) {
    Collection<String> splittedOptions = Arrays.asList(jvmOptions.split(" "));
    for (ServiceTask serviceTask : serviceTasks) {
      serviceTask.getJvmOptions().addAll(splittedOptions);
      this.updateTask(serviceTask);
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "jvmOptions")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", jvmOptions)
      );
    }
  }

  @CommandMethod("tasks task <name> add processParameter <options>")
  public void addProcessParameter(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Greedy @Argument("options") String processParameters
  ) {
    Collection<String> splittedOptions = Arrays.asList(processParameters.split(" "));
    for (ServiceTask serviceTask : serviceTasks) {
      serviceTask.getProcessParameters().addAll(splittedOptions);
      this.updateTask(serviceTask);
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "processParameters")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", processParameters)
      );
    }
  }

  @CommandMethod("tasks task <name> remove deployment <deployment>")
  public void removeDeployment(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("deployment") ServiceTemplate template
  ) {
    ServiceDeployment deployment = new ServiceDeployment(template, new ArrayList<>());
    for (ServiceTask serviceTask : serviceTasks) {
      this.updateTask(serviceTask, task -> task.getDeployments().remove(deployment));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "deployment")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", template.getFullName())
      );
    }
  }

  @CommandMethod("tasks task <name> remove template <template>")
  public void removeTemplate(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("template") ServiceTemplate template
  ) {

    for (ServiceTask serviceTask : serviceTasks) {
      this.updateTask(serviceTask, task -> task.getTemplates().remove(template));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "template")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", template.getFullName())
      );
    }
  }

  @CommandMethod("tasks task <name> remove inclusion <url> <path>")
  public void removeInclusion(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Argument("url") String url,
    @Argument("path") String path
  ) {
    ServiceRemoteInclusion inclusion = new ServiceRemoteInclusion(url, path);

    for (ServiceTask serviceTask : serviceTasks) {
      this.updateTask(serviceTask, task -> task.getIncludes().remove(inclusion));
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "inclusion")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", inclusion.getUrl())
      );
    }
  }

  @CommandMethod("tasks task <name> remove jvmOption <options>")
  public void removeJvmOption(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Greedy @Argument("options") String jvmOptions
  ) {
    Collection<String> splittedOptions = Arrays.asList(jvmOptions.split(" "));
    for (ServiceTask serviceTask : serviceTasks) {
      serviceTask.getJvmOptions().removeAll(splittedOptions);
      this.updateTask(serviceTask);
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "jvmOptions")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", String.join(", ", serviceTask.getJvmOptions()))
      );
    }
  }

  @CommandMethod("tasks task <name> remove processParameter <options>")
  public void removeProcessParameter(
    CommandSource source,
    @Argument("name") Collection<ServiceTask> serviceTasks,
    @Greedy @Argument("options") String processParameters
  ) {
    Collection<String> splittedOptions = Arrays.asList(processParameters.split(" "));
    for (ServiceTask serviceTask : serviceTasks) {
      serviceTask.getProcessParameters().removeAll(splittedOptions);
      this.updateTask(serviceTask);
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "processParameters")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", String.join(", ", serviceTask.getProcessParameters()))
      );
    }
  }

  @CommandMethod("tasks task <name> clear jvmOptions")
  public void clearJvmOptions(CommandSource source, @Argument("name") Collection<ServiceTask> serviceTasks) {
    for (ServiceTask serviceTask : serviceTasks) {
      this.updateTask(serviceTask, task -> task.getJvmOptions().clear());
      source.sendMessage(
        LanguageManager.getMessage("command-tasks-set-property-success")
          .replace("%property%", "jvmOptions")
          .replace("%name%", serviceTask.getName())
          .replace("%value%", "empty")
      );
    }
  }

  private void updateTask(ServiceTask task) {
    this.taskProvider().addPermanentServiceTask(task);
  }

  private void updateTask(ServiceTask task, Consumer<ServiceTask> consumer) {
    consumer.accept(task);
    this.taskProvider().addPermanentServiceTask(task);
  }

  private void singleTaskInfo(CommandSource source, ServiceTask task) {
    source.sendMessage(task.getName() +
      " | MinServiceCount: " + task.getMinServiceCount() +
      " | Maintenance: " + task.isMaintenance() +
      " | Nodes: " + (task.getAssociatedNodes().isEmpty() ? "All"
      : Arrays.toString(task.getAssociatedNodes().toArray())) +
      " | StartPort: " + task.getStartPort()
    );
  }

  public ServiceTaskProvider taskProvider() {
    return CloudNet.getInstance().getServiceTaskProvider();
  }

}
